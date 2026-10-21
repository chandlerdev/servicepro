package com.servicepros.task;

import java.math.BigDecimal;

import com.servicepros.tenant.TenantEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

/** An item in the service catalog ("Task Master"): rate + time on site. */
@Getter
@Setter
@Entity
@Table(name = "service_tasks")
public class ServiceTask extends TenantEntity {

    @Column(nullable = false, length = 120)
    private String name;

    @Column(nullable = false, precision = 10, scale = 2)
    private BigDecimal rate;

    private int durationMinutes;

    /** Soft delete: tasks used by appointments are hidden instead of removed. */
    private boolean deleted = false;
}
