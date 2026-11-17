package com.servicepros.superadmin;

import java.util.ArrayList;
import java.util.List;

import com.servicepros.config.AppProperties;
import com.servicepros.notification.MailGateway;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Super Admin settings: the system-wide integrations (SMS, Google Maps, Email). They are shared by every admin and
 * set in the server's env file, so this is read-only. Secrets are never returned — keys show only their last 4.
 */
@Tag(name = "Super Admin · Settings")
@RestController
@RequestMapping("/api/super/settings")
@RequiredArgsConstructor
public class SuperSettingsController {

    private final AppProperties props;
    private final MailGateway mailGateway;

    @Value("${spring.mail.host:}")
    private String mailHost;
    @Value("${spring.mail.port:}")
    private String mailPort;
    @Value("${spring.mail.username:}")
    private String mailUsername;

    public record Detail(String label, String value) {}

    public record Integration(String key, String name, String purpose, boolean connected,
                              List<Detail> details, List<String> envVars) {}

    @GetMapping("/integrations")
    public List<Integration> integrations() {
        AppProperties.Twilio t = props.getTwilio();
        AppProperties.Google g = props.getGoogle();

        List<Detail> sms = new ArrayList<>();
        sms.add(new Detail("Account SID", masked(t.getAccountSid())));
        sms.add(new Detail("Auth token", StringUtils.hasText(t.getAuthToken()) ? "Set" : "Not set"));
        sms.add(new Detail("Sending number", text(t.getFromNumber())));
        sms.add(new Detail("Reply webhook URL", text(t.getWebhookUrl())));

        List<Detail> maps = new ArrayList<>();
        maps.add(new Detail("API key", masked(g.getMapsApiKey())));
        maps.add(new Detail("Address suggestions from",
                g.getPlacesRegions() == null || g.getPlacesRegions().isEmpty()
                        ? "Worldwide" : String.join(", ", g.getPlacesRegions()).toUpperCase()));

        List<Detail> mail = new ArrayList<>();
        mail.add(new Detail("SMTP server", StringUtils.hasText(mailHost)
                ? mailHost + (StringUtils.hasText(mailPort) ? ":" + mailPort : "") : "Not set"));
        mail.add(new Detail("Username", text(mailUsername)));
        mail.add(new Detail("From address", text(props.getMail().getFrom())));

        return List.of(
                new Integration("sms", "SMS (Twilio)",
                        "Appointment texts to customers and their replies",
                        t.isConfigured(), sms,
                        List.of("TWILIO_ACCOUNT_SID", "TWILIO_AUTH_TOKEN", "TWILIO_FROM_NUMBER", "TWILIO_WEBHOOK_URL")),
                new Integration("maps", "Google Maps (geocoding & routing)",
                        "Address suggestions, map pins and drive times for route planning",
                        g.isConfigured(), maps,
                        List.of("GOOGLE_MAPS_API_KEY", "GOOGLE_PLACES_REGIONS")),
                new Integration("mail", "Email (SMTP)",
                        "Forgot-password codes and email notifications",
                        mailGateway.isConfigured(), mail,
                        List.of("MAIL_HOST", "MAIL_PORT", "MAIL_USERNAME", "MAIL_PASSWORD", "MAIL_FROM")));
    }

    private static String text(String v) {
        return StringUtils.hasText(v) ? v.trim() : "Not set";
    }

    /** "••••1234" — enough to tell which key is in use without exposing it. */
    private static String masked(String v) {
        if (!StringUtils.hasText(v)) return "Not set";
        String s = v.trim();
        return s.length() <= 4 ? "••••" : "••••" + s.substring(s.length() - 4);
    }
}
