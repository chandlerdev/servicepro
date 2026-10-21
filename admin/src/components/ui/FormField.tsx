"use client";

import { useState, type ComponentProps, type ReactNode } from "react";
import { Eye, EyeOff } from "lucide-react";
import { cn } from "@/lib/utils";

export function FormField({
  label,
  error,
  hint,
  htmlFor,
  children,
  className,
}: {
  label?: ReactNode;
  error?: string;
  hint?: ReactNode;
  htmlFor?: string;
  children: ReactNode;
  className?: string;
}) {
  return (
    <div className={cn("min-w-0", className)}>
      {label && (
        <label className="flabel" htmlFor={htmlFor}>
          {label}
        </label>
      )}
      {children}
      {hint && !error && <p className="mt-1 text-[11.5px] text-muted">{hint}</p>}
      {error && <p className="mt-1 text-[11.5px] font-medium text-danger">{error}</p>}
    </div>
  );
}

/** Responsive row of fields (stacked on mobile, equal columns from `sm`). */
export function FormRow({ children, className }: { children: ReactNode; className?: string }) {
  return (
    <div className={cn("mb-3 grid grid-cols-1 gap-3 sm:auto-cols-fr sm:grid-flow-col", className)}>{children}</div>
  );
}

export function Input({ className, "aria-invalid": invalid, ...rest }: ComponentProps<"input">) {
  return <input className={cn("input", invalid && "input-error", className)} aria-invalid={invalid} {...rest} />;
}

/** Password box with an eye button to show / hide what was typed. */
export function PasswordInput({ className, ...rest }: Omit<ComponentProps<"input">, "type">) {
  const [visible, setVisible] = useState(false);
  return (
    <div className="relative">
      <Input type={visible ? "text" : "password"} className={cn("pr-10", className)} {...rest} />
      <button
        type="button"
        tabIndex={-1}
        aria-label={visible ? "Hide password" : "Show password"}
        title={visible ? "Hide password" : "Show password"}
        className="absolute top-1/2 right-2.5 -translate-y-1/2 cursor-pointer p-1 text-muted hover:text-ink"
        onClick={() => setVisible((v) => !v)}
      >
        {visible ? <EyeOff size={17} /> : <Eye size={17} />}
      </button>
    </div>
  );
}

export function Textarea({ className, "aria-invalid": invalid, ...rest }: ComponentProps<"textarea">) {
  return (
    <textarea className={cn("input", invalid && "input-error", className)} aria-invalid={invalid} {...rest} />
  );
}

export function Checkbox({
  label,
  className,
  ...rest
}: Omit<ComponentProps<"input">, "type"> & { label: ReactNode }) {
  return (
    <label className={cn("inline-flex cursor-pointer items-center gap-2 text-[13.5px]", className)}>
      <input type="checkbox" className="size-4 accent-brand" {...rest} />
      {label}
    </label>
  );
}
