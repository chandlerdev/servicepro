import type { ReactNode } from "react";
import {
  APPOINTMENT_STATUS_TONE,
  NOTIFICATION_STATUS_TONE,
  enumLabel,
  type BadgeTone,
} from "@/lib/enums";
import { cn } from "@/lib/utils";
import type { AppointmentStatus, NotificationStatus } from "@/types/api";

export function Badge({
  tone = "grey",
  children,
  className,
  title,
  onClick,
}: {
  tone?: BadgeTone;
  children: ReactNode;
  className?: string;
  title?: string;
  onClick?: () => void;
}) {
  if (onClick) {
    return (
      <button
        type="button"
        title={title}
        onClick={onClick}
        className={cn("badge cursor-pointer border-0", `badge-${tone}`, className)}
      >
        {children}
      </button>
    );
  }
  return (
    <span title={title} className={cn("badge", `badge-${tone}`, className)}>
      {children}
    </span>
  );
}

type StatusBadgeProps =
  | { kind?: "appointment"; status: AppointmentStatus }
  | { kind: "notification"; status: NotificationStatus };

/** Coloured badge for any status enum. */
export function StatusBadge(props: StatusBadgeProps & { className?: string }) {
  let tone: BadgeTone;
  switch (props.kind) {
    case "notification":
      tone = NOTIFICATION_STATUS_TONE[props.status];
      break;
    default:
      tone = APPOINTMENT_STATUS_TONE[props.status];
  }
  return (
    <Badge tone={tone ?? "grey"} className={props.className}>
      {enumLabel(props.status)}
    </Badge>
  );
}
