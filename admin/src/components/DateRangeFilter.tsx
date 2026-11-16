"use client";

import { Input } from "@/components/ui/FormField";

/** YYYY-MM-DD for a moment, in the business timezone (the backend's app.timezone). */
function ymd(d: Date) {
  return new Intl.DateTimeFormat("en-CA", { timeZone: "America/Chicago" }).format(d);
}

/** The default notification window: yesterday through today. */
export function defaultRange() {
  const now = Date.now();
  return { from: ymd(new Date(now - 24 * 3600 * 1000)), to: ymd(new Date(now)) };
}

/** "From" and "to" date boxes; an emptied box falls back to the default window on the server. */
export function DateRangeFilter({
  from,
  to,
  onChange,
}: {
  from: string;
  to: string;
  onChange: (next: { from: string; to: string }) => void;
}) {
  return (
    <div className="flex flex-wrap items-center gap-2 text-[12.5px] text-muted">
      <label className="flex items-center gap-1.5">
        From
        <Input
          type="date"
          className="w-[150px]"
          value={from}
          max={to || undefined}
          onChange={(e) => onChange({ from: e.target.value, to })}
          aria-label="From date"
        />
      </label>
      <label className="flex items-center gap-1.5">
        To
        <Input
          type="date"
          className="w-[150px]"
          value={to}
          min={from || undefined}
          onChange={(e) => onChange({ from, to: e.target.value })}
          aria-label="To date"
        />
      </label>
    </div>
  );
}
