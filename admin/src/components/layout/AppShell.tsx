"use client";

import { useState, type ReactNode } from "react";
import Link from "next/link";
import { usePathname, useRouter } from "next/navigation";
import { LogOut, Menu, X } from "lucide-react";
import { PageHeader } from "@/components/ui/PageHeader";
import { logout } from "@/lib/api";
import { NAV_GROUPS, SUPER_NAV_GROUPS, TECH_NAV_GROUPS, isActive, navItemFor, type NavGroup } from "@/lib/nav";
import { cn } from "@/lib/utils";
import { useAuthStore } from "@/store/auth";
import type { Role } from "@/types/api";

const ROLE_LABEL: Record<Role, string> = { SUPER_ADMIN: "Super Admin", ADMIN: "Admin", TECHNICIAN: "Technician" };

function Sidebar({ groups, panelLabel, onNavigate }: { groups: NavGroup[]; panelLabel: string; onNavigate?: () => void }) {
  const pathname = usePathname();
  const user = useAuthStore((s) => s.user);
  return (
    <div className="flex h-full w-[216px] min-w-[216px] flex-col bg-sidebar text-[#cfd8e3]">
      <div className="border-b border-white/[.08] px-4 pb-3.5 pt-[18px]">
        <div className="flex items-center gap-1.5 text-[17px] font-bold tracking-[.3px] text-white">
          ⚡ ServProWare
        </div>
        <span className="mt-0.5 block text-[11px] text-[#7d93a8]">{panelLabel}</span>
      </div>
      <nav className="flex-1 overflow-y-auto px-2 py-2.5">
        {groups.map((g) => (
          <div key={g.label || "main"}>
            {g.label && (
              <div className="px-2.5 pb-1 pt-3 text-[10px] uppercase tracking-[1px] text-[#5f7488]">{g.label}</div>
            )}
            {g.items.filter((item) => !item.hidden).map((item) => {
              const active = isActive(item.href, pathname);
              return (
                <Link
                  key={item.href}
                  href={item.href}
                  onClick={onNavigate}
                  className={cn(
                    "mb-0.5 flex w-full items-center gap-[9px] rounded-lg px-2.5 py-[9px] text-[13.5px] text-sidebar-ink no-underline transition-colors",
                    active ? "bg-brand font-semibold text-white" : "hover:bg-white/[.07] hover:text-white",
                  )}
                >
                  <span className="w-[18px] shrink-0 text-center" aria-hidden>
                    {item.icon}
                  </span>
                  {item.label}
                </Link>
              );
            })}
          </div>
        ))}
      </nav>
      <div className="border-t border-white/[.08] p-3">
        <div className="text-[10.5px] uppercase tracking-[.8px] text-[#7d93a8]">Signed in as</div>
        <div className="mt-1 truncate text-[13px] text-white">{user?.name}</div>
        <div className="truncate text-[11.5px] text-[#7d93a8]">{user?.email}</div>
      </div>
    </div>
  );
}

export function AppShell({ role, children }: { role: Role; children: ReactNode }) {
  const groups = role === "SUPER_ADMIN" ? SUPER_NAV_GROUPS : role === "ADMIN" ? NAV_GROUPS : TECH_NAV_GROUPS;
  const pathname = usePathname();
  const router = useRouter();
  const user = useAuthStore((s) => s.user);
  const [drawer, setDrawer] = useState(false);
  const item = navItemFor(pathname);
  const panelLabel = ROLE_LABEL[role];

  const doLogout = async () => {
    await logout();
    router.replace("/login");
  };

  return (
    <div className="flex h-dvh overflow-hidden">
      {/* Desktop sidebar */}
      <aside className="hidden h-full lg:block">
        <Sidebar groups={groups} panelLabel={panelLabel} />
      </aside>

      {/* Mobile drawer */}
      {drawer && (
        <div className="fixed inset-0 z-[1500] flex lg:hidden">
          <div className="h-full shadow-2xl">
            <Sidebar groups={groups} panelLabel={panelLabel} onNavigate={() => setDrawer(false)} />
          </div>
          <button
            type="button"
            aria-label="Close menu"
            className="flex-1 bg-black/40"
            onClick={() => setDrawer(false)}
          >
            <X className="ml-auto mr-4 mt-4 text-white" />
          </button>
        </div>
      )}

      <div className="flex h-full min-w-0 flex-1 flex-col overflow-hidden">
        <header className="flex items-center gap-3.5 border-b border-line bg-card px-4 py-3 sm:px-[22px]">
          <button
            type="button"
            aria-label="Open menu"
            className="btn btn-sec btn-sm lg:hidden"
            onClick={() => setDrawer(true)}
          >
            <Menu size={16} />
          </button>
          <PageHeader title={item?.title ?? "ServProWare"} subtitle={item?.subtitle} />
          <div className="flex-1" />
          <span className="hidden rounded-full bg-brand-light px-3 py-[5px] text-xs font-semibold text-brand-dark sm:inline-block">
            {ROLE_LABEL[role]} · {user?.name ?? ""}
          </span>
          <button type="button" className="btn btn-sec btn-sm" onClick={doLogout} title="Log out">
            <LogOut size={14} /> <span className="hidden sm:inline">Logout</span>
          </button>
        </header>
        <main className="flex-1 overflow-y-auto px-3 py-4 sm:px-[22px] sm:py-5">{children}</main>
      </div>
    </div>
  );
}
