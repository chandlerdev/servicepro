package com.servicepros.notification;

import com.servicepros.appointment.Appointment;
import com.servicepros.customer.Customer;
import com.servicepros.notification.NotificationDtos.NotificationResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

/** Sends SMS / email and records every attempt in the notification log. Never throws on provider errors. */
@Service
@RequiredArgsConstructor
public class NotificationService {

    private final NotificationRepository repo;
    private final SmsGateway smsGateway;
    private final MailGateway mailGateway;

    @Transactional
    public NotificationResponse smsToCustomer(Customer c, Appointment a, Notification.Type type, String message) {
        Notification n = base(type, Notification.Channel.SMS, c.getName(), c.getPhone(), message);
        n.setCustomerId(c.getId());
        if (a != null) {
            n.setAppointmentId(a.getId());
            n.setRouteId(a.getRoute().getId());
        }
        apply(n, smsGateway.send(c.getPhone(), message));
        return NotificationResponse.from(repo.save(n));
    }

    @Transactional
    public void emailToCustomer(Customer c, Appointment a, Notification.Type type, String subject, String body) {
        if (!StringUtils.hasText(c.getEmail())) return;
        Notification n = base(type, Notification.Channel.EMAIL, c.getName(), c.getEmail(), subject + "\n\n" + body);
        n.setCustomerId(c.getId());
        if (a != null) {
            n.setAppointmentId(a.getId());
            n.setRouteId(a.getRoute().getId());
        }
        apply(n, mailGateway.send(c.getEmail(), subject, body));
        repo.save(n);
    }

    private Notification base(Notification.Type type, Notification.Channel channel, String name, String to, String msg) {
        Notification n = new Notification();
        n.setType(type);
        n.setChannel(channel);
        n.setToName(name);
        n.setToAddress(to);
        n.setMessage(msg.length() > 2000 ? msg.substring(0, 2000) : msg);
        return n;
    }

    private void apply(Notification n, SmsGateway.Result r) {
        n.setStatus(r.status());
        n.setProviderMessageId(r.providerId());
        n.setError(r.error());
    }
}
