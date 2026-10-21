package com.servicepros.tenant;

import com.servicepros.common.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.MappedSuperclass;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.TenantId;

/**
 * Base class for records that belong to one Admin. {@code ownerId} is set automatically
 * from the logged-in admin/technician and every query is filtered by it.
 */
@Getter
@Setter
@MappedSuperclass
public abstract class TenantEntity extends BaseEntity {

    @TenantId
    @Column(name = "owner_id", nullable = false, updatable = false)
    private Long ownerId;
}
