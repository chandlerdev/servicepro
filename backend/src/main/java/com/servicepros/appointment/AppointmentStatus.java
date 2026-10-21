package com.servicepros.appointment;

public enum AppointmentStatus {
    SCHEDULED, CONFIRMED, EN_ROUTE, DELAYED, COMPLETED, CANCELLED;

    public boolean isOpen() {
        return this != COMPLETED && this != CANCELLED;
    }
}
