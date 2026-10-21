"use client";

import { useEffect, useState } from "react";
import Link from "next/link";
import { useRouter } from "next/navigation";
import { useForm } from "react-hook-form";
import { zodResolver } from "@hookform/resolvers/zod";
import { z } from "zod";
import { Zap } from "lucide-react";
import toast from "react-hot-toast";
import { Button } from "@/components/ui/Button";
import { FormField, Input, PasswordInput } from "@/components/ui/FormField";
import { ApiError, handleApiError } from "@/lib/api";
import { passwordResetApi } from "@/lib/services";

const RESEND_SECONDS = 60;

const emailSchema = z.object({ email: z.email("Enter a valid email") });
type EmailForm = z.infer<typeof emailSchema>;

const resetSchema = z
  .object({
    code: z.string().trim().regex(/^\d{6}$/, "Enter the 6-digit code from the email"),
    newPassword: z.string().min(8, "At least 8 characters").max(100),
    confirm: z.string(),
  })
  .refine((v) => v.newPassword === v.confirm, { message: "Passwords don't match", path: ["confirm"] });
type ResetForm = z.infer<typeof resetSchema>;

/** Forgot password for admins and technicians: email a 6-digit code, then set a new password with it. */
export default function ForgotPasswordPage() {
  const [email, setEmail] = useState<string | null>(null);
  const [cooldown, setCooldown] = useState(0);

  useEffect(() => {
    if (cooldown <= 0) return;
    const t = setTimeout(() => setCooldown((c) => c - 1), 1000);
    return () => clearTimeout(t);
  }, [cooldown]);

  const sendCode = async (address: string) => {
    const res = await passwordResetApi.requestCode(address);
    toast.success(res.message, { duration: 6000 });
    setEmail(address);
    setCooldown(RESEND_SECONDS);
  };

  return (
    <div className="flex min-h-dvh items-center justify-center bg-bg p-4">
      <div className="w-full max-w-[400px]">
        <div className="mb-5 flex items-center justify-center gap-2 text-2xl font-bold text-sidebar">
          <span className="flex size-10 items-center justify-center rounded-xl bg-sidebar">
            <Zap size={20} className="fill-[#ffd84d] text-[#ffd84d]" />
          </span>
          ServProWare
        </div>
        {email ? (
          <ResetStep email={email} cooldown={cooldown} onResend={() => sendCode(email)} onChangeEmail={() => setEmail(null)} />
        ) : (
          <EmailStep onSend={sendCode} />
        )}
        <p className="mt-3 text-center text-[13px]">
          <Link href="/login" className="font-medium text-brand hover:underline">
            ← Back to sign in
          </Link>
        </p>
      </div>
    </div>
  );
}

function EmailStep({ onSend }: { onSend: (email: string) => Promise<void> }) {
  const {
    register,
    handleSubmit,
    setError,
    formState: { errors, isSubmitting },
  } = useForm<EmailForm>({ resolver: zodResolver(emailSchema), defaultValues: { email: "" } });

  const onSubmit = async (v: EmailForm) => {
    try {
      await onSend(v.email.trim());
    } catch (err) {
      // e.g. "Email is not registered..!" — show it under the field and as a toast
      if (err instanceof ApiError && err.fieldErrors?.email) {
        setError("email", { type: "server", message: err.fieldErrors.email });
        toast.error(err.message);
      } else {
        handleApiError(err, setError);
      }
    }
  };

  return (
    <form className="card p-6!" onSubmit={handleSubmit(onSubmit)} noValidate>
      <h1 className="mb-1 text-lg font-semibold">Forgot password</h1>
      <p className="mb-5 text-[13px] text-muted">
        Enter the email you sign in with. We&apos;ll email you a 6-digit code to set a new password.
      </p>
      <FormField label="Email" error={errors.email?.message} htmlFor="email" className="mb-5">
        <Input id="email" type="email" autoComplete="username" autoFocus aria-invalid={!!errors.email} {...register("email")} />
      </FormField>
      <Button type="submit" className="w-full" loading={isSubmitting}>
        Send code
      </Button>
      <p className="mt-3 text-[11.5px] text-muted">
        For admins and technicians. The Super Admin&apos;s password is changed on the server.
      </p>
    </form>
  );
}

function ResetStep({
  email,
  cooldown,
  onResend,
  onChangeEmail,
}: {
  email: string;
  cooldown: number;
  onResend: () => Promise<void>;
  onChangeEmail: () => void;
}) {
  const router = useRouter();
  const [resending, setResending] = useState(false);
  const {
    register,
    handleSubmit,
    setError,
    formState: { errors, isSubmitting },
  } = useForm<ResetForm>({ resolver: zodResolver(resetSchema), defaultValues: { code: "", newPassword: "", confirm: "" } });

  const onSubmit = async (v: ResetForm) => {
    try {
      await passwordResetApi.reset(email, v.code.trim(), v.newPassword);
      toast.success("Password changed — sign in with your new password");
      router.replace("/login");
    } catch (err) {
      handleApiError(err, setError);
    }
  };

  const resend = async () => {
    setResending(true);
    try {
      await onResend();
    } catch (err) {
      handleApiError(err);
    } finally {
      setResending(false);
    }
  };

  return (
    <form className="card p-6!" onSubmit={handleSubmit(onSubmit)} noValidate>
      <h1 className="mb-1 text-lg font-semibold">Enter the code</h1>
      <p className="mb-5 text-[13px] text-muted">
        We sent a 6-digit code to <b className="text-ink">{email}</b>. It expires in 10 minutes.{" "}
        <button type="button" className="font-medium text-brand hover:underline" onClick={onChangeEmail}>
          Change email
        </button>
      </p>
      <FormField label="6-digit code" error={errors.code?.message} htmlFor="code" className="mb-3">
        <Input
          id="code"
          inputMode="numeric"
          autoComplete="one-time-code"
          maxLength={6}
          autoFocus
          placeholder="••••••"
          className="text-center text-lg tracking-[0.5em]"
          aria-invalid={!!errors.code}
          {...register("code")}
        />
      </FormField>
      <FormField label="New password" error={errors.newPassword?.message} hint="At least 8 characters" htmlFor="np" className="mb-3">
        <PasswordInput id="np" autoComplete="new-password" aria-invalid={!!errors.newPassword} {...register("newPassword")} />
      </FormField>
      <FormField label="Confirm new password" error={errors.confirm?.message} htmlFor="cp" className="mb-5">
        <PasswordInput id="cp" autoComplete="new-password" aria-invalid={!!errors.confirm} {...register("confirm")} />
      </FormField>
      <Button type="submit" className="w-full" loading={isSubmitting}>
        Set new password
      </Button>
      <div className="mt-3 text-center text-[12.5px] text-muted">
        Didn&apos;t get it?{" "}
        {cooldown > 0 ? (
          <span>Resend in {cooldown}s</span>
        ) : (
          <button type="button" className="font-medium text-brand hover:underline disabled:opacity-60" disabled={resending} onClick={resend}>
            {resending ? "Sending…" : "Resend code"}
          </button>
        )}
      </div>
    </form>
  );
}
