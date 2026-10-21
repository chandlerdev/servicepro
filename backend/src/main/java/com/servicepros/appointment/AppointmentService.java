package com.servicepros.appointment;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import com.servicepros.appointment.AppointmentDtos.AppointmentCreate;
import com.servicepros.appointment.AppointmentDtos.AppointmentResponse;
import com.servicepros.appointment.AppointmentDtos.AppointmentUpdate;
import com.servicepros.common.ApiException;
import com.servicepros.common.PageResponse;
import com.servicepros.config.AppProperties;
import com.servicepros.customer.Customer;
import com.servicepros.customer.CustomerService;
import com.servicepros.notification.MessageTemplates;
import com.servicepros.notification.Notification;
import com.servicepros.notification.NotificationDtos.NotificationResponse;
import com.servicepros.notification.NotificationService;
import com.servicepros.route.ServiceRoute;
import com.servicepros.route.ServiceRouteRepository;
import com.servicepros.route.ServiceRouteService;
import com.servicepros.schedule.PlanStaleMarker;
import com.servicepros.schedule.RoutePlan;
import com.servicepros.schedule.RoutePlanRepository;
import com.servicepros.settings.CompanySettings;
import com.servicepros.settings.SettingsService;
import com.servicepros.task.ServiceTask;
import com.servicepros.task.ServiceTaskService;
import jakarta.persistence.criteria.Predicate;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Service
@RequiredArgsConstructor
public class AppointmentService {

    private final AppointmentRepository repo;
    private final CustomerService customers;
    private final ServiceTaskService tasks;
    private final ServiceRouteService routes;
    private final ServiceRouteRepository routeRepo;
    private final RoutePlanRepository plans;
    private final PlanStaleMarker staleMarker;
    private final NotificationService notifications;
    private final SettingsService settingsService;
    private final AppProperties props;

    @Transactional(readOnly = true)
    public PageResponse<AppointmentResponse> search(LocalDate date, LocalDate from, LocalDate to, Long routeId,
                                                    AppointmentStatus status, String q, Integer page, Integer size) {
        Specification<Appointment> spec = (root, query, cb) -> {
            List<Predicate> ps = new ArrayList<>();
            if (date != null) ps.add(cb.equal(root.get("date"), date));
            if (from != null) ps.add(cb.greaterThanOrEqualTo(root.get("date"), from));
            if (to != null) ps.add(cb.lessThanOrEqualTo(root.get("date"), to));
            if (routeId != null) ps.add(cb.equal(root.get("route").get("id"), routeId));
            if (status != null) ps.add(cb.equal(root.get("status"), status));
            if (StringUtils.hasText(q)) {
                String like = "%" + q.trim().toLowerCase() + "%";
                var customer = root.get("customer");
                ps.add(cb.or(cb.like(cb.lower(customer.get("name")), like),
                        cb.like(cb.lower(customer.get("address")), like),
                        cb.like(customer.get("phone"), like),
                        cb.like(cb.lower(root.get("task").get("name")), like)));
            }
            return cb.and(ps.toArray(Predicate[]::new));
        };
        Sort sort = Sort.by(Sort.Order.asc("date"), Sort.Order.asc("route.name"),
                Sort.Order.asc("stopOrder").nullsLast(), Sort.Order.asc("id"));
        Page<Appointment> result = repo.findAll(spec, PageResponse.pageable(page, size, sort));
        Map<String, Boolean> allocated = allocatedLookup(result.getContent());
        return PageResponse.of(result, a -> AppointmentResponse.from(a, allocated.getOrDefault(key(a), false)
                && a.getPlannedArrival() != null));
    }

    @Transactional(readOnly = true)
    public AppointmentResponse get(Long id) {
        return toResponse(find(id));
    }

    public Appointment find(Long id) {
        return repo.findById(id).orElseThrow(() -> ApiException.notFound("Appointment", id));
    }

    public List<AppointmentResponse> toResponses(List<Appointment> list) {
        Map<String, Boolean> allocated = allocatedLookup(list);
        return list.stream().map(a -> AppointmentResponse.from(a,
                allocated.getOrDefault(key(a), false) && a.getPlannedArrival() != null)).toList();
    }

    public AppointmentResponse toResponse(Appointment a) {
        return toResponses(List.of(a)).getFirst();
    }

