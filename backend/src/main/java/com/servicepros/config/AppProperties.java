package com.servicepros.config;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.util.StringUtils;

@Getter
@Setter
@ConfigurationProperties(prefix = "app")
public class AppProperties {

    private String timezone = "America/Chicago";
    private Cors cors = new Cors();
    private Jwt jwt = new Jwt();
    private RateLimit rateLimit = new RateLimit();
    private Twilio twilio = new Twilio();
    private Google google = new Google();
    private Mail mail = new Mail();
    private Optimizer optimizer = new Optimizer();
    private SuperAdmin superAdmin = new SuperAdmin();

    /** First Super Admin login, created at startup when none exists. */
    @Getter @Setter
    public static class SuperAdmin {
        private String email;
        private String password;
    }

    @Getter @Setter
    public static class Cors {
        private List<String> allowedOrigins = new ArrayList<>();
    }

    @Getter @Setter
    public static class Jwt {
        private String secret;
        private Duration accessTtl = Duration.ofMinutes(15);
        private Duration refreshTtl = Duration.ofDays(30);
    }

    @Getter @Setter
    public static class RateLimit {
        private int loginPerMinute = 10;
        private boolean trustForwardedHeaders = false;
    }

    @Getter @Setter
    public static class Twilio {
        private String accountSid;
        private String authToken;
        private String fromNumber;
        private String webhookUrl;

        public boolean isConfigured() {
            return StringUtils.hasText(accountSid) && StringUtils.hasText(authToken) && StringUtils.hasText(fromNumber);
        }
    }

    @Getter @Setter
    public static class Google {
        private String mapsApiKey;
        /** Countries address suggestions come from (ISO codes, e.g. us). Empty = worldwide. */
        private List<String> placesRegions = new ArrayList<>(List.of("us"));

        public boolean isConfigured() {
            return StringUtils.hasText(mapsApiKey);
        }
    }

    @Getter @Setter
    public static class Mail {
        private String from = "no-reply@servicepros.local";
    }

    @Getter @Setter
    public static class Optimizer {
        private double avgSpeedKmh = 32;
        /** Multiplier applied to straight-line distance when Google Routes is unavailable. */
        private double roadFactor = 1.25;
        private int arrivalWindowMinutes = 30;
    }
}
