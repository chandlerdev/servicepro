import type { ReactNode } from "react";
import { AppShell } from "@/components/layout/AppShell";
import { AuthGuard } from "@/components/layout/AuthGuard";

export default function AdminLayout({ children }: { children: ReactNode }) {
  return (
    <AuthGuard role="ADMIN">
      <AppShell role="ADMIN">
        {children}
      </AppShell>
    </AuthGuard>
  );
}
