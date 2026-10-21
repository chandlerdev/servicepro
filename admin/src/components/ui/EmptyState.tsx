import type { ReactNode } from "react";
import { cn } from "@/lib/utils";

export function EmptyState({
  icon,
  title,
  children,
  className,
}: {
  icon?: ReactNode;
  title?: ReactNode;
  children?: ReactNode;
  className?: string;
}) {
  return (
    <div className={cn("empty", className)}>
      {icon && <div className="mb-2 text-2xl">{icon}</div>}
      {title && <div className="font-semibold text-ink">{title}</div>}
      {children && <div className="mt-1">{children}</div>}
    </div>
  );
}

/** Error box with retry. */
export function ErrorState({ message, onRetry }: { message: string; onRetry?: () => void }) {
  return (
    <div className="empty">
      <div className="mb-1 text-2xl">⚠️</div>
      <div className="font-semibold text-danger">{message}</div>
      {onRetry && (
        <button type="button" className="btn btn-sec btn-sm mt-3" onClick={onRetry}>
          Retry
        </button>
      )}
    </div>
  );
}
