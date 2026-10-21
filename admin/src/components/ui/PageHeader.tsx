import type { ReactNode } from "react";

/** Page title + subtitle (rendered in the top bar). */
export function PageHeader({ title, subtitle }: { title: ReactNode; subtitle?: ReactNode }) {
  return (
    <div className="min-w-0">
      <h1 className="truncate text-[17px] font-semibold">{title}</h1>
      {subtitle && <div className="truncate text-[12.5px] text-muted">{subtitle}</div>}
    </div>
  );
}
