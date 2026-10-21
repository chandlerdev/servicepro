package com.servicepros.notification;

import com.servicepros.tenant.TenantEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

/** Log of every outbound SMS / email (sent, failed or simulated). */
@Getter
@Setter
@Entity
@Table(name = "notifications", indexes = @Index(name = "idx_notif_route", columnList = "routeId"))
public class Notification extends TenantEntity {

    public enum Type { BOOKING, SCHEDULE, UPDATE, DELAY, ENROUTE, REVIEW }

    public enum Channel { SMS, EMAIL }

    public enum Status { SENT, FAILED, SIMULATED }

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Type type;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private Channel channel;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 12)
    private Status status;

    @Column(length = 120)
    private String toName;

    @Column(length = 160)
    private String toAddress;

    @Column(nullable = false, length = 2000)
    private String message;

    @Column(length = 500)
    private String error;

    @Column(length = 80)
    private String providerMessageId;

    private Long customerId;
    private Long appointmentId;
    private Long routeId;
}
