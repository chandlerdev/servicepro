"use client";

import { useState } from "react";
import { NotificationList } from "@/components/NotificationList";
import { Card } from "@/components/ui/Card";
import { ErrorState } from "@/components/ui/EmptyState";
import { Pagination } from "@/components/ui/Pagination";
import { PageSpinner } from "@/components/ui/Spinner";
import { useApi } from "@/hooks/useApi";
import { useInterval } from "@/hooks/useInterval";
import { techApi } from "@/lib/services";

const PAGE_SIZE = 25;

export default function TechNotificationsPage() {
  const [page, setPage] = useState(0);
  const list = useApi(() => techApi.notifications({ page, size: PAGE_SIZE }), [page]);
  useInterval(() => list.reload({ silent: true }), page === 0 ? 30_000 : null);
  const data = list.data;

  return (
    <Card
      title={
        <>
          📨 Messages to my customers{" "}
          <span className="text-xs font-normal text-muted">“Simulated” = SMS provider not configured yet</span>
        </>
      }
    >
      {!data && list.loading && <PageSpinner />}
      {!data && !list.loading && list.error && <ErrorState message={list.error} onRetry={() => list.reload()} />}
      {data && (
        <div className={list.loading ? "opacity-60" : undefined}>
          <NotificationList items={data.content} />
          <Pagination
            page={data.page}
            size={data.size}
            totalElements={data.totalElements}
            totalPages={data.totalPages}
            onPageChange={setPage}
          />
        </div>
      )}
    </Card>
  );
}
