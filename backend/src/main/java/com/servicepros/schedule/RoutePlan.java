package com.servicepros.schedule;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;

import com.servicepros.tenant.TenantEntity;
import com.servicepros.route.ServiceRoute;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.Getter;
import lombok.Setter;

/** Optimization result for one route on one date. Per-stop order/ETAs live on the appointments. */
@Getter
@Setter
@Entity
@Table(name = "route_plans", uniqueConstraints = @UniqueConstraint(name = "uk_plan_date_route", columnNames = {"date", "route_id"}))
public class RoutePlan extends TenantEntity {

    @Column(nullable = false)
    private LocalDate date;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "route_id", nullable = false)
    private ServiceRoute route;

    private Double totalKm;
    private LocalTime endTime;

    /** True when appointments/addresses/durations changed after the last optimization. */
    private boolean stale;

    private Instant optimizedAt;
    private Instant notifiedAt;
}
