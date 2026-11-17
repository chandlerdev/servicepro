package com.servicepros.notification;

import com.servicepros.config.AppProperties;
import com.servicepros.integration.IntegrationSettingsService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Component;

/**
 * Sends plain-text email through the SMTP server set by the Super Admin (Settings → Integrations → Email);
 * when none is set, emails are only logged (simulated).
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class MailGateway {

    private final IntegrationSettingsService integrations;
    private final AppProperties props;

    public boolean isConfigured() {
        return integrations.isMailConfigured();
    }

    public SmsGateway.Result send(String to, String subject, String body) {
        JavaMailSender sender = integrations.mailSender();
        if (sender == null) {
            log.info("[Email simulated] to {}: {}", to, subject);
            return new SmsGateway.Result(Notification.Status.SIMULATED, null, null);
        }
        try {
            SimpleMailMessage msg = new SimpleMailMessage();
            msg.setFrom(props.getMail().getFrom());
            msg.setTo(to);
            msg.setSubject(subject);
            msg.setText(body);
            sender.send(msg);
            return new SmsGateway.Result(Notification.Status.SENT, null, null);
        } catch (Exception e) {
            log.warn("Email to {} failed: {}", to, e.getMessage());
            String err = e.getMessage() == null ? "Mail error" : e.getMessage();
            return new SmsGateway.Result(Notification.Status.FAILED, null, err.length() > 480 ? err.substring(0, 480) : err);
        }
    }
}
