"use client";

import { useState } from "react";
import { DateRangeFilter, defaultRange } from "@/components/DateRangeFilter";
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
  const [range, setRange] = useState(defaultRange);
  const list = useApi(
    () => techApi.notifications({ from: range.from || undefined, to: range.to || undefined, page, size: PAGE_SIZE }),
    [range.from, range.to, page],
  );
  useInterval(() => list.reload({ silent: true }), page === 0 ? 30_000 : null);
  const data = list.data;

  return (
    <Card
      title={<>📨 Messages to my customers</>}
      actions={
        <DateRangeFilter
          from={range.from}
          to={range.to}
          onChange={(r) => {
            setRange(r);
            setPage(0);
          }}
        />
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
