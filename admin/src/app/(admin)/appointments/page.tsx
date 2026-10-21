"use client";

import { useMemo, useState } from "react";
import toast from "react-hot-toast";
import { AppointmentFormModal } from "@/components/appointments/AppointmentFormModal";
import { appointmentColumns } from "@/components/appointments/appointmentColumns";
import { Button } from "@/components/ui/Button";
import { Card } from "@/components/ui/Card";
import { ConfirmDialog } from "@/components/ui/ConfirmDialog";
import { DataTable } from "@/components/ui/DataTable";
import { Input } from "@/components/ui/FormField";
import { useApi } from "@/hooks/useApi";
import { handleApiError } from "@/lib/api";
import { formatDate } from "@/lib/format";
import { appointmentsApi } from "@/lib/services";
import type { Appointment } from "@/types/api";

type ModalState = { mode: "new" } | { mode: "edit"; appt: Appointment } | null;

const PAGE_SIZE = 20;

export default function AppointmentsPage() {
  const [date, setDate] = useState("");
  const [page, setPage] = useState(0);
  const [modal, setModal] = useState<ModalState>(null);
  const [toDelete, setToDelete] = useState<Appointment | null>(null);
  const [smsBusyId, setSmsBusyId] = useState<number | null>(null);

  const list = useApi(
    () => appointmentsApi.page({ date: date || undefined, page, size: PAGE_SIZE }),
    [date, page],
  );

  const columns = useMemo(
    () =>
      appointmentColumns({
        smsBusyId,
        onSms: async (a) => {
          setSmsBusyId(a.id);
          try {
            const n = await appointmentsApi.sendSms(a.id);
            if (n.status === "FAILED") toast.error(`SMS to ${a.customerName} failed: ${n.error ?? "unknown error"}`);
            else
              toast.success(
                `📨 Schedule texted to ${a.customerName}${n.status === "SIMULATED" ? " (simulated)" : ""}`,
              );
            list.reload({ silent: true });
          } catch (err) {
            handleApiError(err);
          } finally {
            setSmsBusyId(null);
          }
        },
        onEdit: (a) => setModal({ mode: "edit", appt: a }),
        onDelete: (a) => setToDelete(a),
      }),
    // list.reload is stable
    // eslint-disable-next-line react-hooks/exhaustive-deps
    [smsBusyId],
  );

  return (
    <>
      <Card
        title={
          <span className="flex flex-wrap items-center gap-2">
            📅 Appointments
            <Input
              type="date"
              className="w-[170px] font-normal"
              value={date}
              onChange={(e) => {
                setDate(e.target.value);
                setPage(0);
              }}
              aria-label="Filter by date"
            />
            {date && (
              <Button
                variant="secondary"
                size="sm"
                onClick={() => {
                  setDate("");
                  setPage(0);
                }}
              >
                Clear filter
              </Button>
            )}
          </span>
        }
        actions={<Button onClick={() => setModal({ mode: "new" })}>＋ Book Appointment</Button>}
      >
        <DataTable
          columns={columns}
          data={list.data?.content}
          loading={list.loading}
          error={list.error}
          onRetry={() => list.reload()}
          page={list.data}
          onPageChange={setPage}
          emptyText="No appointments for this date."
        />
        <div className="demo-note">
          Booking or editing an appointment flags its route/date schedule as changed. Use{" "}
          <b>Day Plan &amp; Map → Re-optimize</b> to recalculate ETAs and notify customers of the updated plan.
        </div>
      </Card>

      <AppointmentFormModal
        open={!!modal}
        onClose={() => setModal(null)}
        appointment={modal?.mode === "edit" ? modal.appt : null}
        initialDate={date || undefined}
        onSaved={() => list.reload({ silent: true })}
      />

      <ConfirmDialog
        open={!!toDelete}
        title="Delete appointment"
        message={
          toDelete && (
            <p>
              Delete the <b>{toDelete.taskName}</b> appointment for <b>{toDelete.customerName}</b> on{" "}
              {formatDate(toDelete.date)}?
            </p>
          )
        }
        onClose={() => setToDelete(null)}
        onConfirm={async () => {
          if (!toDelete) return;
          try {
            await appointmentsApi.remove(toDelete.id);
            toast.success("Appointment deleted");
            setToDelete(null);
            list.reload({ silent: true });
          } catch (err) {
            handleApiError(err);
          }
        }}
      />
    </>
  );
}
