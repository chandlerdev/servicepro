"use client";

import Link from "next/link";
import { TechStopCard } from "@/components/tech/TechStopCard";
import { Card } from "@/components/ui/Card";
import { ErrorState } from "@/components/ui/EmptyState";
import { Input } from "@/components/ui/FormField";
import { PageSpinner, Spinner } from "@/components/ui/Spinner";
import { useApi } from "@/hooks/useApi";
import { useInterval } from "@/hooks/useInterval";
import { formatDate } from "@/lib/format";
import { techApi } from "@/lib/services";
import { useTechDate } from "@/store/techDate";

export default function MySchedulePage() {
  const { date, setDate } = useTechDate();
  const stops = useApi(() => techApi.stops(date), [date]);
  useInterval(() => stops.reload({ silent: true }), 60_000);

  const data = stops.data;
  const plans = (data?.routes ?? []).filter((p) => p.stops.some((s) => s.status !== "CANCELLED"));

  return (
    <>
      <Card
        title="🗓️ My stops"
        actions={
          <Link href="/tech/map" className="btn btn-sec btn-sm">
            🗺️ Open map
          </Link>
        }
      >
        <div className="flex flex-wrap items-center gap-2">
          <Input
            type="date"
            className="w-[170px]"
            value={date}
            onChange={(e) => setDate(e.target.value)}
            aria-label="Date"
          />
          <span className="inline-flex items-center gap-1.5 text-xs text-muted">
            {stops.loading && data ? <Spinner size={12} /> : <span className="size-2 rounded-full bg-ok" />}
            {data ? `${data.technician.name} · ${formatDate(date)}` : formatDate(date)}
          </span>
        </div>
        <div className="demo-note">
          “Delay” texts every remaining customer on your route a new ETA. “Complete” closes the job and texts the customer
          Google &amp; Facebook review links.
        </div>
      </Card>

      {!data && stops.loading && <PageSpinner />}
      {!data && !stops.loading && stops.error && (
        <div className="card">
          <ErrorState message={stops.error} onRetry={() => stops.reload()} />
        </div>
      )}
      {data && plans.length === 0 && (
        <div className="card">
          <div className="empty">No stops for you on {formatDate(date)}.</div>
        </div>
      )}
      {plans.map((p) => (
        <TechStopCard key={p.routeId} plan={p} onChanged={() => stops.reload({ silent: true })} />
      ))}
    </>
  );
}
