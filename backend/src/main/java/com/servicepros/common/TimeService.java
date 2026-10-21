package com.servicepros.common;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.time.temporal.ChronoUnit;

import com.servicepros.config.AppProperties;
import org.springframework.stereotype.Component;

/**
 * All "today"/"now" logic runs in the business timezone (America/Chicago by default), never UTC —
 * the prototype computed "today" in UTC, which broke the evening-before allocation after 7 PM Houston time.
 */
@Component
public class TimeService {

    private static final DateTimeFormatter HH_MM = DateTimeFormatter.ofPattern("HH:mm");
    private static final DateTimeFormatter FRIENDLY_TIME = DateTimeFormatter.ofPattern("h:mm a");
    private static final DateTimeFormatter FRIENDLY_DATE = DateTimeFormatter.ofPattern("EEE, MMM d");

    private final ZoneId zone;

    public TimeService(AppProperties props) {
        this.zone = ZoneId.of(props.getTimezone());
    }

    public ZoneId zone() {
        return zone;
    }

    public LocalDate today() {
        return LocalDate.now(zone);
    }

    public LocalTime nowTime() {
        return LocalTime.now(zone).truncatedTo(ChronoUnit.MINUTES);
    }

    public Instant now() {
        return Instant.now();
    }

    public static String hhmm(LocalTime t) {
        return t == null ? null : t.format(HH_MM);
    }

    public static LocalTime parseHhmm(String s, String field) {
        if (s == null || s.isBlank()) return null;
        try {
            return LocalTime.parse(s.length() > 5 ? s.substring(0, 5) : s, HH_MM);
        } catch (DateTimeParseException e) {
            throw ApiException.badRequest(field + " must be HH:mm");
        }
    }

    public static String friendlyTime(LocalTime t) {
        return t == null ? "" : t.format(FRIENDLY_TIME);
    }

    public static String friendlyDate(LocalDate d) {
        return d == null ? "" : d.format(FRIENDLY_DATE);
    }
}
