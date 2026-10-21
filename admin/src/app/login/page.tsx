"use client";

import { useEffect } from "react";
import Link from "next/link";
import { useRouter } from "next/navigation";
import { useForm } from "react-hook-form";
import { zodResolver } from "@hookform/resolvers/zod";
import { z } from "zod";
import { Zap } from "lucide-react";
import toast from "react-hot-toast";
import { Button } from "@/components/ui/Button";
import { FormField, Input, PasswordInput } from "@/components/ui/FormField";
import { useHydrated } from "@/hooks/useHydrated";
import { ApiError, handleApiError, login } from "@/lib/api";
import { homeFor } from "@/lib/nav";
import { useAuthStore } from "@/store/auth";

const schema = z.object({
  email: z.email("Enter a valid email"),
  password: z.string().min(1, "Password is required"),
});
type LoginForm = z.infer<typeof schema>;

export default function LoginPage() {
  const router = useRouter();
  const hydrated = useHydrated();
  const token = useAuthStore((s) => s.accessToken);
  const user = useAuthStore((s) => s.user);
  const setSession = useAuthStore((s) => s.setSession);

  const {
    register,
    handleSubmit,
    setError,
    formState: { errors, isSubmitting },
  } = useForm<LoginForm>({ resolver: zodResolver(schema), defaultValues: { email: "", password: "" } });

  useEffect(() => {
    if (hydrated && token && user) router.replace(homeFor(user.role));
  }, [hydrated, token, user, router]);

  const onSubmit = async (values: LoginForm) => {
    try {
      const res = await login(values.email, values.password);
      setSession(res);
      toast.success(`Welcome, ${res.user.name}`);
      router.replace(homeFor(res.user.role));
    } catch (err) {
      // Login errors are 401s, which handleApiError deliberately ignores (it treats 401 as "session expired"),
      // so show them here: under the wrong field ("No account found…" / "Incorrect password") and as a toast.
      if (err instanceof ApiError && err.status === 401) {
        const fields = err.fieldErrors ?? {};
        if (fields.email) setError("email", { type: "server", message: fields.email });
        if (fields.password) setError("password", { type: "server", message: fields.password });
        toast.error(err.message);
      } else {
        handleApiError(err, setError);
      }
    }
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
        <form className="card p-6!" onSubmit={handleSubmit(onSubmit)} noValidate>
          <h1 className="mb-1 text-lg font-semibold">Sign in</h1>
          <p className="mb-5 text-[13px] text-muted">Schedule, route and notify — all in one place.</p>
          <FormField label="Email" error={errors.email?.message} htmlFor="email" className="mb-3">
            <Input
              id="email"
              type="email"
              autoComplete="username"
              autoFocus
              aria-invalid={!!errors.email}
              {...register("email")}
            />
          </FormField>
          <FormField label="Password" error={errors.password?.message} htmlFor="password" className="mb-1">
            <PasswordInput
              id="password"
              autoComplete="current-password"
              aria-invalid={!!errors.password}
              {...register("password")}
            />
          </FormField>
          <div className="mb-5 text-right">
            <Link href="/forgot-password" className="text-[12.5px] font-medium text-brand hover:underline">
              Forgot password?
            </Link>
          </div>
          <Button type="submit" className="w-full" loading={isSubmitting}>
            Sign in
          </Button>
        </form>
        <p className="mt-3 text-center text-[11.5px] text-[#8a96a8]">
          Super Admin, admins and technicians sign in here. Technicians can also use the ServProWare mobile app.
        </p>
      </div>
    </div>
  );
}
