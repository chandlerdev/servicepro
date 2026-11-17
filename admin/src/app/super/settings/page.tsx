"use client";

import { useState, type ReactNode } from "react";
import { useForm } from "react-hook-form";
import { zodResolver } from "@hookform/resolvers/zod";
import { z } from "zod";
import toast from "react-hot-toast";
import { CheckCircle2, CircleSlash } from "lucide-react";
import { Button } from "@/components/ui/Button";
import { Card } from "@/components/ui/Card";
import { ConfirmDialog } from "@/components/ui/ConfirmDialog";
import { ErrorState } from "@/components/ui/EmptyState";
import { FormField, FormRow, Input, PasswordInput } from "@/components/ui/FormField";
import { Modal } from "@/components/ui/Modal";
import { PageSpinner } from "@/components/ui/Spinner";
import { useApi } from "@/hooks/useApi";
import { handleApiError } from "@/lib/api";
import { superSettingsApi, type IntegrationKey, type Integrations } from "@/lib/services";

const NAMES: Record<IntegrationKey, string> = {
  sms: "SMS (Twilio)",
  maps: "Google Maps (geocoding & routing)",
  mail: "Email (SMTP)",
};

/**
 * Super Admin settings: the SMS, Google Maps and Email keys every admin shares. Saved keys apply immediately.
 * Secrets are write-only — the page shows only their last 4 characters; leave a secret blank to keep it.
 */
export default function SuperSettingsPage() {
  const res = useApi(() => superSettingsApi.integrations(), []);
  const [editing, setEditing] = useState<IntegrationKey | null>(null);
  const [disconnecting, setDisconnecting] = useState<IntegrationKey | null>(null);
  const [testing, setTesting] = useState(false);

  if (!res.data) {
    return res.loading ? <PageSpinner /> : <ErrorState message={res.error ?? "Failed to load"} onRetry={() => res.reload()} />;
  }
  const d = res.data;
  const saved = (next: Integrations) => {
    res.setData(() => next);
    setEditing(null);
  };

  return (
    <>
      <Card title="🔌 Integrations">
        <div className="grid grid-cols-1 gap-3 lg:grid-cols-3">
          <IntegrationCard
            name={NAMES.sms}
            purpose="Appointment texts to customers and their replies"
            connected={d.sms.connected}
            details={[
              ["Account SID", d.sms.accountSid],
              ["Auth token", d.sms.authTokenHint],
              ["Sending number", d.sms.fromNumber],
              ["Reply webhook URL", d.sms.webhookUrl],
            ]}
            onEdit={() => setEditing("sms")}
            onDisconnect={() => setDisconnecting("sms")}
          />
          <IntegrationCard
            name={NAMES.maps}
            purpose="Address suggestions, map pins and drive times for route planning"
            connected={d.maps.connected}
            details={[
              ["API key", d.maps.apiKeyHint],
              ["Address suggestions from", d.maps.regions ? d.maps.regions.toUpperCase().replaceAll(",", ", ") : "Worldwide"],
            ]}
            onEdit={() => setEditing("maps")}
            onDisconnect={() => setDisconnecting("maps")}
          />
          <IntegrationCard
            name={NAMES.mail}
            purpose="Forgot-password codes and email notifications"
            connected={d.mail.connected}
            details={[
              ["SMTP server", d.mail.host ? `${d.mail.host}${d.mail.port ? `:${d.mail.port}` : ""}` : null],
              ["Username", d.mail.username],
              ["Password", d.mail.passwordSet ? "••••••••" : null],
              ["From address", d.mail.from],
            ]}
            onEdit={() => setEditing("mail")}
            onDisconnect={() => setDisconnecting("mail")}
            extra={
              d.mail.connected && (
                <Button size="sm" variant="secondary" onClick={() => setTesting(true)}>
                  Send test email
                </Button>
              )
            }
          />
        </div>
      </Card>

      {editing === "sms" && <SmsModal data={d} onClose={() => setEditing(null)} onSaved={saved} />}
      {editing === "maps" && <MapsModal data={d} onClose={() => setEditing(null)} onSaved={saved} />}
      {editing === "mail" && <MailModal data={d} onClose={() => setEditing(null)} onSaved={saved} />}
      {testing && <TestMailModal onClose={() => setTesting(false)} />}

      <ConfirmDialog
        open={!!disconnecting}
        title="Disconnect"
        confirmLabel="Disconnect"
        message={
          disconnecting && (
            <>
              <p>
                Remove the <b>{NAMES[disconnecting]}</b> keys?
              </p>
              <p className="mt-2 text-[13px] text-muted">
                {disconnecting === "sms" && "Texts to customers will stop being sent (they are only logged) for every admin."}
                {disconnecting === "maps" &&
                  "Address suggestions stop working and drive times are estimated from straight-line distance, for every admin."}
                {disconnecting === "mail" && "Forgot-password codes and email notifications will stop being sent, for every admin."}
              </p>
            </>
          )
        }
        onClose={() => setDisconnecting(null)}
        onConfirm={async () => {
          if (!disconnecting) return;
          try {
            const next = await superSettingsApi.disconnect(disconnecting);
            res.setData(() => next);
            toast.success(`${NAMES[disconnecting]} disconnected`);
          } catch (err) {
            handleApiError(err);
            res.reload({ silent: true });
          } finally {
            setDisconnecting(null);
          }
        }}
      />
    </>
  );
}

