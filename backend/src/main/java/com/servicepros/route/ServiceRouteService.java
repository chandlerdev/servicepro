package com.servicepros.route;

import java.util.LinkedHashSet;
import java.util.List;

import com.servicepros.appointment.AppointmentRepository;
import com.servicepros.appointment.AppointmentStatus;
import com.servicepros.city.CityService;
import com.servicepros.common.ApiException;
import com.servicepros.common.TimeService;
import com.servicepros.route.RouteDtos.RouteRequest;
import com.servicepros.route.RouteDtos.RouteResponse;
import com.servicepros.schedule.PlanStaleMarker;
import com.servicepros.schedule.RoutePlanRepository;
import com.servicepros.technician.Technician;
import com.servicepros.technician.TechnicianRepository;
import com.servicepros.vehicle.VehicleRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class ServiceRouteService {

    private static final List<AppointmentStatus> OPEN = List.of(AppointmentStatus.SCHEDULED,
            AppointmentStatus.CONFIRMED, AppointmentStatus.EN_ROUTE, AppointmentStatus.DELAYED);

    private final ServiceRouteRepository repo;
    private final VehicleRepository vehicles;
    private final TechnicianRepository technicians;
    private final CityService cities;
    private final AppointmentRepository appointments;
    private final RoutePlanRepository plans;
    private final PlanStaleMarker staleMarker;

    @Transactional(readOnly = true)
    public List<RouteResponse> list() {
        return repo.findByDeletedFalseOrderByNameAsc().stream().map(RouteResponse::from).toList();
    }

    @Transactional(readOnly = true)
    public RouteResponse get(Long id) {
        return RouteResponse.from(find(id));
    }

    public ServiceRoute find(Long id) {
        return repo.findById(id).filter(r -> !r.isDeleted()).orElseThrow(() -> ApiException.notFound("Route", id));
    }

    @Transactional
    public RouteResponse create(RouteRequest r) {
        ServiceRoute route = new ServiceRoute();
        apply(route, r);
        return RouteResponse.from(repo.save(route));
    }

    @Transactional
    public RouteResponse update(Long id, RouteRequest r) {
        ServiceRoute route = find(id);
        apply(route, r);
        staleMarker.markRouteFromToday(id);
        return RouteResponse.from(route);
    }

    /** Blocked while open appointments exist; routes with past jobs are hidden (soft delete). */
    @Transactional
    public void delete(Long id) {
        ServiceRoute route = find(id);
        long open = appointments.countByRouteIdAndStatusIn(id, OPEN);
        if (open > 0) {
            throw ApiException.conflict(open + " open appointment(s) on this route need reassignment first");
        }
        if (appointments.existsByRouteId(id)) {
            route.setDeleted(true);
        } else {
            plans.deleteByRouteId(id);
            repo.delete(route);
        }
    }

    private void apply(ServiceRoute route, RouteRequest r) {
        route.setName(r.name().trim());
        route.setVehicle(vehicles.findById(r.vehicleId()).orElseThrow(() -> ApiException.notFound("Vehicle", r.vehicleId())));
        List<Technician> techs = technicians.findAllById(r.technicianIds());
        if (techs.size() != new LinkedHashSet<>(r.technicianIds()).size()) {
            throw ApiException.badRequest("One or more technicians do not exist");
        }
        route.getTechnicians().clear();
        route.getTechnicians().addAll(techs);
        route.setCity(cities.require(r.city()).getName());
        route.setAreaDescription(r.areaDescription());
        route.setStartTime(TimeService.parseHhmm(r.startTime(), "startTime"));
    }
}
