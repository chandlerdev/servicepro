"use client";

import { useEffect, type ReactNode } from "react";
import { useRouter } from "next/navigation";
import { PageSpinner } from "@/components/ui/Spinner";
import { useHydrated } from "@/hooks/useHydrated";
import { homeFor } from "@/lib/nav";
import { authApi } from "@/lib/services";
import { useAuthStore } from "@/store/auth";
import type { Role } from "@/types/api";

/**
 * Client-side guard: tokens live in localStorage, so auth is decided after hydration.
 * - no session → /login
 * - signed in with a different role → that role's home ("/dashboard" for admins, "/tech" for technicians)
 * - matching role → children
 */
export function AuthGuard({ role, children }: { role: Role; children: ReactNode }) {
  const hydrated = useHydrated();
  const router = useRouter();
  const token = useAuthStore((s) => s.accessToken);
  const user = useAuthStore((s) => s.user);
  const setUser = useAuthStore((s) => s.setUser);

  useEffect(() => {
    if (hydrated && !token) router.replace("/login");
  }, [hydrated, token, router]);

  useEffect(() => {
    if (user && user.role !== role) router.replace(homeFor(user.role));
  }, [user, role, router]);

  // Refresh the profile once per session (role/name may have changed).
  useEffect(() => {
    if (!hydrated || !token) return;
    authApi.me().then(setUser, () => {
      /* 401 handled by interceptor; network errors ignored */
    });
    // only on first mount with a token
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [hydrated]);

  if (!hydrated || !token || !user || user.role !== role) {
    return <PageSpinner label="Checking your session…" />;
  }
  return <>{children}</>;
}