    @Transactional
    public AppointmentResponse create(AppointmentCreate r) {
        Customer customer = customers.find(r.customerId());
        ServiceTask task = tasks.find(r.taskId());
        ServiceRoute route = r.routeId() != null ? routes.find(r.routeId())
                : routeRepo.findFirstByCityIgnoreCaseAndDeletedFalseOrderByIdAsc(customer.getCity())
                .orElseThrow(() -> ApiException.badRequest("No route serves " + customer.getCity()
                        + " — pick a route manually"));

        Appointment a = new Appointment();
        a.setDate(r.date());
        a.setCustomer(customer);
        a.setTask(task);
        a.setRoute(route);
        a.setNotes(r.notes());
        a.setStatus(AppointmentStatus.SCHEDULED);
        repo.save(a);
        staleMarker.mark(a.getDate(), route.getId());

        if (r.sendConfirmation() == null || r.sendConfirmation()) {
            CompanySettings s = settingsService.get();
            notifications.smsToCustomer(customer, a, Notification.Type.BOOKING, MessageTemplates.booking(a, s));
            notifications.emailToCustomer(customer, a, Notification.Type.BOOKING,
                    "Your " + task.getName() + " is booked", MessageTemplates.booking(a, s));
        }
        return toResponse(a);
    }

    @Transactional
    public AppointmentResponse update(Long id, AppointmentUpdate r) {
        Appointment a = find(id);
        LocalDate oldDate = a.getDate();
        Long oldRoute = a.getRoute().getId();
        boolean moved = !oldDate.equals(r.date()) || !oldRoute.equals(r.routeId())
                || !a.getCustomer().getId().equals(r.customerId()) || !a.getTask().getId().equals(r.taskId())
                || (a.getStatus() != r.status() && (r.status() == AppointmentStatus.CANCELLED || a.getStatus() == AppointmentStatus.CANCELLED));

        a.setDate(r.date());
        a.setCustomer(customers.find(r.customerId()));
        a.setTask(tasks.find(r.taskId()));
        a.setRoute(routes.find(r.routeId()));
        a.setNotes(r.notes());
        a.setStatus(r.status());
        if (r.status() == AppointmentStatus.COMPLETED && a.getCompletedAt() == null) a.setCompletedAt(java.time.Instant.now());

        if (moved) {
            staleMarker.mark(oldDate, oldRoute);
            staleMarker.mark(a.getDate(), a.getRoute().getId());
            if (!oldDate.equals(a.getDate()) || !oldRoute.equals(a.getRoute().getId())) {
                a.setStopOrder(null);
                a.setPlannedArrival(null);
                a.setPlannedEnd(null);
                a.setNotifiedArrival(null);
            }
        }
        return toResponse(a);
    }

    @Transactional
    public void delete(Long id) {
        Appointment a = find(id);
        staleMarker.mark(a.getDate(), a.getRoute().getId());
        repo.delete(a);
    }

    /** Texts the customer their date and (if allocated) arrival window. */
    @Transactional
    public NotificationResponse sendSms(Long id) {
        Appointment a = find(id);
        if (!a.getStatus().isOpen()) {
            throw ApiException.badRequest("Appointment is " + a.getStatus().name().toLowerCase());
        }
        CompanySettings s = settingsService.get();
        boolean allocated = allocatedLookup(List.of(a)).getOrDefault(key(a), false) && a.getPlannedArrival() != null;
        int window = props.getOptimizer().getArrivalWindowMinutes();
        if (!allocated) {
            // no trusted arrival time yet → date-only wording
            return notifications.smsToCustomer(a.getCustomer(), a, Notification.Type.SCHEDULE,
                    MessageTemplates.schedule(a, null, s, window, false));
        }
        boolean update = a.getNotifiedArrival() != null && !a.getNotifiedArrival().equals(a.getPlannedArrival());
        NotificationResponse n = notifications.smsToCustomer(a.getCustomer(), a,
                update ? Notification.Type.UPDATE : Notification.Type.SCHEDULE,
                MessageTemplates.schedule(a, a.getPlannedArrival(), s, window, update));
        a.setNotifiedArrival(a.getPlannedArrival());
        if (a.getStatus() == AppointmentStatus.SCHEDULED) a.setStatus(AppointmentStatus.CONFIRMED);
        return n;
    }

    private Map<String, Boolean> allocatedLookup(List<Appointment> list) {
        if (list.isEmpty()) return Map.of();
        Set<LocalDate> dates = list.stream().map(Appointment::getDate).collect(Collectors.toSet());
        Set<Long> routeIds = list.stream().map(a -> a.getRoute().getId()).collect(Collectors.toSet());
        Map<String, Boolean> map = new HashMap<>();
        for (RoutePlan p : plans.findByDateInAndRouteIdIn(dates, routeIds)) {
            map.put(p.getDate() + "|" + p.getRoute().getId(), !p.isStale() && p.getOptimizedAt() != null);
        }
        return map;
    }

    private static String key(Appointment a) {
        return a.getDate() + "|" + a.getRoute().getId();
    }
}
