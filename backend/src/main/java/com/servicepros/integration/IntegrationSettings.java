package com.servicepros.integration;

import com.servicepros.common.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

/**
 * The system-wide integration keys (one row, shared by every admin), edited by the Super Admin on Settings.
 * On the first start the values are copied from the server's env file; after that this row is what is used.
 */
@Getter
@Setter
@Entity
@Table(name = "integration_settings")
public class IntegrationSettings extends BaseEntity {

    // SMS (Twilio)
    @Column(length = 64)
    private String twilioAccountSid;
    @Column(length = 128)
    private String twilioAuthToken;
    @Column(length = 40)
    private String twilioFromNumber;
    @Column(length = 500)
    private String twilioWebhookUrl;

    // Google Maps
    @Column(length = 200)
    private String googleMapsApiKey;
    /** comma separated ISO country codes for address suggestions; empty = worldwide */
    @Column(length = 200)
    private String googlePlacesRegions;

    // Email (SMTP)
    @Column(length = 200)
    private String mailHost;
    private Integer mailPort;
    @Column(length = 200)
    private String mailUsername;
    @Column(length = 200)
    private String mailPassword;
    @Column(length = 200)
    private String mailFrom;
}
