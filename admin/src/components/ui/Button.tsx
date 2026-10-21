import type { ComponentProps } from "react";
import { cn } from "@/lib/utils";
import { Spinner } from "./Spinner";

export type ButtonVariant = "primary" | "secondary" | "warn" | "danger" | "ok";

const VARIANT: Record<ButtonVariant, string> = {
  primary: "",
  secondary: "btn-sec",
  warn: "btn-warn",
  danger: "btn-dng",
  ok: "btn-ok",
};

export interface ButtonProps extends ComponentProps<"button"> {
  variant?: ButtonVariant;
  size?: "md" | "sm";
  loading?: boolean;
}

export function Button({
  variant = "primary",
  size = "md",
  loading = false,
  className,
  children,
  disabled,
  type = "button",
  ...rest
}: ButtonProps) {
  return (
    <button
      type={type}
      className={cn("btn", VARIANT[variant], size === "sm" && "btn-sm", className)}
      disabled={disabled || loading}
      {...rest}
    >
      {loading && <Spinner size={size === "sm" ? 12 : 14} />}
      {children}
    </button>
  );
}

/** Anchor styled as a button (for external links like Google Maps). */
export function LinkButton({
  variant = "secondary",
  size = "sm",
  className,
  ...rest
}: ComponentProps<"a"> & { variant?: ButtonVariant; size?: "md" | "sm" }) {
  return (
    <a
      target="_blank"
      rel="noopener noreferrer"
      className={cn("btn", VARIANT[variant], size === "sm" && "btn-sm", className)}
      {...rest}
    />
  );
}
