"use client";

import { useState } from "react";
import { Button, LinkButton } from "@/components/ui/Button";
import { Badge, StatusBadge } from "@/components/ui/StatusBadge";
import { formatTime } from "@/lib/format";
import { cn, mapsDirectionsUrl } from "@/lib/utils";
import type { RoutePlan, Stop } from "@/types/api";
import { CompleteDialog, DelayDialog, sendOnMyWay } from "./StopActionDialogs";

type Action = { kind: "delay"; stop: Stop } | { kind: "complete"; stop: Stop };

/**
 * One route's stops for the signed-in technician, with the prototype's actions:
 * Directions · On my way · Delay (texts every remaining customer) · Complete (review request).
 */
export function TechStopCard({ plan, onChanged }: { plan: RoutePlan; onChanged: () => void }) {
  const [busyId, setBusyId] = useState<number | null>(null);
  const [action, setAction] = useState<Action | null>(null);
  const stops = plan.stops.filter((s) => s.status !== "CANCELLED");
  const fresh = plan.optimized && !plan.stale;
  if (!stops.length) return null;

  const onMyWay = async (s: Stop) => {
    setBusyId(s.appointmentId);
    if (await sendOnMyWay(s)) onChanged();
    setBusyId(null);
  };

  return (
    <section className="card">
      <div className="cardhead">
        <h2 className="card-title">🚚 {plan.routeName}</h2>
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
        {plan.vehicleName} · {plan.technicians.map((t) => t.name).join(", ")}
      </div>

      {stops.map((s, i) => {
        const cls = s.status === "COMPLETED" ? "done" : s.status === "DELAYED" ? "late" : "";
        const open = s.status !== "COMPLETED" && s.status !== "CANCELLED";
        const busy = busyId === s.appointmentId;
        return (
          <div key={s.appointmentId} className="stopline flex-wrap">
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
            <div className="flex w-full flex-wrap justify-end gap-1.5 sm:w-auto">
              <LinkButton href={mapsDirectionsUrl(s.lat, s.lng)} title="Turn-by-turn directions in Google Maps">
                🧭 Directions
              </LinkButton>
              {(s.status === "SCHEDULED" || s.status === "CONFIRMED") && (
                <Button size="sm" loading={busy} disabled={busyId != null} onClick={() => onMyWay(s)}>
                  🚚 On my way
                </Button>
              )}
              {open && (
                <>
                  <Button
                    size="sm"
                    variant="warn"
                    disabled={busyId != null}
                    onClick={() => setAction({ kind: "delay", stop: s })}
                  >
                    ⏳ Delay
                  </Button>
                  <Button
                    size="sm"
                    variant="ok"
                    disabled={busyId != null}
                    onClick={() => setAction({ kind: "complete", stop: s })}
                  >
                    ✔ Complete
                  </Button>
                </>
              )}
            </div>
          </div>
        );
      })}

      {action?.kind === "delay" && (
        <DelayDialog stop={action.stop} routeName={plan.routeName} onClose={() => setAction(null)} onDone={onChanged} />
      )}
      {action?.kind === "complete" && (
        <CompleteDialog stop={action.stop} onClose={() => setAction(null)} onDone={onChanged} />
      )}
    </section>
  );
}
