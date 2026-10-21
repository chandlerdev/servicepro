"use client";

import { Button, LinkButton } from "@/components/ui/Button";
import type { DataColumn } from "@/components/ui/DataTable";
import { StatusBadge } from "@/components/ui/StatusBadge";
import { formatDate, formatMoney, formatTime } from "@/lib/format";
import { mapsPointUrl } from "@/lib/utils";
import type { Appointment } from "@/types/api";

export function AllocatedTime({ a }: { a: Pick<Appointment, "plannedArrival" | "allocated" | "status"> }) {
  if (a.status === "CANCELLED") return <span className="text-xs text-muted">—</span>;
  if (!a.plannedArrival) return <span className="text-xs font-normal text-muted">not allocated yet</span>;
  if (!a.allocated)
    return (
      <span className="text-xs font-normal text-muted" title="The plan changed since this time was computed">
        <s>{formatTime(a.plannedArrival)}</s> · needs re-optimize
      </span>
    );
  return <span className="eta">{formatTime(a.plannedArrival)}</span>;
}

export interface AppointmentActions {
  onSms?: (a: Appointment) => void;
  onEdit?: (a: Appointment) => void;
  onDelete?: (a: Appointment) => void;
  smsBusyId?: number | null;
}

export function appointmentColumns(actions?: AppointmentActions): DataColumn<Appointment>[] {
  const cols: DataColumn<Appointment>[] = [
    {
      id: "date",
      header: "Date",
      cell: ({ row }) => <span className="whitespace-nowrap">{formatDate(row.original.date)}</span>,
    },
    {
      id: "customer",
      header: "Customer",
      cell: ({ row }) => (
        <>
          <b>{row.original.customerName}</b>{" "}
          <span className="text-xs text-muted">{row.original.city}</span>
        </>
      ),
    },
    {
      id: "task",
      header: "Task",
      cell: ({ row }) => (
        <>
          {row.original.taskName} <span className="text-xs text-muted">{formatMoney(row.original.taskRate)}</span>
        </>
      ),
    },
    { id: "route", header: "Route", cell: ({ row }) => row.original.routeName || "—" },
    {
      id: "allocated",
      header: "Allocated time",
      cell: ({ row }) => <AllocatedTime a={row.original} />,
    },
    { id: "status", header: "Status", cell: ({ row }) => <StatusBadge status={row.original.status} /> },
  ];
  if (actions) {
    cols.push({
      id: "actions",
      header: "Actions",
      meta: { className: "text-right whitespace-nowrap" },
      cell: ({ row }) => {
        const a = row.original;
        return (
          <div className="inline-flex flex-wrap justify-end gap-1">
            <LinkButton href={mapsPointUrl(a.customerLat, a.customerLng)} title="Open in Google Maps">
              📍
            </LinkButton>
            {actions.onSms && (
              <Button
                variant="secondary"
                size="sm"
                title="Text schedule to customer"
                loading={actions.smsBusyId === a.id}
                disabled={a.status === "CANCELLED" || a.status === "COMPLETED"}
                onClick={() => actions.onSms?.(a)}
              >
                📨 SMS
              </Button>
            )}
            {actions.onEdit && (
              <Button variant="secondary" size="sm" onClick={() => actions.onEdit?.(a)}>
                Edit
              </Button>
            )}
            {actions.onDelete && (
              <Button variant="danger" size="sm" onClick={() => actions.onDelete?.(a)}>
                Del
              </Button>
            )}
          </div>
        );
      },
    });
  }
  return cols;
}
