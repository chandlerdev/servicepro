"use client";

import { useMemo, useState } from "react";
import { useForm } from "react-hook-form";
import { zodResolver } from "@hookform/resolvers/zod";
import { z } from "zod";
import toast from "react-hot-toast";
import { Button } from "@/components/ui/Button";
import { Card } from "@/components/ui/Card";
import { ConfirmDialog } from "@/components/ui/ConfirmDialog";
import { DataTable, type DataColumn } from "@/components/ui/DataTable";
import { FormField, FormRow, Input } from "@/components/ui/FormField";
import { Modal } from "@/components/ui/Modal";
import { useApi } from "@/hooks/useApi";
import { handleApiError } from "@/lib/api";
import { formatMoney } from "@/lib/format";
import { numberText, req } from "@/lib/schemas";
import { tasksApi } from "@/lib/services";
import type { ServiceTask } from "@/types/api";

export default function TasksPage() {
  const list = useApi(() => tasksApi.list(), []);
  const [editing, setEditing] = useState<ServiceTask | "new" | null>(null);
  const [toDelete, setToDelete] = useState<ServiceTask | null>(null);

  const columns = useMemo<DataColumn<ServiceTask>[]>(
    () => [
      {
        id: "name",
        header: "Task",
        cell: ({ row }) => <b>{row.original.name}</b>,
      },
      { id: "rate", header: "Rate", cell: ({ row }) => formatMoney(row.original.rate) },
      { id: "dur", header: "Time on site", cell: ({ row }) => `${row.original.durationMinutes} min` },
      {
        id: "actions",
        header: "Actions",
        meta: { className: "text-right whitespace-nowrap" },
        cell: ({ row }) => (
          <div className="inline-flex gap-1">
            <Button variant="secondary" size="sm" onClick={() => setEditing(row.original)}>
              Edit
            </Button>
            <Button variant="danger" size="sm" onClick={() => setToDelete(row.original)}>
              Delete
            </Button>
          </div>
        ),
      },
    ],
    [],
  );

  return (
    <>
      <Card title="🧰 Service Tasks" actions={<Button onClick={() => setEditing("new")}>＋ New Task</Button>}>
        <DataTable
          columns={columns}
          data={list.data}
          loading={list.loading}
          error={list.error}
          onRetry={() => list.reload()}
          emptyText="No tasks yet — add your first service."
        />
      </Card>

      {editing && (
        <TaskModal
          task={editing === "new" ? null : editing}
          onClose={() => setEditing(null)}
          onSaved={() => list.reload({ silent: true })}
        />
      )}

      <ConfirmDialog
        open={!!toDelete}
        title="Delete task"
        message={
          toDelete && (
            <p>
              Delete <b>{toDelete.name}</b> from the catalog?
            </p>
          )
        }
        onClose={() => setToDelete(null)}
        onConfirm={async () => {
          if (!toDelete) return;
          try {
            await tasksApi.remove(toDelete.id);
            toast.success("Task deleted");
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

const schema = z.object({
  name: req("Task name"),
  rate: numberText("Rate", 0),
  durationMinutes: numberText("Duration", 5),
});
type FormValues = z.infer<typeof schema>;

function TaskModal({
  task,
  onClose,
  onSaved,
}: {
  task: ServiceTask | null;
  onClose: () => void;
  onSaved: (t: ServiceTask) => void;
}) {
  const {
    register,
    handleSubmit,
    setError,
    formState: { errors, isSubmitting },
  } = useForm<FormValues>({
    resolver: zodResolver(schema),
    defaultValues: {
      name: task?.name ?? "",
      rate: String(task?.rate ?? 100),
      durationMinutes: String(task?.durationMinutes ?? 60),
    },
  });

  const onSubmit = async (v: FormValues) => {
    const body = {
      name: v.name,
      rate: Number(v.rate),
      durationMinutes: Math.round(Number(v.durationMinutes)),
    };
    try {
      const saved = task ? await tasksApi.update(task.id, body) : await tasksApi.create(body);
      toast.success(task ? "Task updated" : "Task created");
      onSaved(saved);
      onClose();
    } catch (err) {
      handleApiError(err, setError);
    }
  };

  return (
    <Modal
      open
      onClose={onClose}
      title={task ? "Edit Task" : "New Task"}
      footer={
        <>
          <Button variant="secondary" onClick={onClose}>
            Cancel
          </Button>
          <Button type="submit" form="task-form" loading={isSubmitting}>
            Save
          </Button>
        </>
      }
    >
      <form id="task-form" onSubmit={handleSubmit(onSubmit)} noValidate>
        <FormRow>
          <FormField label="Task name" error={errors.name?.message}>
            <Input aria-invalid={!!errors.name} {...register("name")} />
          </FormField>
        </FormRow>
        <FormRow>
          <FormField label="Rate (USD)" error={errors.rate?.message}>
            <Input type="number" min={0} step="0.01" aria-invalid={!!errors.rate} {...register("rate")} />
          </FormField>
          <FormField label="Time on site (minutes)" error={errors.durationMinutes?.message}>
            <Input
              type="number"
              min={5}
              step={5}
              aria-invalid={!!errors.durationMinutes}
              {...register("durationMinutes")}
            />
          </FormField>
        </FormRow>
      </form>
    </Modal>
  );
}
