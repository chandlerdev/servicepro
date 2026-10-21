import { Loader2 } from "lucide-react";
import { cn } from "@/lib/utils";

export function Spinner({ size = 16, className }: { size?: number; className?: string }) {
  return <Loader2 aria-label="Loading" size={size} className={cn("animate-spin", className)} />;
}

export function PageSpinner({ label = "Loading…" }: { label?: string }) {
  return (
    <div className="flex items-center justify-center gap-2 py-12 text-muted">
      <Spinner size={18} /> <span className="text-sm">{label}</span>
    </div>
  );
}
