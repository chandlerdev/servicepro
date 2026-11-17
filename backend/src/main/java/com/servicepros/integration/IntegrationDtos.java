package com.servicepros.integration;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * Super Admin → Settings → Integrations. Secrets (auth token, API key, SMTP password) are write-only:
 * responses only say whether one is set and show its last 4 characters; a blank secret on save keeps the current one.
 */
public final class IntegrationDtos {

    private IntegrationDtos() {
    }

    public record SmsView(boolean connected, String accountSid, boolean authTokenSet, String authTokenHint,
                          String fromNumber, String webhookUrl) {
    }

    public record MapsView(boolean connected, boolean apiKeySet, String apiKeyHint, String regions) {
    }

    public record MailView(boolean connected, String host, Integer port, String username, boolean passwordSet,
                           String from) {
    }

    public record IntegrationsView(SmsView sms, MapsView maps, MailView mail) {
    }

    public record SmsRequest(
            @NotBlank(message = "Account SID is required")
            @Pattern(regexp = "^\\s*AC[0-9a-fA-F]{32}\\s*$", message = "Starts with AC followed by 32 letters/digits (from the Twilio console)")
            String accountSid,
            @Size(max = 128) String authToken,
            @NotBlank(message = "Sending number is required")
            @Pattern(regexp = "^\\s*(\\+[1-9]\\d{7,14}|MG[0-9a-fA-F]{32})\\s*$",
                    message = "Full number with country code, e.g. +15125550100 (or a Messaging Service SID MG…)")
            String fromNumber,
            @Pattern(regexp = "^\\s*(https://\\S+)?\\s*$", message = "Must start with https://")
            @Size(max = 500) String webhookUrl) {
    }

    public record MapsRequest(
            @Size(max = 200) String apiKey,
            @Pattern(regexp = "^\\s*([A-Za-z]{2}\\s*(,\\s*[A-Za-z]{2}\\s*)*)?$",
                    message = "2-letter country codes separated by commas, e.g. us, ca — leave empty for worldwide")
            String regions) {
    }

    public record MailRequest(
            @NotBlank(message = "SMTP server is required") @Size(max = 200) String host,
            @NotNull(message = "Port is required") @Min(value = 1, message = "1–65535") @Max(value = 65535, message = "1–65535")
            Integer port,
            @Size(max = 200) String username,
            @Size(max = 200) String password,
            @NotBlank(message = "From address is required") @Email(message = "Enter a valid email") @Size(max = 200)
            String from) {
    }

    public record TestEmailRequest(@NotBlank(message = "Email is required") @Email(message = "Enter a valid email") String to) {
    }
}
