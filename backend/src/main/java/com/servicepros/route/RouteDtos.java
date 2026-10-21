package com.servicepros.route;

import java.util.List;

import com.servicepros.common.TimeService;
import com.servicepros.technician.TechnicianDtos.TechnicianRef;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public final class RouteDtos {

    private RouteDtos() {
    }

    public record RouteRequest(@NotBlank @Size(max = 120) String name,
                               @NotNull Long vehicleId,
                               @NotEmpty(message = "assign at least one technician") List<Long> technicianIds,
                               @NotBlank @Size(max = 80) String city,
                               @Size(max = 200) String areaDescription,
                               @NotBlank @Pattern(regexp = "^([01]\\d|2[0-3]):[0-5]\\d$", message = "must be HH:mm") String startTime) {
    }

    public record RouteResponse(Long id, String name, Long vehicleId, String vehicleName, String vehiclePlate,
                                List<Long> technicianIds, List<TechnicianRef> technicians, String city,
                                String areaDescription, String startTime) {
        public static RouteResponse from(ServiceRoute r) {
            var techs = r.getTechnicians().stream().map(TechnicianRef::from).toList();
            var v = r.getVehicle();
            return new RouteResponse(r.getId(), r.getName(), v == null ? null : v.getId(), v == null ? null : v.getName(),
                    v == null ? null : v.getPlate(), techs.stream().map(TechnicianRef::id).toList(), techs,
                    r.getCity(), r.getAreaDescription(), TimeService.hhmm(r.getStartTime()));
        }
    }
}
