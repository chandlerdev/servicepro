package com.servicepros.webhook;

import java.util.Map;
import java.util.TreeMap;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class TwilioSignatureTest {

    /** Example from Twilio's "Validating requests" documentation. */
    @Test
    void validatesTwilioDocumentationExample() {
        Map<String, String> params = new TreeMap<>(Map.of(
                "CallSid", "CA1234567890ABCDE",
                "Caller", "+14158675309",
                "Digits", "1234",
                "From", "+14158675309",
                "To", "+18005551212"));
        String url = "https://mycompany.com/myapp.php?foo=1&bar=2";
        String token = "12345";

        assertThat(TwilioWebhookController.valid(url, params, "RSOYDt4T1cUTdK1PDd93/VVr8B8=", token)).isTrue();
        assertThat(TwilioWebhookController.valid(url, params, "invalid", token)).isFalse();
        assertThat(TwilioWebhookController.valid(url, params, null, token)).isFalse();
    }
}
