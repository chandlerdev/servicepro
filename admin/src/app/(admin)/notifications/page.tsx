"use client";

import { useState } from "react";
import toast from "react-hot-toast";
import { NotificationList } from "@/components/NotificationList";
import { Button } from "@/components/ui/Button";
import { Card } from "@/components/ui/Card";
import { ConfirmDialog } from "@/components/ui/ConfirmDialog";
import { ErrorState } from "@/components/ui/EmptyState";
import { Pagination } from "@/components/ui/Pagination";
import { Select } from "@/components/ui/Select";
import { PageSpinner } from "@/components/ui/Spinner";
import { useApi } from "@/hooks/useApi";
import { useInterval } from "@/hooks/useInterval";
import { handleApiError } from "@/lib/api";
import { NOTIFICATION_TYPE_META } from "@/lib/enums";
import { notificationsApi } from "@/lib/services";
import { NOTIFICATION_TYPES, type NotificationType } from "@/types/api";

const PAGE_SIZE = 25;

export default function NotificationsPage() {
  const [type, setType] = useState<NotificationType | "">("");
  const [page, setPage] = useState(0);
  const [confirmClear, setConfirmClear] = useState(false);
  const list = useApi(() => notificationsApi.page({ type: type || undefined, page, size: PAGE_SIZE }), [type, page]);
  useInterval(() => list.reload({ silent: true }), page === 0 ? 30_000 : null);

  const data = list.data;

  return (
    <>
      <Card
        title={
          <>
            📨 Message Log{" "}
            <span className="text-xs font-normal text-muted">
              SMS &amp; email · “Simulated” = provider not configured, message only logged
            </span>
          </>
        }
        actions={
          <div className="flex flex-wrap gap-2">
            <Select
              className="w-[200px]"
              value={type}
              onChange={(e) => {
                setType(e.target.value as NotificationType | "");
                setPage(0);
              }}
              placeholder="All types"
              options={NOTIFICATION_TYPES.map((t) => ({
                value: t,
                label: `${NOTIFICATION_TYPE_META[t].icon} ${NOTIFICATION_TYPE_META[t].label}`,
              }))}
              aria-label="Type"
            />
            {!!data?.totalElements && (
              <Button variant="secondary" onClick={() => setConfirmClear(true)}>
                Clear all
              </Button>
            )}
          </div>
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

      <ConfirmDialog
        open={confirmClear}
        title="Clear notification log"
        confirmLabel="Clear all"
        message={<p>Delete every logged SMS and email? This can&apos;t be undone.</p>}
        onClose={() => setConfirmClear(false)}
        onConfirm={async () => {
          try {
            await notificationsApi.clear();
            toast.success("Notification log cleared");
            setConfirmClear(false);
            setPage(0);
            list.reload();
          } catch (err) {
            handleApiError(err);
          }
        }}
      />
    </>
  );
}
