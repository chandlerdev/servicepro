package com.servicepros.schedule;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

import com.servicepros.appointment.AppointmentStatus;
import com.servicepros.technician.TechnicianDtos.TechnicianRef;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

public final class ScheduleDtos {

    private ScheduleDtos() {
    }

    public record OfficeDto(String name, String address, Double lat, Double lng) {
    }

    public record StopDto(Long appointmentId, int order, Long customerId, String customerName, String customerPhone,
                          String address, double lat, double lng, String taskName, int durationMinutes,
                          AppointmentStatus status, String notes, String plannedArrival, String plannedEnd,
                          Double legKm) {
    }

    public record RoutePlanDto(Long routeId, String routeName, String vehicleName, String vehiclePlate,
                               List<TechnicianRef> technicians, String startTime, boolean optimized, boolean stale,
                               Double totalKm, String endTime, Instant optimizedAt, Instant notifiedAt,
                               List<String> googleMapsUrls, List<StopDto> stops) {
    }

    public record DaySchedule(LocalDate date, OfficeDto office, List<RoutePlanDto> routes) {
    }

    public record AllocateResult(DaySchedule schedule, int notified) {
    }

    public record DateRouteRequest(@NotNull LocalDate date, Long routeId) {
    }
}
