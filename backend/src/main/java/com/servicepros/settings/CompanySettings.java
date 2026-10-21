package com.servicepros.settings;

import com.servicepros.tenant.TenantEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.Getter;
import lombok.Setter;

/**
 * One row per Admin: company profile, depot location (where routes start) and review links.
 * Starts empty — each admin fills in their own details under Settings.
 */
@Getter
@Setter
@Entity
@Table(name = "company_settings", uniqueConstraints = @UniqueConstraint(name = "uk_settings_owner", columnNames = "owner_id"))
public class CompanySettings extends TenantEntity {

    /** Used in customer texts until the admin saves a company name. */
    public static final String FALLBACK_NAME = "your service team";

    @Column(length = 120)
    private String companyName;

    @Column(length = 40)
    private String companyPhone;

    @Column(length = 160)
    private String companyEmail;

    @Column(length = 255)
    private String officeAddress;

    private Double officeLat;
    private Double officeLng;

    @Column(length = 255)
    private String googleReviewUrl;

    @Column(length = 255)
    private String facebookReviewUrl;

    /** Company name for customer texts. */
    public String displayName() {
        return companyName == null || companyName.isBlank() ? FALLBACK_NAME : companyName;
    }

    /** True once the depot (route start point) has been saved. */
    public boolean hasDepot() {
        return officeLat != null && officeLng != null;
    }
}
