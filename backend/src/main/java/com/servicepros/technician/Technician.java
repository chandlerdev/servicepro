package com.servicepros.technician;

import com.servicepros.tenant.TenantEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Entity
@Table(name = "technicians",
        uniqueConstraints = @UniqueConstraint(name = "uk_technician_owner_email", columnNames = {"owner_id", "email"}))
public class Technician extends TenantEntity {

    @Column(nullable = false, length = 120)
    private String name;

    /** e.g. "+1" (null for rows created before the country code existed) */
    @Column(length = 4)
    private String phoneCountryCode;

    /** national number, digits only, e.g. "2815550100" */
    @Column(length = 40)
    private String phone;

    /** also the technician's app / website login */
    @Column(length = 160)
    private String email;

    @Column(length = 200)
    private String skills;
}
