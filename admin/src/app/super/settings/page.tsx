"use client";

import { CheckCircle2, CircleSlash } from "lucide-react";
import { Card } from "@/components/ui/Card";
import { ErrorState } from "@/components/ui/EmptyState";
import { PageSpinner } from "@/components/ui/Spinner";
import { useApi } from "@/hooks/useApi";
import { superSettingsApi, type Integration } from "@/lib/services";

/** Super Admin settings: the integrations every admin shares (set in the server's env file, so read-only here). */
export default function SuperSettingsPage() {
  const res = useApi(() => superSettingsApi.integrations(), []);
  if (!res.data) {
    return res.loading ? <PageSpinner /> : <ErrorState message={res.error ?? "Failed to load"} onRetry={() => res.reload()} />;
  }
  return (
    <Card title="🔌 Integrations">
      <div className="grid grid-cols-1 gap-3 lg:grid-cols-3">
        {res.data.map((i) => (
          <IntegrationCard key={i.key} item={i} />
        ))}
      </div>
    </Card>
  );
}

function IntegrationCard({ item }: { item: Integration }) {
  return (
    <div className="flex flex-col gap-3 rounded-[9px] border border-line p-3">
      <div className="flex items-start gap-3">
        {item.connected ? (
          <CheckCircle2 className="mt-0.5 shrink-0 text-ok" size={20} />
        ) : (
          <CircleSlash className="mt-0.5 shrink-0 text-[#9aa5b4]" size={20} />
        )}
        <div className="min-w-0">
          <div className="font-semibold">{item.name}</div>
          <span className={item.connected ? "badge badge-green" : "badge badge-grey"}>
            {item.connected ? "Connected" : "Not connected"}
          </span>
        </div>
      </div>
      <p className="text-[12.5px] text-muted">{item.purpose}</p>
      <dl className="grid grid-cols-[auto_1fr] gap-x-3 gap-y-1 text-[13px]">
        {item.details.map((d) => (
          <div key={d.label} className="contents">
            <dt className="text-muted">{d.label}</dt>
            <dd className={d.value === "Not set" ? "break-all text-[#9aa5b4]" : "break-all"}>{d.value}</dd>
          </div>
        ))}
      </dl>
      <div className="border-t border-line pt-2 text-[12px] text-muted">
        Set on the server in <code>backend/.env.prod</code>, then restart the backend:
        <div className="mt-1 flex flex-wrap gap-1">
          {item.envVars.map((v) => (
            <code key={v} className="rounded bg-bg px-1.5 py-0.5 text-[11.5px] text-ink">
              {v}
            </code>
          ))}
        </div>
      </div>
    </div>
  );
}
