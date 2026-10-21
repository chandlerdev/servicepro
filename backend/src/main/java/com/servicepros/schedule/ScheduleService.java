package com.servicepros.schedule;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

import com.servicepros.appointment.Appointment;
import com.servicepros.appointment.AppointmentRepository;
import com.servicepros.appointment.AppointmentStatus;
import com.servicepros.common.ApiException;
import com.servicepros.common.TimeService;
import com.servicepros.config.AppProperties;
import com.servicepros.customer.Customer;
import com.servicepros.geo.DistanceMatrixService;
import com.servicepros.geo.LatLng;
import com.servicepros.notification.MessageTemplates;
import com.servicepros.notification.Notification;
import com.servicepros.notification.NotificationService;
import com.servicepros.route.ServiceRoute;
import com.servicepros.route.ServiceRouteRepository;
import com.servicepros.schedule.ScheduleDtos.AllocateResult;
import com.servicepros.schedule.ScheduleDtos.DaySchedule;
import com.servicepros.schedule.ScheduleDtos.OfficeDto;
import com.servicepros.schedule.ScheduleDtos.RoutePlanDto;
import com.servicepros.schedule.ScheduleDtos.StopDto;
import com.servicepros.settings.CompanySettings;
import com.servicepros.settings.SettingsService;
import com.servicepros.technician.TechnicianDtos.TechnicianRef;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Day planning: stop ordering, ETA computation, Google Maps links, and the
 * "allocate & notify every customer" step that runs the evening before.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ScheduleService {

    /** Google Maps URLs accept at most 9 intermediate waypoints → 10 stops per link. */
    private static final int STOPS_PER_MAPS_LINK = 10;

    private final AppointmentRepository appointments;
    private final RoutePlanRepository plans;
    private final ServiceRouteRepository routes;
    private final DistanceMatrixService distances;
    private final SettingsService settingsService;
    private final NotificationService notifications;
    private final TimeService time;
    private final AppProperties props;

    // ------------------------------------------------------------------ queries

    @Transactional(readOnly = true)
    public DaySchedule daySchedule(LocalDate date, Long routeId) {
        CompanySettings s = settingsService.get();
        List<RoutePlanDto> dtos = routesFor(date, routeId).stream().map(r -> routePlanDto(date, r)).toList();
        return new DaySchedule(date, office(s), dtos);
    }

    /** The depot, or null until the admin saves it in Settings. */
    public OfficeDto office(CompanySettings s) {
        if (!s.hasDepot()) return null;
        String name = s.getCompanyName() == null || s.getCompanyName().isBlank() ? "Depot" : s.getCompanyName() + " (Depot)";
        return new OfficeDto(name, s.getOfficeAddress(), s.getOfficeLat(), s.getOfficeLng());
    }

    /** Routes start at the depot, so optimizing needs it. */
    private CompanySettings requireDepot() {
        CompanySettings s = settingsService.get();
        if (!s.hasDepot()) {
            throw ApiException.badRequest("Set your company name and office / depot address in Settings first — routes start from the depot.");
        }
        return s;
    }

    public List<ServiceRoute> routesFor(LocalDate date, Long routeId) {
        if (routeId != null) {
            return List.of(routes.findById(routeId).orElseThrow(() -> ApiException.notFound("Route", routeId)));
        }
        List<Long> ids = appointments.findRouteIdsWithAppointments(date);
        return routes.findAllById(ids).stream().sorted(Comparator.comparing(ServiceRoute::getName)).toList();
    }

    public RoutePlanDto routePlanDto(LocalDate date, ServiceRoute route) {
        RoutePlan plan = plans.findByDateAndRouteId(date, route.getId()).orElse(null);
        List<Appointment> appts = sorted(appointments.findByDateAndRouteIdAndStatusNot(date, route.getId(), AppointmentStatus.CANCELLED));
        boolean optimized = plan != null && plan.getOptimizedAt() != null;
        boolean stale = plan != null && plan.isStale();
        boolean trustTimes = optimized && !stale;

        List<StopDto> stops = new ArrayList<>();
        for (int i = 0; i < appts.size(); i++) {
            Appointment a = appts.get(i);
            Customer c = a.getCustomer();
            stops.add(new StopDto(a.getId(), i + 1, c.getId(), c.getName(), c.getPhone(), c.getAddress(), c.getLat(),
                    c.getLng(), a.getTask().getName(), a.getTask().getDurationMinutes(), a.getStatus(), a.getNotes(),
                    trustTimes ? TimeService.hhmm(a.getPlannedArrival()) : null,
                    trustTimes ? TimeService.hhmm(a.getPlannedEnd()) : null,
                    trustTimes ? a.getLegKm() : null));
        }
        var v = route.getVehicle();
        return new RoutePlanDto(route.getId(), route.getName(), v == null ? null : v.getName(),
                v == null ? null : v.getPlate(), route.getTechnicians().stream().map(TechnicianRef::from).toList(),
                TimeService.hhmm(route.getStartTime()), optimized, stale,
                trustTimes ? plan.getTotalKm() : null, trustTimes ? TimeService.hhmm(plan.getEndTime()) : null,
                plan == null ? null : plan.getOptimizedAt(), plan == null ? null : plan.getNotifiedAt(),
                mapsUrls(appts), stops);
    }

    // ------------------------------------------------------------------ commands

    @Transactional
    public DaySchedule optimize(LocalDate date, Long routeId) {
        requireDepot();
        routesFor(date, routeId).forEach(r -> optimizeRoute(date, r));
        return daySchedule(date, routeId);
    }

    /** Optimizes (if needed) and texts every open customer their arrival window. */
    @Transactional
    public AllocateResult allocateAndNotify(LocalDate date, Long routeId) {
        CompanySettings s = requireDepot();
        int window = props.getOptimizer().getArrivalWindowMinutes();
        int notified = 0;
        for (ServiceRoute route : routesFor(date, routeId)) {
            RoutePlan plan = plans.findByDateAndRouteId(date, route.getId()).orElse(null);
            if (plan == null || plan.isStale() || plan.getOptimizedAt() == null) {
                plan = optimizeRoute(date, route);
            }
            if (plan == null) continue;
            for (Appointment a : sorted(appointments.findByDateAndRouteIdAndStatusNot(date, route.getId(), AppointmentStatus.CANCELLED))) {
                if (a.getStatus() == AppointmentStatus.COMPLETED || a.getStatus() == AppointmentStatus.EN_ROUTE) continue;
                if (a.getPlannedArrival() != null && a.getPlannedArrival().equals(a.getNotifiedArrival())) continue;
                boolean update = a.getNotifiedArrival() != null;
                notifications.smsToCustomer(a.getCustomer(), a, update ? Notification.Type.UPDATE : Notification.Type.SCHEDULE,
                        MessageTemplates.schedule(a, a.getPlannedArrival(), s, window, update));
                a.setNotifiedArrival(a.getPlannedArrival());
                if (a.getStatus() == AppointmentStatus.SCHEDULED) a.setStatus(AppointmentStatus.CONFIRMED);
                notified++;
            }
            plan.setNotifiedAt(Instant.now());
        }
        return new AllocateResult(daySchedule(date, routeId), notified);
    }

    /**
     * Shifts this stop and every later open stop by {@code minutes}.
     * @return the appointments whose ETA moved (to be notified by the caller)
     */
    @Transactional
    public List<Appointment> shiftFrom(Appointment delayed, int minutes) {
        List<Appointment> appts = sorted(appointments.findByDateAndRouteIdAndStatusNot(
                delayed.getDate(), delayed.getRoute().getId(), AppointmentStatus.CANCELLED));
        List<Appointment> affected = new ArrayList<>();
        Integer from = delayed.getStopOrder();
        for (Appointment a : appts) {
            boolean target = a.getId().equals(delayed.getId());
            boolean later = from != null && a.getStopOrder() != null && a.getStopOrder() > from;
            if (!target && !later) continue;
            if (!target && !a.getStatus().isOpen()) continue;
            if (a.getPlannedArrival() != null) a.setPlannedArrival(clamp(a.getPlannedArrival(), minutes));
            if (a.getPlannedEnd() != null) a.setPlannedEnd(clamp(a.getPlannedEnd(), minutes));
            affected.add(a);
        }
        plans.findByDateAndRouteId(delayed.getDate(), delayed.getRoute().getId())
                .ifPresent(p -> { if (p.getEndTime() != null) p.setEndTime(clamp(p.getEndTime(), minutes)); });
        return affected;
    }

    /** Re-orders the open stops of one route and recomputes ETAs. Returns null when the route has no stops. */
    @Transactional
    public RoutePlan optimizeRoute(LocalDate date, ServiceRoute route) {
        List<Appointment> appts = appointments.findByDateAndRouteIdAndStatusNot(date, route.getId(), AppointmentStatus.CANCELLED);
        if (appts.isEmpty()) {
            plans.findByDateAndRouteId(date, route.getId()).ifPresent(plans::delete);
            return null;
        }
        // Completed and en-route stops keep their place; only the remaining ones are re-ordered.
        List<Appointment> fixed = appts.stream()
                .filter(a -> a.getStatus() == AppointmentStatus.COMPLETED || a.getStatus() == AppointmentStatus.EN_ROUTE)
                .sorted(Comparator.comparing((Appointment a) -> a.getStatus() == AppointmentStatus.EN_ROUTE ? 1 : 0)
                        .thenComparing(Appointment::getStopOrder, Comparator.nullsLast(Comparator.naturalOrder()))
                        .thenComparing(Appointment::getId))
                .toList();
        List<Appointment> open = appts.stream().filter(a -> !fixed.contains(a)).toList();

        List<Appointment> all = new ArrayList<>(fixed);
        all.addAll(open);
        CompanySettings s = requireDepot();
        DistanceMatrixService.Matrix m = distances.compute(points(s, all));

        int start = fixed.isEmpty() ? 0 : fixed.size(); // matrix index of last fixed stop (0 = depot)
        List<Integer> openIdx = new ArrayList<>();
        for (int i = 0; i < open.size(); i++) openIdx.add(fixed.size() + i + 1);
        List<Integer> orderedIdx = RouteOptimizer.order(m.minutes(), start, openIdx);

        List<Appointment> ordered = new ArrayList<>(fixed);
        orderedIdx.forEach(idx -> ordered.add(all.get(idx - 1)));

        // Re-index the matrix to the final order so applySequence can walk it sequentially.
        int n = ordered.size() + 1;
        int[] map = new int[n];
        map[0] = 0;
        for (int i = 0; i < ordered.size(); i++) map[i + 1] = all.indexOf(ordered.get(i)) + 1;
        double[][] km = new double[n][n], min = new double[n][n];
        for (int i = 0; i < n; i++) {
            for (int j = 0; j < n; j++) {
                km[i][j] = m.km()[map[i]][map[j]];
                min[i][j] = m.minutes()[map[i]][map[j]];
            }
        }
        RoutePlan plan = applySequence(date, route, ordered, new DistanceMatrixService.Matrix(km, min, m.fromGoogle()));
        log.info("Optimized {} on {}: {} stops, {} km (google={})", route.getName(), date, ordered.size(),
                plan.getTotalKm(), m.fromGoogle());
        return plan;
    }

    // ------------------------------------------------------------------ internals

    /** Walks the stops in the given order (matrix index i+1 = ordered.get(i), 0 = depot) and writes ETAs. */
    private RoutePlan applySequence(LocalDate date, ServiceRoute route, List<Appointment> ordered,
                                    DistanceMatrixService.Matrix m) {
        ZoneId zone = time.zone();
        boolean isToday = date.equals(time.today());
        int nowMin = toMin(time.nowTime());
        int cursor = toMin(route.getStartTime());
        int cursorIdx = 0;
        double totalKm = 0;
        boolean firstOpen = true;

        for (int i = 0; i < ordered.size(); i++) {
            Appointment a = ordered.get(i);
            int idx = i + 1;
            double legKm = m.km()[cursorIdx][idx];
            double legMin = m.minutes()[cursorIdx][idx];
            totalKm += legKm;
            a.setStopOrder(i + 1);
            a.setLegKm(Math.round(legKm * 10) / 10.0);
            int duration = a.getTask().getDurationMinutes();

            boolean fixed = (a.getStatus() == AppointmentStatus.COMPLETED || a.getStatus() == AppointmentStatus.EN_ROUTE)
                    && a.getPlannedArrival() != null;
            if (fixed) {
                int end = a.getPlannedEnd() != null ? toMin(a.getPlannedEnd()) : toMin(a.getPlannedArrival()) + duration;
                if (a.getStatus() == AppointmentStatus.COMPLETED && a.getCompletedAt() != null && isToday) {
                    end = toMin(LocalTime.ofInstant(a.getCompletedAt(), zone));
                }
                cursor = Math.max(cursor, end);
            } else {
                int depart = cursor;
                if (isToday && firstOpen) depart = Math.max(depart, nowMin);
                firstOpen = false;
                int arrival = depart + (int) Math.round(legMin);
                a.setPlannedArrival(toTime(arrival));
                a.setPlannedEnd(toTime(arrival + duration));
                cursor = arrival + duration;
            }
            cursorIdx = idx;
        }

        RoutePlan plan = plans.findByDateAndRouteId(date, route.getId()).orElseGet(() -> {
            RoutePlan p = new RoutePlan();
            p.setDate(date);
            p.setRoute(route);
            return p;
        });
        plan.setTotalKm(Math.round(totalKm * 10) / 10.0);
        plan.setEndTime(toTime(cursor));
        plan.setStale(false);
        plan.setOptimizedAt(Instant.now());
        return plans.save(plan);
    }

    private List<LatLng> points(CompanySettings s, List<Appointment> ordered) {
        List<LatLng> pts = new ArrayList<>(ordered.size() + 1);
        pts.add(new LatLng(s.getOfficeLat(), s.getOfficeLng()));
        ordered.forEach(a -> pts.add(new LatLng(a.getCustomer().getLat(), a.getCustomer().getLng())));
        return pts;
    }

    /** One Google Maps directions link per 10 open stops (the URL API caps waypoints at 9). */
    List<String> mapsUrls(List<Appointment> sortedAppts) {
        CompanySettings s = settingsService.get();
        List<Appointment> open = sortedAppts.stream().filter(a -> a.getStatus().isOpen()).toList();
        boolean anyDone = sortedAppts.stream().anyMatch(a -> a.getStatus() == AppointmentStatus.COMPLETED);
        List<String> urls = new ArrayList<>();
        String origin = anyDone || !s.hasDepot() ? null : new LatLng(s.getOfficeLat(), s.getOfficeLng()).asParam();
        for (int i = 0; i < open.size(); i += STOPS_PER_MAPS_LINK) {
            List<Appointment> chunk = open.subList(i, Math.min(open.size(), i + STOPS_PER_MAPS_LINK));
            Customer dest = chunk.getLast().getCustomer();
            StringBuilder url = new StringBuilder("https://www.google.com/maps/dir/?api=1&travelmode=driving");
            if (origin != null) url.append("&origin=").append(origin);
            url.append("&destination=").append(dest.getLat()).append(',').append(dest.getLng());
            if (chunk.size() > 1) {
                StringBuilder wp = new StringBuilder();
                for (Appointment a : chunk.subList(0, chunk.size() - 1)) {
                    if (!wp.isEmpty()) wp.append("%7C");
                    wp.append(a.getCustomer().getLat()).append(',').append(a.getCustomer().getLng());
                }
                url.append("&waypoints=").append(wp);
            }
            urls.add(url.toString());
            origin = dest.getLat() + "," + dest.getLng();
        }
        return urls;
    }

    public static List<Appointment> sorted(List<Appointment> appts) {
        return appts.stream()
                .sorted(Comparator.comparing(Appointment::getStopOrder, Comparator.nullsLast(Comparator.naturalOrder()))
                        .thenComparing(Appointment::getId))
                .toList();
    }

    public boolean isAllocated(Appointment a) {
        return a.getPlannedArrival() != null && plans.findByDateAndRouteId(a.getDate(), a.getRoute().getId())
                .map(p -> !p.isStale() && p.getOptimizedAt() != null).orElse(false);
    }

    private static int toMin(LocalTime t) {
        return t.getHour() * 60 + t.getMinute();
    }

    private static LocalTime toTime(int minutes) {
        int m = Math.max(0, Math.min(minutes, 23 * 60 + 59));
        return LocalTime.of(m / 60, m % 60);
    }

    private static LocalTime clamp(LocalTime t, int plusMinutes) {
        return toTime(toMin(t) + plusMinutes);
    }
}
