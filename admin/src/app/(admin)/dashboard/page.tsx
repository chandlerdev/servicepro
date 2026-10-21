"use client";

import { useMemo } from "react";
import Link from "next/link";
import { appointmentColumns } from "@/components/appointments/appointmentColumns";
import { NotificationList } from "@/components/NotificationList";
import { Card, StatTile } from "@/components/ui/Card";
import { DataTable } from "@/components/ui/DataTable";
import { ErrorState } from "@/components/ui/EmptyState";
import { PageSpinner } from "@/components/ui/Spinner";
import { useApi } from "@/hooks/useApi";
import { useInterval } from "@/hooks/useInterval";
import { formatDate, formatMoney } from "@/lib/format";
import { dashboardApi } from "@/lib/services";

export default function DashboardPage() {
  const { data, error, loading, reload } = useApi(() => dashboardApi.get(), []);
  useInterval(() => reload({ silent: true }), 30_000);
  const columns = useMemo(() => appointmentColumns(), []);

  if (!data) {
    return loading ? <PageSpinner /> : <ErrorState message={error ?? "Failed to load"} onRetry={() => reload()} />;
  }

  return (
    <>
      <div className="statgrid">
        <StatTile value={data.appointmentsToday} label="Appointments today" />
        <StatTile value={data.completedToday} label="Completed" />
        <StatTile value={data.delayedToday} label="Delayed" tone={data.delayedToday ? "danger" : undefined} />
        <StatTile value={data.customers} label="Customers" />
        <StatTile value={formatMoney(data.revenueToday)} label="Revenue completed today" />
      </div>

      <Card
        title={<>📅 Today — {formatDate(data.date)}</>}
        actions={
          <Link href="/schedule" className="btn btn-sec btn-sm">
            Open Schedule &amp; Map
          </Link>
        }
      >
        <DataTable columns={columns} data={data.todays} emptyText="No appointments for this date." />
      </Card>

      <Card title="📨 Latest notifications">
        <NotificationList items={data.latestNotifications} />
        {data.latestNotifications.length > 0 && (
          <div className="pt-2 text-right">
            <Link href="/notifications" className="linklike">
              View all →
            </Link>
          </div>
        )}
      </Card>
    </>
  );
}
