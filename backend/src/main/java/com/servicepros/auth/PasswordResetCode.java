package com.servicepros.auth;

import java.time.Instant;

import com.servicepros.common.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

/** A one-time "forgot password" code emailed to an Admin or Technician. Only its hash is stored. */
@Getter
@Setter
@Entity
@Table(name = "password_reset_codes", indexes = @Index(name = "ix_reset_user", columnList = "userId"))
public class PasswordResetCode extends BaseEntity {

    @Column(nullable = false)
    private Long userId;

    @Column(nullable = false, length = 64)
    private String codeHash;

    @Column(nullable = false)
    private Instant expiresAt;

    private int attempts;

    private boolean used;
}
