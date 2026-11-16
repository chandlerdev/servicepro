package com.servicepros.notification;

import java.time.Instant;
import java.time.LocalDate;

import com.servicepros.common.ApiException;
import com.servicepros.common.TimeService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * The date window of a notification list. With no dates it is yesterday and today (business
 * timezone), which is all the technician app ever asks for; the admin page can widen it with
 * {@code from} / {@code to}.
 */
@Component
@RequiredArgsConstructor
public class NotificationRange {

    /** {@code from} is inclusive, {@code toExclusive} is the start of the day after {@code to}. */
    public record Bounds(Instant from, Instant toExclusive) {
    }

    private final TimeService time;

    public Bounds resolve(LocalDate from, LocalDate to) {
        LocalDate today = time.today();
        LocalDate yesterday = today.minusDays(1);
        LocalDate start = from != null ? from : (to != null && to.isBefore(yesterday) ? to : yesterday);
        LocalDate end = to != null ? to : (start.isAfter(today) ? start : today);
        if (start.isAfter(end)) {
            throw ApiException.badRequest("'from' must not be after 'to'");
        }
        return new Bounds(
                start.atStartOfDay(time.zone()).toInstant(),
                end.plusDays(1).atStartOfDay(time.zone()).toInstant());
    }
}
