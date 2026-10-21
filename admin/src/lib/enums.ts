import type { AppointmentStatus, NotificationStatus, NotificationType } from "@/types/api";

/** Explicit label overrides where simple title-casing is wrong. */
const LABEL_OVERRIDES: Record<string, string> = {
  SMS: "SMS",
  ENROUTE: "On the way",
};

/**
 * Enum value → UI label: replace "_" with space and title-case.
 * e.g. EN_ROUTE → "En Route".
 */
export function enumLabel(value: string | null | undefined): string {
  if (!value) return "—";
  if (LABEL_OVERRIDES[value]) return LABEL_OVERRIDES[value];
  return value
    .toLowerCase()
    .split("_")
    .map((w) => (w ? w[0].toUpperCase() + w.slice(1) : w))
    .join(" ");
}

export type BadgeTone = "blue" | "teal" | "purple" | "red" | "green" | "grey" | "amber";

export const APPOINTMENT_STATUS_TONE: Record<AppointmentStatus, BadgeTone> = {
  SCHEDULED: "blue",
  CONFIRMED: "teal",
  EN_ROUTE: "purple",
  DELAYED: "red",
  COMPLETED: "green",
  CANCELLED: "grey",
};

export const NOTIFICATION_STATUS_TONE: Record<NotificationStatus, BadgeTone> = {
  SENT: "green",
  FAILED: "red",
  SIMULATED: "grey",
};

/** Icons & labels from the prototype's NTYPE table. */
export const NOTIFICATION_TYPE_META: Record<NotificationType, { icon: string; label: string }> = {
  BOOKING: { icon: "📅", label: "Booking confirmation" },
  SCHEDULE: { icon: "🕗", label: "Day-before schedule" },
  UPDATE: { icon: "🔁", label: "Schedule update" },
  DELAY: { icon: "⏳", label: "Delay alert" },
  ENROUTE: { icon: "🚚", label: "On the way" },
  REVIEW: { icon: "⭐", label: "Review request" },
};
