import type { ReactNode } from "react";
import { AppShell } from "@/components/layout/AppShell";
import { AuthGuard } from "@/components/layout/AuthGuard";

export default function SuperAdminLayout({ children }: { children: ReactNode }) {
  return (
    <AuthGuard role="SUPER_ADMIN">
      <AppShell role="SUPER_ADMIN">{children}</AppShell>
    </AuthGuard>
  );
}
