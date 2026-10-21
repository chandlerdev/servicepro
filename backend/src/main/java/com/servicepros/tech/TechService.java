package com.servicepros.tech;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

import com.servicepros.appointment.Appointment;
import com.servicepros.appointment.AppointmentRepository;
import com.servicepros.appointment.AppointmentStatus;
import com.servicepros.auth.AuthDtos.UserResponse;
import com.servicepros.auth.UserRepository;
import com.servicepros.common.ApiException;
import com.servicepros.common.PageResponse;
import com.servicepros.notification.MessageTemplates;
import com.servicepros.notification.Notification;
import com.servicepros.notification.NotificationDtos.NotificationResponse;
import com.servicepros.notification.NotificationRepository;
import com.servicepros.notification.NotificationService;
import com.servicepros.route.ServiceRoute;
import com.servicepros.route.ServiceRouteRepository;
import com.servicepros.schedule.ScheduleDtos.RoutePlanDto;
import com.servicepros.schedule.ScheduleDtos.StopDto;
import com.servicepros.schedule.ScheduleService;
import com.servicepros.security.AppUserPrincipal;
import com.servicepros.settings.CompanySettings;
import com.servicepros.settings.SettingsService;
import com.servicepros.tech.TechDtos.CompleteResponse;
import com.servicepros.tech.TechDtos.DelayResponse;
import com.servicepros.tech.TechDtos.MeResponse;
import com.servicepros.tech.TechDtos.RouteSummary;
import com.servicepros.tech.TechDtos.StopsResponse;
import com.servicepros.technician.Technician;
import com.servicepros.technician.TechnicianService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Everything the technician mobile app does. Technicians may only touch stops on their own routes. */
@Service
@RequiredArgsConstructor
public class TechService {

    private final TechnicianService technicians;
    private final ServiceRouteRepository routes;
    private final AppointmentRepository appointments;
    private final ScheduleService scheduleService;
    private final NotificationService notifications;
    private final NotificationRepository notificationRepo;
    private final SettingsService settingsService;
    private final UserRepository users;

    @Transactional(readOnly = true)
    public MeResponse me(AppUserPrincipal p, Long technicianIdParam) {
        Technician t = resolveTechnician(p, technicianIdParam);
        UserResponse user = UserResponse.from(users.findById(p.id()).orElseThrow(() -> ApiException.unauthorized("User not found")));
        List<RouteSummary> rs = routes.findByTechnicianId(t.getId()).stream().map(r -> new RouteSummary(r.getId(), r.getName(),
                r.getVehicle() == null ? null : r.getVehicle().getName(),
                r.getVehicle() == null ? null : r.getVehicle().getPlate(), r.getCity())).toList();
        return new MeResponse(user, technicians.toResponse(t), rs);
    }

    @Transactional(readOnly = true)
    public StopsResponse stops(AppUserPrincipal p, Long technicianIdParam, LocalDate date) {
        Technician t = resolveTechnician(p, technicianIdParam);
        CompanySettings s = settingsService.get();
        List<RoutePlanDto> plans = routes.findByTechnicianId(t.getId()).stream()
                .map(r -> scheduleService.routePlanDto(date, r))
                .filter(rp -> !rp.stops().isEmpty())
                .toList();
        return new StopsResponse(date, scheduleService.office(s), technicians.toResponse(t), plans);
    }

    @Transactional
    public StopDto onMyWay(AppUserPrincipal p, Long appointmentId) {
        Appointment a = accessible(p, appointmentId);
        requireOpen(a);
        a.setStatus(AppointmentStatus.EN_ROUTE);
        a.setStartedAt(Instant.now());
        CompanySettings s = settingsService.get();
        notifications.smsToCustomer(a.getCustomer(), a, Notification.Type.ENROUTE,
                MessageTemplates.enRoute(a, s));
        return stopOf(a);
    }

    @Transactional
    public DelayResponse delay(AppUserPrincipal p, Long appointmentId, int minutes, String reason) {
        Appointment a = accessible(p, appointmentId);
        requireOpen(a);
        a.setStatus(AppointmentStatus.DELAYED);
        CompanySettings s = settingsService.get();
        List<Appointment> affected = scheduleService.shiftFrom(a, minutes);
        for (Appointment x : affected) {
            notifications.smsToCustomer(x.getCustomer(), x, Notification.Type.DELAY, MessageTemplates.delay(x, s, minutes, reason));
            x.setNotifiedArrival(x.getPlannedArrival());
        }
        return new DelayResponse(affected.size());
    }

    @Transactional
    public CompleteResponse complete(AppUserPrincipal p, Long appointmentId, String notes) {
        Appointment a = accessible(p, appointmentId);
        requireOpen(a);
        a.setStatus(AppointmentStatus.COMPLETED);
        a.setCompletedAt(Instant.now());
        if (notes != null && !notes.isBlank()) {
            String merged = (a.getNotes() == null || a.getNotes().isBlank() ? "" : a.getNotes() + "\n") + "Tech: " + notes.trim();
            a.setNotes(merged.length() > 1000 ? merged.substring(0, 1000) : merged);
        }
        CompanySettings s = settingsService.get();
        notifications.smsToCustomer(a.getCustomer(), a, Notification.Type.REVIEW, MessageTemplates.review(a, s));
        return new CompleteResponse(stopOf(a));
    }

    @Transactional(readOnly = true)
    public PageResponse<NotificationResponse> notifications(AppUserPrincipal p, Long technicianIdParam, Integer page, Integer size) {
        Technician t = resolveTechnician(p, technicianIdParam);
        List<Long> routeIds = routes.findByTechnicianId(t.getId()).stream().map(ServiceRoute::getId).toList();
        var pageable = PageResponse.pageable(page, size, Sort.by(Sort.Direction.DESC, "id"));
        if (routeIds.isEmpty()) {
            return new PageResponse<>(List.of(), pageable.getPageNumber(), pageable.getPageSize(), 0, 0);
        }
        return PageResponse.of(notificationRepo.findByRouteIdIn(routeIds, pageable), NotificationResponse::from);
    }

    // ---------------------------------------------------------------- helpers

    private Technician resolveTechnician(AppUserPrincipal p, Long technicianIdParam) {
        if (p.isAdmin()) {
            if (technicianIdParam == null) throw ApiException.badRequest("Admins must pass ?technicianId=");
            return technicians.find(technicianIdParam);
        }
        if (p.technicianId() == null) throw ApiException.forbidden("This login is not linked to a technician");
        return technicians.find(p.technicianId());
    }

    private Appointment accessible(AppUserPrincipal p, Long appointmentId) {
        Appointment a = appointments.findById(appointmentId)
                .orElseThrow(() -> ApiException.notFound("Appointment", appointmentId));
        if (!p.isAdmin() && !a.getRoute().hasTechnician(p.technicianId())) {
            throw ApiException.forbidden("This stop is not on your route");
        }
        return a;
    }

    private static void requireOpen(Appointment a) {
        if (!a.getStatus().isOpen()) {
            throw ApiException.conflict("Appointment is already " + a.getStatus().name().toLowerCase());
        }
    }

    private StopDto stopOf(Appointment a) {
        RoutePlanDto plan = scheduleService.routePlanDto(a.getDate(), a.getRoute());
        return plan.stops().stream().filter(s -> s.appointmentId().equals(a.getId())).findFirst()
                .orElseThrow(() -> new IllegalStateException("Stop not found after update"));
    }
}
