"use client";

import { useState, type ReactNode } from "react";
import { Button, type ButtonVariant } from "./Button";
import { Modal } from "./Modal";

export interface ConfirmDialogProps {
  open: boolean;
  title: ReactNode;
  message: ReactNode;
  confirmLabel?: string;
  /** "danger" renders a solid red confirm button */
  variant?: ButtonVariant;
  onConfirm: () => Promise<unknown> | void;
  onClose: () => void;
}

const noop = () => {};

/** Confirmation modal. Stays open (with spinner) until onConfirm settles. */
export function ConfirmDialog({
  open,
  title,
  message,
  confirmLabel = "Delete",
  variant = "danger",
  onConfirm,
  onClose,
}: ConfirmDialogProps) {
  const [busy, setBusy] = useState(false);
  const run = async () => {
    setBusy(true);
    try {
      await onConfirm();
    } finally {
      setBusy(false);
    }
  };
  const danger = variant === "danger";
  return (
    <Modal
      open={open}
      onClose={busy ? noop : onClose}
      title={title}
      size="sm"
      footer={
        <>
          <Button variant="secondary" onClick={onClose} disabled={busy}>
            Cancel
          </Button>
          <Button
            variant={danger ? "primary" : variant}
            className={danger ? "bg-danger! hover:bg-[#a92f2f]!" : undefined}
            loading={busy}
            onClick={run}
          >
            {confirmLabel}
          </Button>
        </>
      }
    >
      <div className="text-[13.5px] leading-relaxed">{message}</div>
    </Modal>
  );
}