function IntegrationCard({
  name,
  purpose,
  connected,
  details,
  onEdit,
  onDisconnect,
  extra,
}: {
  name: string;
  purpose: string;
  connected: boolean;
  details: [string, string | null][];
  onEdit: () => void;
  onDisconnect: () => void;
  extra?: ReactNode;
}) {
  return (
    <div className="flex flex-col gap-3 rounded-[9px] border border-line p-3">
      <div className="flex items-start gap-3">
        {connected ? (
          <CheckCircle2 className="mt-0.5 shrink-0 text-ok" size={20} />
        ) : (
          <CircleSlash className="mt-0.5 shrink-0 text-[#9aa5b4]" size={20} />
        )}
        <div className="min-w-0">
          <div className="font-semibold">{name}</div>
          <span className={connected ? "badge badge-green" : "badge badge-grey"}>
            {connected ? "Connected" : "Not connected"}
          </span>
        </div>
      </div>
      <p className="text-[12.5px] text-muted">{purpose}</p>
      <dl className="grid grid-cols-[auto_1fr] gap-x-3 gap-y-1 text-[13px]">
        {details.map(([label, value]) => (
          <div key={label} className="contents">
            <dt className="text-muted">{label}</dt>
            <dd className={value ? "break-all" : "text-[#9aa5b4]"}>{value || "Not set"}</dd>
          </div>
        ))}
      </dl>
      <div className="mt-auto flex flex-wrap gap-2 border-t border-line pt-3">
        <Button size="sm" onClick={onEdit}>
          {connected ? "Edit" : "Set up"}
        </Button>
        {extra}
        {connected && (
          <Button size="sm" variant="danger" onClick={onDisconnect}>
            Disconnect
          </Button>
        )}
      </div>
    </div>
  );
}

/** Pop-up footer: Cancel + Save (submits the form with the given id). */
function Footer({ formId, onClose, busy }: { formId: string; onClose: () => void; busy: boolean }) {
  return (
    <>
      <Button variant="secondary" onClick={onClose} disabled={busy}>
        Cancel
      </Button>
      <Button type="submit" form={formId} loading={busy}>
        Save
      </Button>
    </>
  );
}

const keepHint = (hint: string | null) => (hint ? `Saved: ${hint} — leave blank to keep it` : undefined);

// ------------------------------------------------------------------ SMS

const smsSchema = z.object({
  accountSid: z
    .string()
    .trim()
    .min(1, "Account SID is required")
    .regex(/^AC[0-9a-fA-F]{32}$/, "Starts with AC followed by 32 letters/digits (from the Twilio console)"),
  authToken: z.string().trim(),
  fromNumber: z
    .string()
    .trim()
    .min(1, "Sending number is required")
    .regex(/^(\+[1-9]\d{7,14}|MG[0-9a-fA-F]{32})$/, "Full number with country code, e.g. +15125550100"),
  webhookUrl: z.union([z.literal(""), z.string().trim().regex(/^https:\/\/\S+$/, "Must start with https://")]),
});

