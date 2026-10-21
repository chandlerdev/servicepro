"use client";

import { useState } from "react";
import toast from "react-hot-toast";
import { RouteStopCard } from "@/components/schedule/RouteStopCard";
import { ScheduleMap } from "@/components/schedule/ScheduleMap";
import { routeColor } from "@/components/schedule/colors";
import { Button } from "@/components/ui/Button";
import { Card } from "@/components/ui/Card";
import { ErrorState } from "@/components/ui/EmptyState";
import { Input } from "@/components/ui/FormField";
import { Select } from "@/components/ui/Select";
import { PageSpinner, Spinner } from "@/components/ui/Spinner";
import { useApi } from "@/hooks/useApi";
import { useInterval } from "@/hooks/useInterval";
import { handleApiError } from "@/lib/api";
import { formatDate, todayISO } from "@/lib/format";
import { routesApi, scheduleApi } from "@/lib/services";

const POLL_MS = 20_000;

export default function SchedulePage() {
  const [date, setDate] = useState(() => todayISO());
  const [routeId, setRouteId] = useState("");
  const [busy, setBusy] = useState<"optimize" | "allocate" | null>(null);

  const routes = useApi(() => routesApi.list(), []);
  const schedule = useApi(
    () => scheduleApi.get({ date, routeId: routeId ? Number(routeId) : undefined }),
    [date, routeId],
  );

  // Live updates: poll so technician status changes (en route / delayed / completed) appear.
  useInterval(
    () => {
      if (!busy) schedule.reload({ silent: true });
    },
    date ? POLL_MS : null,
  );

  const req = { date, routeId: routeId ? Number(routeId) : undefined };
  const data = schedule.data;
  const plans = data?.routes ?? [];
  const visiblePlans = plans.filter((p) => p.stops.some((s) => s.status !== "CANCELLED"));
  const stale = visiblePlans.some((p) => p.stale || !p.optimized);

  const optimize = async () => {
    setBusy("optimize");
    try {
      const s = await scheduleApi.optimize(req);
      schedule.setData(() => s);
      toast.success("⚙️ Routes re-optimized — ETAs recalculated");
    } catch (err) {
      handleApiError(err);
    } finally {
      setBusy(null);
    }
  };

  const allocate = async () => {
    setBusy("allocate");
    try {
      const res = await scheduleApi.allocateNotify(req);
      schedule.setData(() => res.schedule);
      toast.success(
        res.notified
          ? `📨 Optimized schedule sent to ${res.notified} customer(s)`
          : "Nothing to notify for this date",
      );
    } catch (err) {
      handleApiError(err);
    } finally {
      setBusy(null);
    }
  };

  return (
    <>
      <Card
        title="🗺️ Day plan"
        actions={
          <div className="flex flex-wrap gap-2">
            <Button variant="secondary" onClick={optimize} loading={busy === "optimize"} disabled={!!busy || !visiblePlans.length}>
              ⚙️ Re-optimize only
            </Button>
            <Button onClick={allocate} loading={busy === "allocate"} disabled={!!busy || !visiblePlans.length}>
              ⚡ Allocate schedule &amp; notify ALL customers
            </Button>
          </div>
        }
      >
        <div className="flex flex-wrap items-center gap-2">
          <Input
            type="date"
            className="w-[170px]"
            value={date}
            onChange={(e) => setDate(e.target.value || todayISO())}
            aria-label="Plan date"
          />
          <Select
            className="w-[200px]"
            value={routeId}
            onChange={(e) => setRouteId(e.target.value)}
            placeholder="All routes"
            options={(routes.data ?? []).map((r) => ({ value: r.id, label: r.name }))}
            aria-label="Route"
          />
          <span className="inline-flex items-center gap-1.5 text-xs text-muted">
            {schedule.loading && data ? <Spinner size={12} /> : <span className="size-2 rounded-full bg-ok" />}
            Live · refreshes every 20 s
          </span>
        </div>
        <div className="demo-note">
          Run <b>Allocate &amp; notify</b> the evening before: it collects every appointment for the date, optimizes each
          route&apos;s stop order, computes exact arrival times, and texts every customer their time — review links go out
          automatically after each job is completed.
        </div>
      </Card>

      {stale && (
        <div className="banner">
          ⚠️ Appointments or addresses changed since the last optimization. Re-optimize to recalculate the stop order and
          ETAs, then notify customers.
          <Button size="sm" className="ml-auto" onClick={optimize} loading={busy === "optimize"} disabled={!!busy}>
            Re-optimize now
          </Button>
        </div>
      )}

      <div className="grid grid-cols-1 gap-4 xl:grid-cols-[minmax(380px,1fr)_minmax(0,1.25fr)]">
        <div className="min-w-0">
          {!data && schedule.loading && <PageSpinner />}
          {!data && !schedule.loading && schedule.error && (
            <div className="card">
              <ErrorState message={schedule.error} onRetry={() => schedule.reload()} />
            </div>
          )}
          {data && visiblePlans.length === 0 && (
            <div className="card">
              <div className="empty">No appointments on {formatDate(date)}.</div>
            </div>
          )}
          {data &&
            plans.map((p, i) =>
              p.stops.some((s) => s.status !== "CANCELLED") ? (
                <RouteStopCard key={p.routeId} plan={p} color={routeColor(i)} />
              ) : null,
            )}
        </div>
        <div className="min-w-0">
          <div className="card p-2.5! xl:sticky xl:top-0">
            <ScheduleMap schedule={data} />
            <div className="demo-note px-1">
              Pins are numbered in visit order; lines show each route&apos;s driving sequence from the depot ⌂ (dashed =
              not yet optimized / changed).
            </div>
          </div>
        </div>
      </div>
    </>
  );
}

