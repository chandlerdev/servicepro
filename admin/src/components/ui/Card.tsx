import type { ReactNode } from "react";
import { cn } from "@/lib/utils";

export function Card({
  title,
  actions,
  children,
  className,
  bodyClassName,
}: {
  title?: ReactNode;
  /** rendered on the right of the header */
  actions?: ReactNode;
  children?: ReactNode;
  className?: string;
  bodyClassName?: string;
}) {
  return (
    <section className={cn("card", className)}>
      {(title || actions) && (
        <div className="cardhead">
          {title && <h2 className="card-title">{title}</h2>}
          <div className="flex-1" />
          {actions}
        </div>
      )}
      <div className={bodyClassName}>{children}</div>
    </section>
  );
}

export function StatTile({
  value,
  label,
  tone,
}: {
  value: ReactNode;
  label: ReactNode;
  tone?: "danger" | "warn" | "ok";
}) {
  return (
    <div className="stat">
      <div
        className={cn(
          "n",
          tone === "danger" && "text-danger!",
          tone === "warn" && "text-warn!",
          tone === "ok" && "text-ok!",
        )}
      >
        {value}
      </div>
      <div className="l">{label}</div>
    </div>
  );
}
