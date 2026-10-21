package com.servicepros.superadmin;

import com.servicepros.common.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

/**
 * Company and billing details the Super Admin records for an Admin (one row per ADMIN user).
 * Only a masked card is kept — brand, last 4 digits, expiry and holder name. The full card number
 * and CVV are never stored (PCI DSS); charging a card needs a payment gateway.
 */
@Getter
@Setter
@Entity
@Table(name = "admin_profiles")
public class AdminProfile extends BaseEntity {

    @Column(nullable = false, unique = true)
    private Long userId;

    @Column(nullable = false, length = 120)
    private String companyName;

    @Column(nullable = false, length = 120)
    private String contactName;

    /** e.g. "+1" */
    @Column(length = 4)
    private String contactCountryCode;

    /** national number, digits only, e.g. "2815550100" */
    @Column(nullable = false, length = 40)
    private String contactPhone;

    @Column(length = 120)
    private String cardHolder;

    @Column(length = 20)
    private String cardBrand;

    @Column(length = 4)
    private String cardLast4;

    private Integer cardExpMonth;

    private Integer cardExpYear;

    /** Set when the Super Admin deletes the admin (soft delete) — frees the company name and mobile for reuse. */
    private boolean deleted;

    /** The admin's login email at the time of deletion (the user row gets a placeholder so the email can be reused). */
    @Column(length = 160)
    private String deletedEmail;
}
