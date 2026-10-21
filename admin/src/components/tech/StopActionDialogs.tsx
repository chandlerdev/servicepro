"use client";

import { useState } from "react";
import toast from "react-hot-toast";
import { Button } from "@/components/ui/Button";
import { FormField, Input, Textarea } from "@/components/ui/FormField";
import { Modal } from "@/components/ui/Modal";
import { handleApiError } from "@/lib/api";
import { techApi } from "@/lib/services";
import type { Stop } from "@/types/api";

/**
 * Technician stop actions shared by "My Schedule" and the Mobile App preview.
 * `technicianId` is only passed when an ADMIN acts on behalf of a technician.
 */
export interface StopDialogProps {
  stop: Stop;
  technicianId?: number | null;
  onClose: () => void;
  onDone: () => void;
}

/** "On my way" — texts the customer that the technician is travelling. Resolves true on success. */
export async function sendOnMyWay(stop: Stop, technicianId?: number | null): Promise<boolean> {
  try {
    await techApi.onMyWay(stop.appointmentId, technicianId);
    toast.success(`🚚 ${stop.customerName} was texted that you're on the way`);
    return true;
  } catch (err) {
    handleApiError(err);
    return false;
  }
}

/** Report delay: every remaining customer on the route gets an updated ETA by SMS. */
export function DelayDialog({
  stop,
  routeName,
  technicianId,
  onClose,
  onDone,
}: StopDialogProps & { routeName: string }) {
  const [minutes, setMinutes] = useState("30");
  const [reason, setReason] = useState("");
  const [saving, setSaving] = useState(false);
  const mins = Number(minutes);
  const valid = Number.isInteger(mins) && mins >= 5 && mins <= 480;

  const submit = async () => {
    if (!valid) return;
    setSaving(true);
    try {
      const res = await techApi.delay(stop.appointmentId, mins, reason.trim() || undefined, technicianId);
      toast.success(`⏳ Delay logged — ${res.notified} customer(s) notified`);
      onDone();
      onClose();
    } catch (err) {
      handleApiError(err);
    } finally {
      setSaving(false);
    }
  };

  return (
    <Modal
      open
      onClose={onClose}
      title="Report delay"
      footer={
        <>
          <Button variant="secondary" onClick={onClose}>
            Cancel
          </Button>
          <Button onClick={submit} loading={saving} disabled={!valid}>
            Notify customers
          </Button>
        </>
      }
    >
      <p className="mb-3 text-[13.5px]">
        How late is the <b>{routeName}</b> running? Every remaining customer on the route gets an updated ETA by SMS.
      </p>
      <FormField label="Delay (minutes)" error={valid ? undefined : "Enter 5 – 480 minutes"} className="mb-3">
        <Input type="number" min={5} max={480} step={5} value={minutes} onChange={(e) => setMinutes(e.target.value)} />
      </FormField>
      <FormField label="Reason (optional)">
        <Input placeholder="Traffic on I-10" value={reason} maxLength={200} onChange={(e) => setReason(e.target.value)} />
      </FormField>
    </Modal>
  );
}

/** Complete job: the customer is texted Google & Facebook review links. */
export function CompleteDialog({ stop, technicianId, onClose, onDone }: StopDialogProps) {
  const [notes, setNotes] = useState("");
  const [saving, setSaving] = useState(false);

  const submit = async () => {
    setSaving(true);
    try {
      await techApi.complete(stop.appointmentId, notes.trim() || undefined, technicianId);
      toast.success("✔ Job completed — review request sent");
      onDone();
      onClose();
    } catch (err) {
      handleApiError(err);
    } finally {
      setSaving(false);
    }
  };

  return (
    <Modal
      open
      onClose={onClose}
      title="Complete job"
      footer={
        <>
          <Button variant="secondary" onClick={onClose}>
            Cancel
          </Button>
          <Button variant="ok" onClick={submit} loading={saving}>
            Complete &amp; send
          </Button>
        </>
      }
    >
      <p className="text-[13.5px]">
        Mark <b>{stop.taskName}</b> for <b>{stop.customerName}</b> as completed?
      </p>
      <p className="mb-3 mt-2 text-xs text-muted">
        The customer will receive a thank-you SMS with Google and Facebook review links.
      </p>
      <FormField label="Job notes (optional)">
        <Textarea rows={2} maxLength={1000} value={notes} onChange={(e) => setNotes(e.target.value)} />
      </FormField>
    </Modal>
  );
}
