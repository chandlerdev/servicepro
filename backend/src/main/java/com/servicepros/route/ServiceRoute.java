package com.servicepros.route;

import java.time.LocalTime;
import java.util.LinkedHashSet;
import java.util.Set;

import com.servicepros.tenant.TenantEntity;
import com.servicepros.technician.Technician;
import com.servicepros.vehicle.Vehicle;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.JoinTable;
import jakarta.persistence.ManyToMany;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

/** A daily route: one vehicle, one or more technicians, one city. */
@Getter
@Setter
@Entity
@Table(name = "service_routes")
public class ServiceRoute extends TenantEntity {

    @Column(nullable = false, length = 120)
    private String name;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "vehicle_id")
    private Vehicle vehicle;

    @ManyToMany(fetch = FetchType.LAZY)
    @JoinTable(name = "route_technicians",
            joinColumns = @JoinColumn(name = "route_id"),
            inverseJoinColumns = @JoinColumn(name = "technician_id"))
    @OrderBy("name ASC")
    private Set<Technician> technicians = new LinkedHashSet<>();

    @Column(nullable = false, length = 80)
    private String city;

    @Column(length = 200)
    private String areaDescription;

    @Column(nullable = false)
    private LocalTime startTime = LocalTime.of(8, 0);

    /** Soft delete: routes with job history are hidden instead of removed. */
    private boolean deleted = false;

    public boolean hasTechnician(Long technicianId) {
        return technicianId != null && technicians.stream().anyMatch(t -> technicianId.equals(t.getId()));
    }
}
