package com.servicepros.webhook;

import java.util.List;
import java.util.Locale;

import com.servicepros.appointment.Appointment;
import com.servicepros.appointment.AppointmentRepository;
import com.servicepros.appointment.AppointmentStatus;
import com.servicepros.common.Phones;
import com.servicepros.common.TimeService;
import com.servicepros.settings.CompanySettings;
import com.servicepros.settings.SettingsService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Customer SMS replies to the schedule text: "C" confirms, "R" asks to reschedule. */
@Service
@RequiredArgsConstructor
public class InboundSmsService {

    private static final List<AppointmentStatus> OPEN = List.of(AppointmentStatus.SCHEDULED,
            AppointmentStatus.CONFIRMED, AppointmentStatus.EN_ROUTE, AppointmentStatus.DELAYED);

    private final AppointmentRepository appointments;
    private final SettingsService settingsService;
    private final TimeService time;

    /** @return the reply text to send back, or null for no reply */
    @Transactional
    public String handle(String from, String body) {
        String text = body == null ? "" : body.trim();
        String cmd = text.toUpperCase(Locale.ROOT);
        // No user is logged in here, so this runs unfiltered across all admins.
        List<Appointment> upcoming = appointments.findUpcomingByPhone(Phones.last10(from), time.today(), OPEN);

        if (cmd.equals("STOP") || cmd.equals("UNSUBSCRIBE") || cmd.equals("START") || cmd.equals("UNSTOP")) {
            return null; // opt-out / opt-in keywords are handled by Twilio itself
        }
        if (upcoming.isEmpty()) {
            return "Thanks for your message! A team member will get back to you shortly.";
        }
        Appointment next = upcoming.getFirst();
        CompanySettings s = settingsService.getFor(next.getOwnerId());
        String stamp = time.today() + " " + TimeService.hhmm(time.nowTime());
        if (cmd.equals("C") || cmd.equals("YES") || cmd.equals("CONFIRM")) {
            if (next.getStatus() == AppointmentStatus.SCHEDULED) next.setStatus(AppointmentStatus.CONFIRMED);
            appendNote(next, "[" + stamp + "] Customer confirmed by SMS");
            return "Thanks! Your " + next.getTask().getName() + " on " + TimeService.friendlyDate(next.getDate())
                    + " is confirmed. — " + s.displayName();
        }
        if (cmd.equals("R") || cmd.startsWith("RESCHEDULE")) {
            appendNote(next, "[" + stamp + "] Customer asked to RESCHEDULE by SMS");
            return "No problem — our office will call you to pick a new date for your " + next.getTask().getName()
                    + ". — " + s.displayName();
        }
        appendNote(next, "[" + stamp + "] SMS from customer: " + text);
        return "Thanks, we've passed your message to our team. Reply C to confirm or R to reschedule.";
    }

    private static void appendNote(Appointment a, String line) {
        String merged = (a.getNotes() == null || a.getNotes().isBlank() ? "" : a.getNotes() + "\n") + line;
        a.setNotes(merged.length() > 1000 ? merged.substring(merged.length() - 1000) : merged);
    }
}
