package com.servicepros.notification;

import java.time.LocalTime;
import java.util.stream.Collectors;

import com.servicepros.appointment.Appointment;
import com.servicepros.common.TimeService;
import com.servicepros.settings.CompanySettings;
import com.servicepros.technician.Technician;

/** Customer-facing SMS wording (kept in one place so it is easy to review and translate). */
public final class MessageTemplates {

    private MessageTemplates() {
    }

    public static String booking(Appointment a, CompanySettings s) {
        return "Hi " + a.getCustomer().firstName() + "! Your " + a.getTask().getName() + " is booked for "
                + TimeService.friendlyDate(a.getDate())
                + ". We'll text your exact arrival time the evening before, once the day's schedule is allocated. — "
                + s.displayName();
    }

    /** @param arrival allocated arrival time, or null when the day isn't allocated yet */
    public static String schedule(Appointment a, LocalTime arrival, CompanySettings s, int windowMinutes, boolean update) {
        String window = window(arrival, windowMinutes);
        String vehicle = a.getRoute().getVehicle() != null ? " (" + a.getRoute().getVehicle().getName() + ")" : "";
        String head = "Hi " + a.getCustomer().firstName() + "! " + (update ? "Schedule update: " : "Reminder: ")
                + a.getTask().getName() + " on " + TimeService.friendlyDate(a.getDate());
        String body = window == null
                ? " — exact arrival time will be texted the evening before. Tech: " + techNames(a)
                : ". Your technician " + techNames(a) + vehicle + " will arrive around " + window;
        return head + body + ". Reply C to confirm or R to reschedule. — " + s.displayName();
    }

    public static String enRoute(Appointment a, CompanySettings s) {
        return a.getCustomer().firstName() + ", your " + s.displayName() + " technician is on the way for your "
                + a.getTask().getName() + "! Get ready — see you soon. 🚚";
    }

    public static String delay(Appointment a, CompanySettings s, int minutes, String reason) {
        return "Hi " + a.getCustomer().firstName() + ", we're running about " + minutes + " min behind"
                + (reason == null || reason.isBlank() ? "" : " (" + reason.trim() + ")")
                + ". New arrival estimate for your " + a.getTask().getName() + ": "
                + (a.getPlannedArrival() == null ? "we'll text you shortly" : TimeService.friendlyTime(a.getPlannedArrival()))
                + ". Sorry for the wait! — " + s.displayName();
    }

    public static String review(Appointment a, CompanySettings s) {
        String msg = "Thanks for choosing " + s.displayName() + ", " + a.getCustomer().firstName() + "! Your "
                + a.getTask().getName() + " is complete.";
        boolean google = s.getGoogleReviewUrl() != null && !s.getGoogleReviewUrl().isBlank();
        boolean facebook = s.getFacebookReviewUrl() != null && !s.getFacebookReviewUrl().isBlank();
        if (!google && !facebook) return msg;
        msg += " Loved the service? Rate us:";
        if (google) msg += "\n⭐ Google: " + s.getGoogleReviewUrl();
        if (facebook) msg += "\n👍 Facebook: " + s.getFacebookReviewUrl();
        return msg;
    }

    public static String window(LocalTime arrival, int windowMinutes) {
        if (arrival == null) return null;
        return TimeService.friendlyTime(arrival) + "–" + TimeService.friendlyTime(arrival.plusMinutes(windowMinutes));
    }

    private static String techNames(Appointment a) {
        String names = a.getRoute().getTechnicians().stream().map(Technician::getName).collect(Collectors.joining(" & "));
        return names.isBlank() ? "our technician" : names;
    }
}
