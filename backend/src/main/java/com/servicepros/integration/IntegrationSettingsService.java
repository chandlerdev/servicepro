package com.servicepros.integration;

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;

import com.servicepros.common.ApiException;
import com.servicepros.config.AppProperties;
import com.servicepros.integration.IntegrationDtos.IntegrationsView;
import com.servicepros.integration.IntegrationDtos.MailRequest;
import com.servicepros.integration.IntegrationDtos.MailView;
import com.servicepros.integration.IntegrationDtos.MapsRequest;
import com.servicepros.integration.IntegrationDtos.MapsView;
import com.servicepros.integration.IntegrationDtos.SmsRequest;
import com.servicepros.integration.IntegrationDtos.SmsView;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.JavaMailSenderImpl;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

/**
 * The integration keys the Super Admin edits on Settings (SMS, Google Maps, Email), kept in one database row.
 * <p>
 * On the first start the row is filled from the server's env file (TWILIO_*, GOOGLE_*, MAIL_*); from then on the
 * row is what is used. Every save is applied immediately — the SMS, Maps and Email code reads the shared
 * {@link AppProperties} objects updated here, and the mail sender is rebuilt — so no backend restart is needed.
 */
@Slf4j
@Service
@Order(-5)
public class IntegrationSettingsService implements ApplicationRunner {

    private final IntegrationSettingsRepository repo;
    private final AppProperties props;

    // env values, used only to fill the row the first time
    private final String envMailHost;
    private final Integer envMailPort;
    private final String envMailUsername;
    private final String envMailPassword;

    private volatile JavaMailSender mailSender;
    private volatile boolean mailConfigured;

    public IntegrationSettingsService(IntegrationSettingsRepository repo, AppProperties props,
                                      @Value("${spring.mail.host:}") String mailHost,
                                      @Value("${spring.mail.port:587}") Integer mailPort,
                                      @Value("${spring.mail.username:}") String mailUsername,
                                      @Value("${spring.mail.password:}") String mailPassword) {
        this.repo = repo;
        this.props = props;
        this.envMailHost = mailHost;
        this.envMailPort = mailPort;
        this.envMailUsername = mailUsername;
        this.envMailPassword = mailPassword;
        applyMail(mailHost, mailPort, mailUsername, mailPassword); // until the row is loaded
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        IntegrationSettings s = repo.findFirstByOrderByIdAsc().orElseGet(() -> {
            log.info("Integration settings: copying the initial values from the env file into the database");
            return repo.save(fromEnv());
        });
        apply(s);
    }

    // ---------------------------------------------------------------- read

    @Transactional(readOnly = true)
    public IntegrationsView view() {
        IntegrationSettings s = row();
        return new IntegrationsView(
                new SmsView(props.getTwilio().isConfigured(), s.getTwilioAccountSid(),
                        StringUtils.hasText(s.getTwilioAuthToken()), hint(s.getTwilioAuthToken()),
                        s.getTwilioFromNumber(), s.getTwilioWebhookUrl()),
                new MapsView(props.getGoogle().isConfigured(), StringUtils.hasText(s.getGoogleMapsApiKey()),
                        hint(s.getGoogleMapsApiKey()), s.getGooglePlacesRegions()),
                new MailView(mailConfigured, s.getMailHost(), s.getMailPort(), s.getMailUsername(),
                        StringUtils.hasText(s.getMailPassword()), s.getMailFrom()));
    }

    public boolean isMailConfigured() {
        return mailConfigured;
    }

    public JavaMailSender mailSender() {
        return mailSender;
    }

    // ---------------------------------------------------------------- save

    @Transactional
    public IntegrationsView saveSms(SmsRequest r) {
        IntegrationSettings s = row();
        String token = keepOr(r.authToken(), s.getTwilioAuthToken());
        if (!StringUtils.hasText(token)) {
            throw field("authToken", "Auth token is required");
        }
        s.setTwilioAccountSid(r.accountSid().trim());
        s.setTwilioAuthToken(token);
        s.setTwilioFromNumber(r.fromNumber().trim());
        s.setTwilioWebhookUrl(blankToNull(r.webhookUrl()));
        apply(repo.save(s));
        return view();
    }

    @Transactional
    public IntegrationsView saveMaps(MapsRequest r) {
        IntegrationSettings s = row();
        String key = keepOr(r.apiKey(), s.getGoogleMapsApiKey());
        if (!StringUtils.hasText(key)) {
            throw field("apiKey", "API key is required");
        }
        s.setGoogleMapsApiKey(key);
        s.setGooglePlacesRegions(String.join(",", regions(r.regions())));
        apply(repo.save(s));
        return view();
    }

    @Transactional
    public IntegrationsView saveMail(MailRequest r) {
        IntegrationSettings s = row();
        String username = blankToNull(r.username());
        // a new username with no new password would send the old account's password — ask for it again
        boolean sameUser = username != null && username.equalsIgnoreCase(String.valueOf(s.getMailUsername()));
        String password = sameUser ? keepOr(r.password(), s.getMailPassword()) : blankToNull(r.password());
        if (username != null && !StringUtils.hasText(password)) {
            throw field("password", "Password is required for this username");
        }
        s.setMailHost(r.host().trim());
        s.setMailPort(r.port());
        s.setMailUsername(username);
        s.setMailPassword(username == null ? null : password);
        s.setMailFrom(r.from().trim());
        apply(repo.save(s));
        return view();
    }

