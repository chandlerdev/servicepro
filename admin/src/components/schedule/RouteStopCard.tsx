"use client";

import { LinkButton } from "@/components/ui/Button";
import { Badge, StatusBadge } from "@/components/ui/StatusBadge";
import { formatDateTime, formatTime } from "@/lib/format";
import { cn } from "@/lib/utils";
import type { RoutePlan } from "@/types/api";

/** One route's stops as a numbered list in visit order (the prototype's `stopListCard`). */
export function RouteStopCard({ plan, color }: { plan: RoutePlan; color: string }) {
  const stops = plan.stops.filter((s) => s.status !== "CANCELLED");
  const fresh = plan.optimized && !plan.stale;
  if (!stops.length) return null;

  return (
    <section className="card">
      <div className="cardhead">
        <h2 className="card-title">
          <span className="inline-block size-3 rounded-full" style={{ background: color }} aria-hidden />
          🚚 {plan.routeName}
        </h2>
        <div className="flex-1" />
        {fresh ? (
          <span className="text-xs text-muted">
            {plan.totalKm != null ? `${plan.totalKm.toFixed(1)} km` : ""}
            {plan.endTime ? ` · done ~${formatTime(plan.endTime)}` : ""}
          </span>
        ) : (
          <Badge tone="amber">Not optimized</Badge>
        )}
        {plan.googleMapsUrls.map((url, i) => (
          <LinkButton key={url} href={url} title="Open the whole route in Google Maps">
            🗺️ {plan.googleMapsUrls.length > 1 ? `Part ${i + 1}/${plan.googleMapsUrls.length}` : "Google Maps"}
          </LinkButton>
        ))}
      </div>
      <div className="-mt-1.5 mb-2 text-xs text-muted">
        {plan.vehicleName} · {plan.technicians.map((t) => t.name).join(", ") || "no technicians"}
        {plan.notifiedAt && <> · 📨 notified {formatDateTime(plan.notifiedAt)}</>}
      </div>
      {stops.map((s, i) => {
        const cls = s.status === "COMPLETED" ? "done" : s.status === "DELAYED" ? "late" : "";
        return (
          <div key={s.appointmentId} className="stopline">
            <div className={cn("stopnum", cls)}>{i + 1}</div>
            <div className="min-w-0 flex-1">
              <b className="block truncate text-[13.5px]">
                {s.customerName} — {s.taskName}
              </b>
              <span className="block truncate text-xs text-muted">
                {s.address}
                {s.notes ? ` · 📝 ${s.notes}` : ""}
              </span>
            </div>
            <StatusBadge status={s.status} />
            <div className="eta min-w-[62px] text-right">
              {fresh && s.plannedArrival ? formatTime(s.plannedArrival) : "—"}
            </div>
          </div>
        );
      })}
    </section>
  );
}
