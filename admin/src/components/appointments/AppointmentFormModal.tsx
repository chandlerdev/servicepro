"use client";

import { useMemo, type ChangeEvent } from "react";
import { useForm } from "react-hook-form";
import { zodResolver } from "@hookform/resolvers/zod";
import { z } from "zod";
import toast from "react-hot-toast";
import { Button } from "@/components/ui/Button";
import { Checkbox, FormField, FormRow, Input, Textarea } from "@/components/ui/FormField";
import { Modal } from "@/components/ui/Modal";
import { Select, type SelectGroup } from "@/components/ui/Select";
import { useApi } from "@/hooks/useApi";
import { handleApiError } from "@/lib/api";
import { enumLabel } from "@/lib/enums";
import { addDays, formatMoney, todayISO } from "@/lib/format";
import { appointmentsApi, citiesApi, customersApi, routesApi, tasksApi } from "@/lib/services";
import { APPOINTMENT_STATUSES, type Appointment, type CustomerOption, type Route } from "@/types/api";

const schema = z.object({
  date: z.string().min(1, "Date is required"),
  customerId: z.string().min(1, "Choose a customer"),
  taskId: z.string().min(1, "Choose a task"),
  routeId: z.string(),
  notes: z.string(),
  status: z.enum(APPOINTMENT_STATUSES),
  sendConfirmation: z.boolean(),
});
type FormValues = z.infer<typeof schema>;

export interface AppointmentFormModalProps {
  open: boolean;
  onClose: () => void;
  /** edit mode when provided */
  appointment?: Appointment | null;
  initialDate?: string;
  onSaved?: (a: Appointment) => void;
}

/** Book / edit appointment modal (customer select grouped by city → auto-selects that city's route). */
export function AppointmentFormModal(props: AppointmentFormModalProps) {
  if (!props.open) return null;
  return <AppointmentForm key={props.appointment?.id ?? "new"} {...props} />;
}

function pickRoute(routes: Route[] | undefined, c: CustomerOption | undefined): Route | undefined {
  if (!routes || !c) return undefined;
  return routes.find((r) => r.city === c.city);
}

