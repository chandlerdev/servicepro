package com.servicepros.vehicle;

import com.servicepros.tenant.TenantEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Entity
@Table(name = "vehicles")
public class Vehicle extends TenantEntity {

    @Column(nullable = false, length = 80)
    private String name;

    @Column(length = 30)
    private String plate;

    @Column(length = 60)
    private String capacity;
}
