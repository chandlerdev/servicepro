package com.servicepros.notification;

import com.servicepros.config.AppProperties;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

/** Sends plain-text email when SMTP (MAIL_HOST) is configured; otherwise simulates. */
@Slf4j
@Component
public class MailGateway {

    private final ObjectProvider<JavaMailSender> senderProvider;
    private final AppProperties props;
    private final String host;

    public MailGateway(ObjectProvider<JavaMailSender> senderProvider, AppProperties props,
                       @Value("${spring.mail.host:}") String host) {
        this.senderProvider = senderProvider;
        this.props = props;
        this.host = host;
    }

    /** An empty MAIL_HOST still creates a mail sender in Spring, so check the host explicitly. */
    public boolean isConfigured() {
        return StringUtils.hasText(host) && senderProvider.getIfAvailable() != null;
    }

    public SmsGateway.Result send(String to, String subject, String body) {
        if (!isConfigured()) {
            log.info("[Email simulated] to {}: {}", to, subject);
            return new SmsGateway.Result(Notification.Status.SIMULATED, null, null);
        }
        try {
            SimpleMailMessage msg = new SimpleMailMessage();
            msg.setFrom(props.getMail().getFrom());
            msg.setTo(to);
            msg.setSubject(subject);
            msg.setText(body);
            senderProvider.getObject().send(msg);
            return new SmsGateway.Result(Notification.Status.SENT, null, null);
        } catch (Exception e) {
            log.warn("Email to {} failed: {}", to, e.getMessage());
            String err = e.getMessage() == null ? "Mail error" : e.getMessage();
            return new SmsGateway.Result(Notification.Status.FAILED, null, err.length() > 480 ? err.substring(0, 480) : err);
        }
    }
}
