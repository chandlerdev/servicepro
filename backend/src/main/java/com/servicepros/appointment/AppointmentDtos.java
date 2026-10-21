package com.servicepros.appointment;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

import com.servicepros.common.TimeService;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public final class AppointmentDtos {

    private AppointmentDtos() {
    }

    public record AppointmentCreate(@NotNull LocalDate date, @NotNull Long customerId, @NotNull Long taskId,
                                    Long routeId, @Size(max = 1000) String notes, Boolean sendConfirmation) {
    }

    public record AppointmentUpdate(@NotNull LocalDate date, @NotNull Long customerId, @NotNull Long taskId,
                                    @NotNull Long routeId, @Size(max = 1000) String notes,
                                    @NotNull AppointmentStatus status) {
    }

    public record AppointmentResponse(Long id, LocalDate date, Long customerId, String customerName,
                                      String customerPhone, String customerAddress, double customerLat,
                                      double customerLng, String city, Long taskId, String taskName,
                                      BigDecimal taskRate, int taskDuration, Long routeId, String routeName,
                                      AppointmentStatus status, String notes, Integer stopOrder,
                                      String plannedArrival, String plannedEnd, boolean allocated,
                                      Instant createdAt) {

        public static AppointmentResponse from(Appointment a, boolean allocated) {
            var c = a.getCustomer();
            var t = a.getTask();
            var r = a.getRoute();
            return new AppointmentResponse(a.getId(), a.getDate(), c.getId(), c.getName(), c.getPhone(),
                    c.getAddress(), c.getLat(), c.getLng(), c.getCity(), t.getId(), t.getName(),
                    t.getRate(), t.getDurationMinutes(), r.getId(), r.getName(), a.getStatus(), a.getNotes(),
                    allocated ? a.getStopOrder() : null,
                    allocated ? TimeService.hhmm(a.getPlannedArrival()) : null,
                    allocated ? TimeService.hhmm(a.getPlannedEnd()) : null,
                    allocated, a.getCreatedAt());
        }
    }
}
