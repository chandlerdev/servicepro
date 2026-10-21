package com.servicepros.webhook;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Base64;
import java.util.Map;
import java.util.TreeMap;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

import com.servicepros.config.AppProperties;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.util.HtmlUtils;

/** Inbound SMS from Twilio (replies like "C" / "R"). Requests are verified with X-Twilio-Signature. */
@Slf4j
@Tag(name = "Webhooks")
@RestController
@RequestMapping("/api/webhooks/twilio")
@RequiredArgsConstructor
public class TwilioWebhookController {

    private final InboundSmsService inboundSms;
    private final AppProperties props;

    @PostMapping(value = "/sms", consumes = MediaType.APPLICATION_FORM_URLENCODED_VALUE, produces = MediaType.APPLICATION_XML_VALUE)
    public ResponseEntity<String> sms(HttpServletRequest request,
                                      @RequestHeader(value = "X-Twilio-Signature", required = false) String signature) {
        AppProperties.Twilio cfg = props.getTwilio();
        if (!cfg.isConfigured()) {
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body("<Response/>");
        }
        Map<String, String> params = new TreeMap<>();
        request.getParameterMap().forEach((k, v) -> params.put(k, v.length > 0 ? v[0] : ""));

        String url = StringUtils.hasText(cfg.getWebhookUrl()) ? cfg.getWebhookUrl() : request.getRequestURL().toString();
        if (!valid(url, params, signature, cfg.getAuthToken())) {
            log.warn("Rejected Twilio webhook with invalid signature from {}", request.getRemoteAddr());
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body("<Response/>");
        }

        String reply = inboundSms.handle(params.get("From"), params.get("Body"));
        String xml = reply == null ? "<Response/>"
                : "<Response><Message>" + HtmlUtils.htmlEscape(reply) + "</Message></Response>";
        return ResponseEntity.ok(xml);
    }

    /** Twilio: Base64(HMAC-SHA1(authToken, url + concatenated sorted key/value pairs)). */
    static boolean valid(String url, Map<String, String> sortedParams, String signature, String authToken) {
        if (signature == null) return false;
        try {
            StringBuilder data = new StringBuilder(url);
            sortedParams.forEach((k, v) -> data.append(k).append(v));
            Mac mac = Mac.getInstance("HmacSHA1");
            mac.init(new SecretKeySpec(authToken.getBytes(StandardCharsets.UTF_8), "HmacSHA1"));
            String expected = Base64.getEncoder().encodeToString(mac.doFinal(data.toString().getBytes(StandardCharsets.UTF_8)));
            return MessageDigest.isEqual(expected.getBytes(StandardCharsets.UTF_8), signature.getBytes(StandardCharsets.UTF_8));
        } catch (Exception e) {
            return false;
        }
    }
}
