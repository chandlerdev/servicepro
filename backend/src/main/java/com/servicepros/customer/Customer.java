package com.servicepros.customer;

import com.servicepros.tenant.TenantEntity;
import com.servicepros.common.Phones;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Index;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Entity
@Table(name = "customers", indexes = @Index(name = "idx_customer_phone_digits", columnList = "phoneDigits"))
public class Customer extends TenantEntity {

    @Column(nullable = false, length = 120)
    private String name;

    @Column(nullable = false, length = 40)
    private String phone;

    /** Last 10 digits of the phone, used to match inbound SMS replies. */
    @Column(length = 15)
    private String phoneDigits;

    @Column(length = 160)
    private String email;

    @Column(nullable = false, length = 255)
    private String address;

    @Column(nullable = false, length = 80)
    private String city;

    private double lat;
    private double lng;

    /** Soft delete: keeps job history intact. */
    private boolean deleted = false;

    @PrePersist
    @PreUpdate
    void normalize() {
        phoneDigits = Phones.last10(phone);
    }

    public String firstName() {
        return name == null ? "" : name.trim().split("\\s+")[0];
    }
}