function AppointmentForm({ onClose, appointment, initialDate, onSaved }: AppointmentFormModalProps) {
  const isEdit = !!appointment;
  const customers = useApi(() => customersApi.options(), []);
  const tasks = useApi(() => tasksApi.list(), []);
  const routes = useApi(() => routesApi.list(), []);
  const cities = useApi(() => citiesApi.list(), []);

  const {
    register,
    handleSubmit,
    setValue,
    setError,
    formState: { errors, isSubmitting },
  } = useForm<FormValues>({
    resolver: zodResolver(schema),
    defaultValues: appointment
      ? {
          date: appointment.date,
          customerId: String(appointment.customerId),
          taskId: String(appointment.taskId),
          routeId: String(appointment.routeId),
          notes: appointment.notes ?? "",
          status: appointment.status,
          sendConfirmation: false,
        }
      : {
          date: initialDate ?? addDays(todayISO(), 1),
          customerId: "",
          taskId: "",
          routeId: "",
          notes: "",
          status: "SCHEDULED",
          sendConfirmation: true,
        },
  });

  // Optgroups in the order of the City Master, then any other city.
  const customerGroups: SelectGroup[] = useMemo(() => {
    const byCity = new Map<string, CustomerOption[]>();
    for (const c of customers.data ?? []) {
      const k = c.city || "Other";
      byCity.set(k, [...(byCity.get(k) ?? []), c]);
    }
    const order = (cities.data ?? []).map((c) => c.name);
    const rank = (name: string) => {
      const i = order.indexOf(name);
      return i < 0 ? order.length : i;
    };
    return [...byCity.entries()]
      .sort(([a], [b]) => rank(a) - rank(b) || a.localeCompare(b))
      .map(([label, list]) => ({
        label,
        options: list
          .sort((a, b) => a.name.localeCompare(b.name))
          .map((c) => ({ value: c.id, label: `${c.name} — ${c.address}` })),
      }));
  }, [customers.data, cities.data]);

  const taskOptions = (tasks.data ?? []).map((t) => ({
    value: t.id,
    label: `${t.name} (${formatMoney(t.rate)} · ${t.durationMinutes} min)`,
  }));
  // A deleted task is hidden from the catalog but must stay selectable on its existing appointments.
  if (appointment && tasks.data && !tasks.data.some((t) => t.id === appointment.taskId)) {
    taskOptions.push({ value: appointment.taskId, label: appointment.taskName });
  }

  const routeOptions = (routes.data ?? []).map((r) => ({ value: r.id, label: r.name }));

  const customerField = register("customerId", {
    onChange: (e: ChangeEvent<HTMLSelectElement>) => {
      const c = customers.data?.find((x) => String(x.id) === e.target.value);
      const r = pickRoute(routes.data, c);
      if (r) setValue("routeId", String(r.id));
    },
  });

  const onSubmit = async (v: FormValues) => {
    try {
      let saved: Appointment;
      if (appointment) {
        if (!v.routeId) {
          setError("routeId", { message: "Choose a route" });
          return;
        }
        saved = await appointmentsApi.update(appointment.id, {
          date: v.date,
          customerId: Number(v.customerId),
          taskId: Number(v.taskId),
          routeId: Number(v.routeId),
          notes: v.notes,
          status: v.status,
        });
        toast.success("Appointment updated");
      } else {
        saved = await appointmentsApi.create({
          date: v.date,
          customerId: Number(v.customerId),
          taskId: Number(v.taskId),
          routeId: v.routeId ? Number(v.routeId) : null,
          notes: v.notes || undefined,
          sendConfirmation: v.sendConfirmation,
        });
        toast.success(v.sendConfirmation ? "Appointment booked — confirmation texted" : "Appointment booked");
      }
      onSaved?.(saved);
      onClose();
    } catch (err) {
      handleApiError(err, setError);
    }
  };

  const listsLoading = customers.loading || tasks.loading || routes.loading;
  const listError = customers.error || tasks.error || routes.error;

  return (
    <Modal
      open
      onClose={onClose}
      title={isEdit ? "Edit Appointment" : "Book Appointment"}
      size="md"
      footer={
        <>
          <Button variant="secondary" onClick={onClose}>
            Cancel
          </Button>
          <Button type="submit" form="appt-form" loading={isSubmitting} disabled={listsLoading}>
            {isEdit ? "Save" : "Book appointment"}
          </Button>
        </>
      }
    >
      <form id="appt-form" onSubmit={handleSubmit(onSubmit)} noValidate>
        {listError && <div className="banner">⚠️ {listError}</div>}
        <FormRow>
          <FormField label="Date" error={errors.date?.message}>
            <Input type="date" aria-invalid={!!errors.date} {...register("date")} />
          </FormField>
          <FormField label="Arrival time">
            <Input disabled value="Allocated automatically the previous day" readOnly />
          </FormField>
        </FormRow>
        <FormRow>
          <FormField
            label={
              <>
                Customer{" "}
                <span className="normal-case tracking-normal text-muted">
                  (picking one auto-selects their city&apos;s route)
                </span>
              </>
            }
            error={errors.customerId?.message}
          >
            <Select
              aria-invalid={!!errors.customerId}
              placeholder={customers.loading ? "Loading customers…" : "— Select customer —"}
              groups={customerGroups}
              {...customerField}
            />
          </FormField>
        </FormRow>
        <FormRow>
          <FormField label="Task" error={errors.taskId?.message}>
            <Select
              aria-invalid={!!errors.taskId}
              placeholder={tasks.loading ? "Loading…" : "— Select task —"}
              options={taskOptions}
              {...register("taskId")}
            />
          </FormField>
          <FormField label="Route" error={errors.routeId?.message}>
            <Select
              aria-invalid={!!errors.routeId}
              placeholder={isEdit ? "— Select route —" : "Auto (by customer's city)"}
              options={routeOptions}
              {...register("routeId")}
            />
          </FormField>
        </FormRow>
        {isEdit && (
          <FormRow>
            <FormField label="Status" error={errors.status?.message}>
              <Select
                options={APPOINTMENT_STATUSES.map((s) => ({ value: s, label: enumLabel(s) }))}
                {...register("status")}
              />
            </FormField>
          </FormRow>
        )}
        <FormRow>
          <FormField label="Notes" error={errors.notes?.message}>
            <Textarea rows={2} placeholder="Gate code, pets, parking…" {...register("notes")} />
          </FormField>
        </FormRow>
        {!isEdit && (
          <Checkbox label="Text a booking confirmation to the customer" {...register("sendConfirmation")} />
        )}
      </form>
    </Modal>
  );
}
