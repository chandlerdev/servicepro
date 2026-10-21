package com.servicepros.schedule;

import java.time.LocalDate;
import java.util.Collection;

import com.servicepros.appointment.Appointment;
import com.servicepros.common.TimeService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/** Flags route plans whose inputs changed so the UI shows "re-optimize" and ETAs are not trusted. */
@Component
@RequiredArgsConstructor
public class PlanStaleMarker {

    private final RoutePlanRepository plans;
    private final TimeService time;

    @Transactional
    public void mark(LocalDate date, Long routeId) {
        if (date == null || routeId == null) return;
        plans.findByDateAndRouteId(date, routeId).ifPresent(p -> p.setStale(true));
    }

    @Transactional
    public void markAll(Collection<Appointment> appointments) {
        appointments.forEach(a -> mark(a.getDate(), a.getRoute().getId()));
    }

    @Transactional
    public void markRouteFromToday(Long routeId) {
        plans.markStaleFrom(routeId, time.today());
    }
}
