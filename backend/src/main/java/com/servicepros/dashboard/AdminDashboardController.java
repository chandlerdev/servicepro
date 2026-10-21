package com.servicepros.dashboard;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import com.servicepros.appointment.Appointment;
import com.servicepros.appointment.AppointmentDtos.AppointmentResponse;
import com.servicepros.appointment.AppointmentRepository;
import com.servicepros.appointment.AppointmentService;
import com.servicepros.appointment.AppointmentStatus;
import com.servicepros.common.TimeService;
import com.servicepros.customer.CustomerRepository;
import com.servicepros.notification.NotificationDtos.NotificationResponse;
import com.servicepros.notification.NotificationRepository;
import com.servicepros.schedule.ScheduleService;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Admin · Dashboard")
@RestController
@RequestMapping("/api/admin/dashboard")
@RequiredArgsConstructor
public class AdminDashboardController {

    public record Dashboard(LocalDate date, long appointmentsToday, long completedToday, long delayedToday,
                            long enRouteToday, long customers, BigDecimal revenueToday, List<AppointmentResponse> todays,
                            List<NotificationResponse> latestNotifications) {
    }

    private final AppointmentRepository appointments;
    private final AppointmentService appointmentService;
    private final CustomerRepository customers;
    private final NotificationRepository notifications;
    private final TimeService time;

    @GetMapping
    @Transactional(readOnly = true)
    public Dashboard get() {
        LocalDate today = time.today();
        List<Appointment> todays = ScheduleService.sorted(appointments.findByDateAndStatusNot(today, AppointmentStatus.CANCELLED))
                .stream().sorted(java.util.Comparator.comparing((Appointment a) -> a.getRoute().getName())
                        .thenComparing(Appointment::getStopOrder, java.util.Comparator.nullsLast(java.util.Comparator.naturalOrder())))
                .toList();
        BigDecimal revenue = todays.stream().filter(a -> a.getStatus() == AppointmentStatus.COMPLETED)
                .map(a -> a.getTask().getRate()).reduce(BigDecimal.ZERO, BigDecimal::add);
        return new Dashboard(today, todays.size(),
                appointments.countByDateAndStatus(today, AppointmentStatus.COMPLETED),
                appointments.countByDateAndStatus(today, AppointmentStatus.DELAYED),
                appointments.countByDateAndStatus(today, AppointmentStatus.EN_ROUTE),
                customers.countByDeletedFalse(), revenue,
                appointmentService.toResponses(todays),
                notifications.findTop5ByOrderByIdDesc().stream().map(NotificationResponse::from).toList());
    }
}
