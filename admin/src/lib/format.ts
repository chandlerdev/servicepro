/** Formatting helpers. Business timezone is America/Chicago. */
export const BUSINESS_TZ = "America/Chicago";

/** "HH:mm" (24h) → "8:30 AM". Returns "—" for null/invalid. */
export function formatTime(hhmm: string | null | undefined): string {
  if (!hhmm) return "—";
  const m = /^(\d{1,2}):(\d{2})/.exec(hhmm);
  if (!m) return hhmm;
  let h = Number(m[1]);
  const min = m[2];
  const ap = h >= 12 ? "PM" : "AM";
  h = h % 12 || 12;
  return `${h}:${min} ${ap}`;
}

/** Date-only "YYYY-MM-DD" → Date anchored at noon UTC (same calendar day in Chicago). */
function dateOnly(d: string): Date {
  return new Date(`${d.slice(0, 10)}T12:00:00Z`);
}

const shortDateFmt = new Intl.DateTimeFormat("en-US", {
  timeZone: BUSINESS_TZ,
  weekday: "short",
  month: "short",
  day: "numeric",
});
const dateTimeFmt = new Intl.DateTimeFormat("en-US", {
  timeZone: BUSINESS_TZ,
  month: "short",
  day: "numeric",
  hour: "numeric",
  minute: "2-digit",
});
const isoDayFmt = new Intl.DateTimeFormat("en-CA", {
  timeZone: BUSINESS_TZ,
  year: "numeric",
  month: "2-digit",
  day: "2-digit",
});

/** "2026-10-01" → "Thu, Oct 1" */
export function formatDate(d: string | null | undefined): string {
  if (!d) return "—";
  return shortDateFmt.format(dateOnly(d));
}

/** ISO timestamp → "Oct 1, 1:30 PM" (Chicago time). */
export function formatDateTime(iso: string | null | undefined): string {
  if (!iso) return "—";
  const d = new Date(iso);
  if (Number.isNaN(d.getTime())) return iso;
  return dateTimeFmt.format(d);
}

/** Today's date (YYYY-MM-DD) in the business timezone. */
export function todayISO(): string {
  return isoDayFmt.format(new Date());
}

/** Add days to a YYYY-MM-DD date string. */
export function addDays(d: string, days: number): string {
  const dt = dateOnly(d);
  dt.setUTCDate(dt.getUTCDate() + days);
  return dt.toISOString().slice(0, 10);
}

const moneyFmt = new Intl.NumberFormat("en-US", { style: "currency", currency: "USD" });

/** 129.9 → "$129.90" */
export function formatMoney(n: number | null | undefined): string {
  return moneyFmt.format(n ?? 0);
}
