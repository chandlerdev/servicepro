package com.servicepros.auth;

import java.time.Instant;

import com.servicepros.common.BaseEntity;
import com.servicepros.technician.Technician;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Entity
@Table(name = "users")
public class User extends BaseEntity {

    public enum Role { SUPER_ADMIN, ADMIN, TECHNICIAN }

    @Column(nullable = false, length = 120)
    private String name;

    @Column(nullable = false, unique = true, length = 160)
    private String email;

    @Column(nullable = false, length = 100)
    private String passwordHash;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 15)
    private Role role;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "technician_id", unique = true)
    private Technician technician;

    private boolean active = true;

    /** Soft delete (admins): the row and all of the admin's data are kept, but hidden and unable to sign in. */
    private Instant deletedAt;
}
