package com.servicepros.appointment;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;

import com.servicepros.tenant.TenantEntity;
import com.servicepros.customer.Customer;
import com.servicepros.route.ServiceRoute;
import com.servicepros.task.ServiceTask;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

/**
 * A booked job. Customers book a date only; the stop order and arrival window
 * (stopOrder / plannedArrival / plannedEnd) are filled in by the optimizer.
 */
@Getter
@Setter
@Entity
@Table(name = "appointments", indexes = @Index(name = "idx_appt_date_route", columnList = "date,route_id"))
public class Appointment extends TenantEntity {

    @Column(nullable = false)
    private LocalDate date;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "customer_id", nullable = false)
    private Customer customer;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "task_id", nullable = false)
    private ServiceTask task;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "route_id", nullable = false)
    private ServiceRoute route;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private AppointmentStatus status = AppointmentStatus.SCHEDULED;

    @Column(length = 1000)
    private String notes;

    private Integer stopOrder;
    private LocalTime plannedArrival;
    private LocalTime plannedEnd;
    private Double legKm;

    /** Arrival time last texted to the customer — avoids re-sending an unchanged schedule. */
    private LocalTime notifiedArrival;

    private Instant startedAt;
    private Instant completedAt;
}