function SmsModal({ data, onClose, onSaved }: { data: Integrations; onClose: () => void; onSaved: (d: Integrations) => void }) {
  const s = data.sms;
  const suggestedWebhook =
    typeof window === "undefined" ? "" : `${window.location.origin}/api/webhooks/twilio/sms`;
  const {
    register,
    handleSubmit,
    setError,
    formState: { errors, isSubmitting },
  } = useForm<z.infer<typeof smsSchema>>({
    resolver: zodResolver(smsSchema),
    defaultValues: {
      accountSid: s.accountSid ?? "",
      authToken: "",
      fromNumber: s.fromNumber ?? "",
      webhookUrl: s.webhookUrl ?? "",
    },
  });
  const onSubmit = handleSubmit(async (v) => {
    if (!v.authToken && !s.authTokenSet) {
      setError("authToken", { message: "Auth token is required" });
      return;
    }
    try {
      onSaved(await superSettingsApi.saveSms(v));
      toast.success("SMS settings saved");
    } catch (err) {
      handleApiError(err, setError);
    }
  });
  return (
    <Modal open onClose={onClose} title={NAMES.sms} footer={<Footer formId="sms-form" onClose={onClose} busy={isSubmitting} />}>
      <form id="sms-form" onSubmit={onSubmit} noValidate>
        <p className="mb-3 text-[12.5px] text-muted">
          From the Twilio console (Account → API keys &amp; tokens). Used for every admin&apos;s customer texts.
        </p>
        <FormField label="Account SID" error={errors.accountSid?.message} className="mb-3">
          <Input placeholder="AC…" autoComplete="off" aria-invalid={!!errors.accountSid} {...register("accountSid")} />
        </FormField>
        <FormField label="Auth token" error={errors.authToken?.message} hint={keepHint(s.authTokenHint)} className="mb-3">
          <PasswordInput
            autoComplete="new-password"
            placeholder={s.authTokenSet ? "Leave blank to keep the saved token" : ""}
            aria-invalid={!!errors.authToken}
            {...register("authToken")}
          />
        </FormField>
        <FormField
          label="Sending number"
          error={errors.fromNumber?.message}
          hint="Your Twilio number with country code, e.g. +15125550100"
          className="mb-3"
        >
          <Input placeholder="+1…" autoComplete="off" aria-invalid={!!errors.fromNumber} {...register("fromNumber")} />
        </FormField>
        <FormField
          label="Reply webhook URL (optional)"
          error={errors.webhookUrl?.message}
          hint={suggestedWebhook ? `Usually ${suggestedWebhook} — also set it in Twilio for the number's incoming messages` : undefined}
        >
          <Input placeholder="https://…/api/webhooks/twilio/sms" autoComplete="off" aria-invalid={!!errors.webhookUrl} {...register("webhookUrl")} />
        </FormField>
      </form>
    </Modal>
  );
}

// ------------------------------------------------------------------ Google Maps

const mapsSchema = z.object({
  apiKey: z.string().trim(),
  regions: z
    .string()
    .trim()
    .regex(/^([A-Za-z]{2}\s*(,\s*[A-Za-z]{2}\s*)*)?$/, "2-letter country codes separated by commas, e.g. us, ca"),
});

function MapsModal({ data, onClose, onSaved }: { data: Integrations; onClose: () => void; onSaved: (d: Integrations) => void }) {
  const m = data.maps;
  const {
    register,
    handleSubmit,
    setError,
    formState: { errors, isSubmitting },
  } = useForm<z.infer<typeof mapsSchema>>({
    resolver: zodResolver(mapsSchema),
    defaultValues: { apiKey: "", regions: (m.regions ?? "").replaceAll(",", ", ") },
  });
  const onSubmit = handleSubmit(async (v) => {
    if (!v.apiKey && !m.apiKeySet) {
      setError("apiKey", { message: "API key is required" });
      return;
    }
    try {
      onSaved(await superSettingsApi.saveMaps(v));
      toast.success("Google Maps settings saved");
    } catch (err) {
      handleApiError(err, setError);
    }
  });
  return (
    <Modal open onClose={onClose} title={NAMES.maps} footer={<Footer formId="maps-form" onClose={onClose} busy={isSubmitting} />}>
      <form id="maps-form" onSubmit={onSubmit} noValidate>
        <p className="mb-3 text-[12.5px] text-muted">
          A server key from Google Cloud with <b>Places API (New)</b>, <b>Geocoding API</b> and <b>Routes API</b> enabled.
        </p>
        <FormField label="API key" error={errors.apiKey?.message} hint={keepHint(m.apiKeyHint)} className="mb-3">
          <PasswordInput
            autoComplete="new-password"
            placeholder={m.apiKeySet ? "Leave blank to keep the saved key" : "AIza…"}
            aria-invalid={!!errors.apiKey}
            {...register("apiKey")}
          />
        </FormField>
        <FormField
          label="Address suggestions from"
          error={errors.regions?.message}
          hint="Country codes, e.g. us or us, ca — leave empty for worldwide"
        >
          <Input placeholder="us" autoComplete="off" aria-invalid={!!errors.regions} {...register("regions")} />
        </FormField>
      </form>
    </Modal>
  );
}

