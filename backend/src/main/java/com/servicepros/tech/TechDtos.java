package com.servicepros.tech;

import java.time.LocalDate;
import java.util.List;

import com.servicepros.auth.AuthDtos.UserResponse;
import com.servicepros.schedule.ScheduleDtos.OfficeDto;
import com.servicepros.schedule.ScheduleDtos.RoutePlanDto;
import com.servicepros.schedule.ScheduleDtos.StopDto;
import com.servicepros.technician.TechnicianDtos.TechnicianResponse;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public final class TechDtos {

    private TechDtos() {
    }

    public record RouteSummary(Long id, String name, String vehicleName, String vehiclePlate, String city) {
    }

    public record MeResponse(UserResponse user, TechnicianResponse technician, List<RouteSummary> routes) {
    }

    public record StopsResponse(LocalDate date, OfficeDto office, TechnicianResponse technician, List<RoutePlanDto> routes) {
    }

    public record DelayRequest(@NotNull @Min(5) @Max(480) Integer minutes, @Size(max = 200) String reason) {
    }

    public record DelayResponse(int notified) {
    }

    public record CompleteRequest(@Size(max = 1000) String notes) {
    }

    public record CompleteResponse(StopDto appointment) {
    }
}
