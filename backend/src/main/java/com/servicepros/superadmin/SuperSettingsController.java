package com.servicepros.superadmin;

import java.util.Map;

import com.servicepros.common.ApiException;
import com.servicepros.integration.IntegrationDtos.IntegrationsView;
import com.servicepros.integration.IntegrationDtos.MailRequest;
import com.servicepros.integration.IntegrationDtos.MapsRequest;
import com.servicepros.integration.IntegrationDtos.SmsRequest;
import com.servicepros.integration.IntegrationDtos.TestEmailRequest;
import com.servicepros.integration.IntegrationSettingsService;
import com.servicepros.notification.MailGateway;
import com.servicepros.notification.Notification;
import com.servicepros.notification.SmsGateway;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Super Admin → Settings → Integrations: add, edit or remove the SMS, Google Maps and Email keys every admin
 * shares. Changes apply immediately (no restart). Secrets are never returned, only their last 4 characters.
 */
@Tag(name = "Super Admin · Settings")
@RestController
@RequestMapping("/api/super/settings/integrations")
@RequiredArgsConstructor
public class SuperSettingsController {

    private final IntegrationSettingsService service;
    private final MailGateway mail;

    @GetMapping
    public IntegrationsView get() {
        return service.view();
    }

    @PutMapping("/sms")
    public IntegrationsView sms(@Valid @RequestBody SmsRequest req) {
        return service.saveSms(req);
    }

    @PutMapping("/maps")
    public IntegrationsView maps(@Valid @RequestBody MapsRequest req) {
        return service.saveMaps(req);
    }

    @PutMapping("/mail")
    public IntegrationsView mail(@Valid @RequestBody MailRequest req) {
        return service.saveMail(req);
    }

    @DeleteMapping("/{key}")
    public IntegrationsView disconnect(@PathVariable String key) {
        return service.disconnect(key);
    }

    /** Sends a test email with the saved SMTP settings, so the Super Admin can check them. */
    @PostMapping("/mail/test")
    public Map<String, String> testMail(@Valid @RequestBody TestEmailRequest req) {
        if (!mail.isConfigured()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Save the email settings first");
        }
        SmsGateway.Result r = mail.send(req.to().trim(), "ServProWare test email",
                "This is a test email from ServProWare. Your email (SMTP) settings are working.");
        if (r.status() != Notification.Status.SENT) {
            throw new ApiException(HttpStatus.BAD_GATEWAY, "Email could not be sent: " + r.error());
        }
        return Map.of("message", "Test email sent to " + req.to().trim());
    }
}
