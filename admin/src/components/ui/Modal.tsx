"use client";

import { useEffect, type ReactNode } from "react";
import { X } from "lucide-react";
import { cn } from "@/lib/utils";

export interface ModalProps {
  open: boolean;
  onClose: () => void;
  title: ReactNode;
  children: ReactNode;
  footer?: ReactNode;
  size?: "sm" | "md" | "lg" | "xl";
  className?: string;
}

const WIDTH = { sm: "w-[420px]", md: "w-[560px]", lg: "w-[720px]", xl: "w-[880px]" };

export function Modal({ open, onClose, title, children, footer, size = "md", className }: ModalProps) {
  useEffect(() => {
    if (!open) return;
    const onKey = (e: KeyboardEvent) => {
      if (e.key === "Escape") onClose();
    };
    window.addEventListener("keydown", onKey);
    return () => window.removeEventListener("keydown", onKey);
  }, [open, onClose]);

  if (!open) return null;
  // Clicking the dimmed background does NOT close the pop-up (so typed data isn't lost by a stray click);
  // it closes only with ✕, Cancel or the Esc key.
  return (
    <div className="fixed inset-0 z-[2000] flex items-center justify-center bg-[rgba(15,25,38,.5)] p-3">
      <div
        role="dialog"
        aria-modal="true"
        className={cn(
          "flex max-h-[90vh] max-w-[96vw] flex-col rounded-[14px] bg-white shadow-[0_18px_60px_rgba(0,0,0,.3)]",
          WIDTH[size],
          className,
        )}
      >
        <div className="flex items-center border-b border-line px-5 py-[15px]">
          <h3 className="flex-1 text-[15.5px] font-semibold">{title}</h3>
          <button
            type="button"
            aria-label="Close"
            className="cursor-pointer text-muted hover:text-ink"
            onClick={onClose}
          >
            <X size={19} />
          </button>
        </div>
        <div className="overflow-y-auto px-5 py-[18px]">{children}</div>
        {footer && (
          <div className="flex flex-wrap justify-end gap-[9px] border-t border-line px-5 py-[14px]">
            {footer}
          </div>
        )}
      </div>
    </div>
  );
}
