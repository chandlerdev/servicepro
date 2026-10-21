"use client";

import { ScheduleMap } from "@/components/schedule/ScheduleMap";
import { Card } from "@/components/ui/Card";
import { ErrorState } from "@/components/ui/EmptyState";
import { Input } from "@/components/ui/FormField";
import { useApi } from "@/hooks/useApi";
import { useInterval } from "@/hooks/useInterval";
import { formatDate } from "@/lib/format";
import { techApi } from "@/lib/services";
import { useTechDate } from "@/store/techDate";

export default function RouteMapPage() {
  const { date, setDate } = useTechDate();
  const stops = useApi(() => techApi.stops(date), [date]);
  useInterval(() => stops.reload({ silent: true }), 60_000);
  const plans = stops.data?.routes ?? [];

  return (
    <>
      <Card
        title={`🗺️ My route — ${formatDate(date)}`}
        actions={
          <span className="text-xs text-muted">
            {plans
              .filter((p) => p.optimized && !p.stale && p.totalKm != null)
              .map((p) => `${p.routeName}: ${p.totalKm!.toFixed(1)} km`)
              .join(" · ")}
          </span>
        }
      >
        <Input
          type="date"
          className="w-[170px]"
          value={date}
          onChange={(e) => setDate(e.target.value)}
          aria-label="Date"
        />
      </Card>
      <div className="card p-2.5!">
        {stops.error && !stops.data ? (
          <ErrorState message={stops.error} onRetry={() => stops.reload()} />
        ) : (
          <ScheduleMap schedule={stops.data} />
        )}
        <div className="demo-note px-1">Tap a pin → “Directions” for turn-by-turn navigation to that stop.</div>
      </div>
    </>
  );
}