    /** Disconnect = remove that integration's keys (texts / emails are then only logged, as before setup). */
    @Transactional
    public IntegrationsView disconnect(String key) {
        IntegrationSettings s = row();
        switch (key) {
            case "sms" -> {
                s.setTwilioAccountSid(null);
                s.setTwilioAuthToken(null);
                s.setTwilioFromNumber(null);
                s.setTwilioWebhookUrl(null);
            }
            case "maps" -> s.setGoogleMapsApiKey(null);
            case "mail" -> {
                s.setMailHost(null);
                s.setMailUsername(null);
                s.setMailPassword(null);
            }
            default -> throw new ApiException(HttpStatus.NOT_FOUND, "Unknown integration: " + key);
        }
        apply(repo.save(s));
        return view();
    }

    // ---------------------------------------------------------------- internals

    private IntegrationSettings row() {
        return repo.findFirstByOrderByIdAsc().orElseGet(() -> repo.save(fromEnv()));
    }

    private IntegrationSettings fromEnv() {
        AppProperties.Twilio t = props.getTwilio();
        AppProperties.Google g = props.getGoogle();
        IntegrationSettings s = new IntegrationSettings();
        s.setTwilioAccountSid(blankToNull(t.getAccountSid()));
        s.setTwilioAuthToken(blankToNull(t.getAuthToken()));
        s.setTwilioFromNumber(blankToNull(t.getFromNumber()));
        s.setTwilioWebhookUrl(blankToNull(t.getWebhookUrl()));
        s.setGoogleMapsApiKey(blankToNull(g.getMapsApiKey()));
        s.setGooglePlacesRegions(g.getPlacesRegions() == null ? "" : String.join(",", g.getPlacesRegions()));
        s.setMailHost(blankToNull(envMailHost));
        s.setMailPort(envMailPort);
        s.setMailUsername(blankToNull(envMailUsername));
        s.setMailPassword(blankToNull(envMailPassword));
        s.setMailFrom(blankToNull(props.getMail().getFrom()));
        return s;
    }

    /** Push the row into the objects the SMS / Maps / Email code reads. */
    private void apply(IntegrationSettings s) {
        AppProperties.Twilio t = props.getTwilio();
        t.setAccountSid(s.getTwilioAccountSid());
        t.setAuthToken(s.getTwilioAuthToken());
        t.setFromNumber(s.getTwilioFromNumber());
        t.setWebhookUrl(s.getTwilioWebhookUrl());

        AppProperties.Google g = props.getGoogle();
        g.setMapsApiKey(s.getGoogleMapsApiKey());
        g.setPlacesRegions(regions(s.getGooglePlacesRegions()));

        if (StringUtils.hasText(s.getMailFrom())) props.getMail().setFrom(s.getMailFrom());
        applyMail(s.getMailHost(), s.getMailPort(), s.getMailUsername(), s.getMailPassword());
    }

    private void applyMail(String host, Integer port, String username, String password) {
        if (!StringUtils.hasText(host)) {
            mailSender = null;
            mailConfigured = false;
            return;
        }
        int p = port == null ? 587 : port;
        JavaMailSenderImpl sender = new JavaMailSenderImpl();
        sender.setHost(host.trim());
        sender.setPort(p);
        sender.setDefaultEncoding("UTF-8");
        Properties mp = sender.getJavaMailProperties();
        mp.put("mail.smtp.connectiontimeout", "10000");
        mp.put("mail.smtp.timeout", "15000");
        mp.put("mail.smtp.writetimeout", "15000");
        if (StringUtils.hasText(username)) {
            sender.setUsername(username.trim());
            sender.setPassword(password);
            mp.put("mail.smtp.auth", "true");
        }
        if (p == 465) {
            mp.put("mail.smtp.ssl.enable", "true"); // implicit TLS
        } else {
            mp.put("mail.smtp.starttls.enable", "true");
        }
        mailSender = sender;
        mailConfigured = true;
    }

    private static List<String> regions(String text) {
        if (!StringUtils.hasText(text)) return new java.util.ArrayList<>();
        return Arrays.stream(text.split(","))
                .map(String::trim).filter(StringUtils::hasText).map(String::toLowerCase).distinct()
                .collect(java.util.stream.Collectors.toCollection(java.util.ArrayList::new));
    }

    private static String keepOr(String typed, String current) {
        return StringUtils.hasText(typed) ? typed.trim() : current;
    }

    private static String blankToNull(String v) {
        return StringUtils.hasText(v) ? v.trim() : null;
    }

    /** "••••1234" */
    private static String hint(String secret) {
        if (!StringUtils.hasText(secret)) return null;
        String s = secret.trim();
        return s.length() <= 4 ? "••••" : "••••" + s.substring(s.length() - 4);
    }

    private static ApiException field(String name, String msg) {
        Map<String, String> m = new HashMap<>();
        m.put(name, msg);
        return new ApiException(HttpStatus.BAD_REQUEST, msg, m);
    }
}
