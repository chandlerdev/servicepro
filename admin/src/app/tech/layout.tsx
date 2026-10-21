import type { ReactNode } from "react";
import { AppShell } from "@/components/layout/AppShell";
import { AuthGuard } from "@/components/layout/AuthGuard";

export default function TechLayout({ children }: { children: ReactNode }) {
  return (
    <AuthGuard role="TECHNICIAN">
      <AppShell role="TECHNICIAN">
        {children}
      </AppShell>
    </AuthGuard>
  );
}
