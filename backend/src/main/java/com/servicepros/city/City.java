package com.servicepros.city;

import com.servicepros.common.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.Getter;
import lombok.Setter;

/**
 * A city / service area (City Master). One shared list, managed by the Super Admin; every admin's customers
 * and routes pick one of these by name. The centre point is the fallback location for a customer whose
 * address can't be geocoded.
 */
@Getter
@Setter
@Entity
@Table(name = "cities", uniqueConstraints = @UniqueConstraint(name = "uk_city_name", columnNames = "name"))
public class City extends BaseEntity {

    @Column(nullable = false, length = 80)
    private String name;

    @Column(nullable = false)
    private double lat;

    @Column(nullable = false)
    private double lng;
}
