package com.servicepros.notification;

import java.util.Map;

import com.servicepros.common.Phones;
import com.servicepros.config.AppProperties;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

/**
 * Twilio Programmable Messaging via its REST API (no SDK needed).
 * When Twilio credentials are missing the message is only logged and reported as SIMULATED.
 */
@Slf4j
@Component
public class SmsGateway {

    public record Result(Notification.Status status, String providerId, String error) {
    }

    private final AppProperties.Twilio cfg;
    private final RestClient client;

    public SmsGateway(AppProperties props, RestClient.Builder builder) {
        this.cfg = props.getTwilio();
        this.client = builder.baseUrl("https://api.twilio.com/2010-04-01").build();
    }

    public boolean isConfigured() {
        return cfg.isConfigured();
    }

    @SuppressWarnings("unchecked")
    public Result send(String toPhone, String body) {
        String to = Phones.e164(toPhone);
        if (to == null || to.length() < 11) {
            return new Result(Notification.Status.FAILED, null, "Invalid phone number");
        }
        if (!cfg.isConfigured()) {
            log.info("[SMS simulated] to {}: {}", to, body);
            return new Result(Notification.Status.SIMULATED, null, null);
        }
        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("To", to);
        form.add("From", cfg.getFromNumber());
        form.add("Body", body);
        try {
            Map<String, Object> res = client.post()
                    .uri("/Accounts/{sid}/Messages.json", cfg.getAccountSid())
                    .headers(h -> h.setBasicAuth(cfg.getAccountSid(), cfg.getAuthToken()))
                    .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                    .body(form)
                    .retrieve()
                    .body(Map.class);
            return new Result(Notification.Status.SENT, res == null ? null : String.valueOf(res.get("sid")), null);
        } catch (RestClientResponseException e) {
            log.warn("Twilio rejected SMS to {}: {} {}", to, e.getStatusCode(), e.getResponseBodyAsString());
            return new Result(Notification.Status.FAILED, null, truncate("Twilio " + e.getStatusCode().value() + ": " + e.getResponseBodyAsString()));
        } catch (Exception e) {
            log.warn("Twilio call failed for {}: {}", to, e.getMessage());
            return new Result(Notification.Status.FAILED, null, truncate(e.getMessage()));
        }
    }

    private static String truncate(String s) {
        return s == null ? null : s.length() > 480 ? s.substring(0, 480) : s;
    }
}
