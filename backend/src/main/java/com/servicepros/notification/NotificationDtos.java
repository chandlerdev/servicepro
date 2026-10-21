package com.servicepros.notification;

import java.time.Instant;

public final class NotificationDtos {

    private NotificationDtos() {
    }

    public record NotificationResponse(Long id, Notification.Type type, Notification.Channel channel,
                                       Notification.Status status, String toName, String toAddress, String message,
                                       String error, Long appointmentId, Instant createdAt) {
        public static NotificationResponse from(Notification n) {
            return new NotificationResponse(n.getId(), n.getType(), n.getChannel(), n.getStatus(), n.getToName(),
                    n.getToAddress(), n.getMessage(), n.getError(), n.getAppointmentId(), n.getCreatedAt());
        }
    }
}
