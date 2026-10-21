import { Mail, MessageSquare } from "lucide-react";
import { EmptyState } from "@/components/ui/EmptyState";
import { StatusBadge } from "@/components/ui/StatusBadge";
import { NOTIFICATION_TYPE_META } from "@/lib/enums";
import { formatDateTime } from "@/lib/format";
import type { Notification } from "@/types/api";

export function NotificationList({ items }: { items: Notification[] }) {
  if (!items.length) {
    return <EmptyState>No messages sent yet. Book an appointment or send the day&apos;s schedule.</EmptyState>;
  }
  return (
    <div>
      {items.map((n) => {
        const meta = NOTIFICATION_TYPE_META[n.type] ?? { icon: "📨", label: n.type };
        return (
          <div key={n.id} className="smsrow">
            <div className="smsic">{meta.icon}</div>
            <div className="min-w-0 flex-1">
              <div className="mb-[3px] flex flex-wrap items-center gap-x-1.5 gap-y-1 text-[11.5px] text-muted">
                <b className="text-ink">{meta.label}</b>
                <span className="inline-flex items-center gap-1">
                  · {n.channel === "EMAIL" ? <Mail size={12} /> : <MessageSquare size={12} />}
                  to {n.toName} <span className="text-[#8a96a8]">{n.toAddress}</span>
                </span>
                <span>· {formatDateTime(n.createdAt)}</span>
                <StatusBadge kind="notification" status={n.status} />
              </div>
              <div className="smsbubble">{n.message}</div>
              {n.error && <div className="mt-1 text-[11.5px] text-danger">Error: {n.error}</div>}
            </div>
          </div>
        );
      })}
    </div>
  );
}