// ------------------------------------------------------------------ Email

const mailSchema = z.object({
  host: z.string().trim().min(1, "SMTP server is required"),
  port: z
    .string()
    .trim()
    .regex(/^\d{1,5}$/, "1–65535")
    .refine((p) => Number(p) >= 1 && Number(p) <= 65535, "1–65535"),
  username: z.string().trim(),
  password: z.string(),
  from: z.email("Enter a valid email"),
});

function MailModal({ data, onClose, onSaved }: { data: Integrations; onClose: () => void; onSaved: (d: Integrations) => void }) {
  const m = data.mail;
  const {
    register,
    handleSubmit,
    setError,
    formState: { errors, isSubmitting },
  } = useForm<z.infer<typeof mailSchema>>({
    resolver: zodResolver(mailSchema),
    defaultValues: {
      host: m.host ?? "",
      port: String(m.port ?? 587),
      username: m.username ?? "",
      password: "",
      from: m.from ?? "",
    },
  });
  const onSubmit = handleSubmit(async (v) => {
    const sameUser = !!m.username && v.username.toLowerCase() === m.username.toLowerCase();
    if (v.username && !v.password && !(sameUser && m.passwordSet)) {
      setError("password", { message: "Password is required for this username" });
      return;
    }
    try {
      onSaved(await superSettingsApi.saveMail({ ...v, port: Number(v.port) }));
      toast.success("Email settings saved");
    } catch (err) {
      handleApiError(err, setError);
    }
  });
  return (
    <Modal open onClose={onClose} title={NAMES.mail} footer={<Footer formId="mail-form" onClose={onClose} busy={isSubmitting} />}>
      <form id="mail-form" onSubmit={onSubmit} noValidate>
        <p className="mb-3 text-[12.5px] text-muted">
          The mail server that sends forgot-password codes and email notifications. Port 587 uses STARTTLS, 465 uses SSL.
        </p>
        <FormRow>
          <FormField label="SMTP server" error={errors.host?.message} className="sm:col-span-2">
            <Input placeholder="smtp.example.com" autoComplete="off" aria-invalid={!!errors.host} {...register("host")} />
          </FormField>
          <FormField label="Port" error={errors.port?.message}>
            <Input inputMode="numeric" placeholder="587" aria-invalid={!!errors.port} {...register("port")} />
          </FormField>
        </FormRow>
        <FormRow>
          <FormField label="Username" error={errors.username?.message}>
            <Input autoComplete="off" aria-invalid={!!errors.username} {...register("username")} />
          </FormField>
          <FormField
            label="Password"
            error={errors.password?.message}
            hint={m.passwordSet ? "Saved — leave blank to keep it" : undefined}
          >
            <PasswordInput autoComplete="new-password" aria-invalid={!!errors.password} {...register("password")} />
          </FormField>
        </FormRow>
        <FormField label="From address" error={errors.from?.message} hint="The sender customers and users see, e.g. no-reply@yourdomain.com">
          <Input type="email" autoComplete="off" aria-invalid={!!errors.from} {...register("from")} />
        </FormField>
      </form>
    </Modal>
  );
}

const testSchema = z.object({ to: z.email("Enter a valid email") });

function TestMailModal({ onClose }: { onClose: () => void }) {
  const {
    register,
    handleSubmit,
    setError,
    formState: { errors, isSubmitting },
  } = useForm<z.infer<typeof testSchema>>({ resolver: zodResolver(testSchema), defaultValues: { to: "" } });
  const onSubmit = handleSubmit(async (v) => {
    try {
      const r = await superSettingsApi.testMail(v.to.trim());
      toast.success(r.message);
      onClose();
    } catch (err) {
      handleApiError(err, setError);
    }
  });
  return (
    <Modal
      open
      size="sm"
      onClose={onClose}
      title="Send test email"
      footer={
        <>
          <Button variant="secondary" onClick={onClose} disabled={isSubmitting}>
            Cancel
          </Button>
          <Button type="submit" form="test-mail-form" loading={isSubmitting}>
            Send
          </Button>
        </>
      }
    >
      <form id="test-mail-form" onSubmit={onSubmit} noValidate>
        <FormField label="Send to" error={errors.to?.message}>
          <Input type="email" autoFocus placeholder="you@example.com" aria-invalid={!!errors.to} {...register("to")} />
        </FormField>
      </form>
    </Modal>
  );
}
