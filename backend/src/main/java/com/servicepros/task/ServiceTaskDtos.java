package com.servicepros.task;

import java.math.BigDecimal;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public final class ServiceTaskDtos {

    private ServiceTaskDtos() {
    }

    public record ServiceTaskRequest(@NotBlank @Size(max = 120) String name,
                                     @NotNull @DecimalMin("0") BigDecimal rate,
                                     @NotNull @Min(5) @Max(720) Integer durationMinutes) {
    }

    public record ServiceTaskResponse(Long id, String name, BigDecimal rate, int durationMinutes) {
        public static ServiceTaskResponse from(ServiceTask t) {
            return new ServiceTaskResponse(t.getId(), t.getName(), t.getRate(), t.getDurationMinutes());
        }
    }
}
