"use client";

import Image from "next/image";
import Link from "next/link";
import { usePathname } from "next/navigation";
import { useQuery } from "@tanstack/react-query";
import { useState } from "react";
import {
  LayoutDashboard,
  Users,
  Inbox,
  Columns3,
  FileDown,
  MonitorPlay,
  ChevronDown,
  ChevronsLeft,
  ChevronsRight,
} from "lucide-react";

import { toast } from "sonner";
import { cn } from "@/lib/utils";
import { useCurrentUser } from "@/hooks/use-current-user";
import { listMySections } from "@/lib/api/me";
import { downloadMyGroupPdf } from "@/lib/api/exports";
import { extractErrorMessage } from "@/lib/api-client";
import { StatusDot } from "./status-dot";

export function Sidebar() {
  const { user } = useCurrentUser();
  const pathname = usePathname();
  const [sectionsOpen, setSectionsOpen] = useState(true);
  const [collapsed, setCollapsed] = useState(false);

  const isAdmin = user?.role === "ADMIN";

  const { data: sections } = useQuery({
    queryKey: ["me", "sections", "nav"],
    queryFn: listMySections,
    enabled: !isAdmin,
    refetchInterval: 30_000,
  });

  return (
    <aside
      className={cn(
        "shrink-0 h-screen sticky top-0 flex flex-col bg-gradient-to-b from-primary-800 to-primary-900 text-white/80 overflow-y-auto overflow-x-hidden scrollbar-thin transition-[width] duration-200",
        collapsed ? "w-[72px]" : "w-64"
      )}
    >
      <div className={cn("p-5 flex items-center gap-3", collapsed && "justify-center px-3")}>
        <div className="bg-white rounded-lg p-2 shrink-0">
          <Image src="/logo-senico.png" alt="SENICO" width={514} height={98} className="h-8 w-8 object-contain" />
        </div>
        {!collapsed && (
          <div className="min-w-0">
            <p className="text-white text-sm font-semibold truncate">SENICO</p>
            <p className="text-[11px] text-white/50 truncate">Plan Stratégique</p>
          </div>
        )}
      </div>

      <button
        type="button"
        onClick={() => setCollapsed((c) => !c)}
        title={collapsed ? "Déplier le menu" : "Réduire le menu"}
        className={cn(
          "mx-3 mb-2 flex items-center gap-2 rounded-lg py-1.5 text-[11px] font-medium text-white/50 hover:text-white/80 hover:bg-white/5 transition-colors duration-150",
          collapsed ? "justify-center px-0" : "justify-start px-3"
        )}
      >
        {collapsed ? (
          <ChevronsRight className="h-4 w-4 shrink-0" strokeWidth={2.5} />
        ) : (
          <>
            <ChevronsLeft className="h-4 w-4 shrink-0" strokeWidth={2.5} />
            Réduire le menu
          </>
        )}
      </button>

      <nav className="flex-1 px-3 py-2 space-y-0.5">
        {isAdmin ? (
          <>
            <NavItem href="/admin" icon={LayoutDashboard} label="Tableau de bord" active={pathname === "/admin"} collapsed={collapsed} />
            <NavItem
              href="/admin/submissions"
              icon={Inbox}
              label="Soumissions"
              active={pathname.startsWith("/admin/submissions")}
              collapsed={collapsed}
            />
            <NavItem
              href="/admin/groups"
              icon={Users}
              label="Groupes de travail"
              active={pathname.startsWith("/admin/groups")}
              collapsed={collapsed}
            />
            <NavItem
              href="/admin/compare"
              icon={Columns3}
              label="Vue comparative"
              active={pathname.startsWith("/admin/compare")}
              collapsed={collapsed}
            />
            <Link
              href="/projection"
              target="_blank"
              rel="noopener noreferrer"
              title="Vue projecteur"
              className={cn(
                "flex items-center gap-2.5 rounded-lg px-3 py-2 text-[13px] font-medium text-white/75 hover:bg-white/5 transition-colors duration-150",
                collapsed && "justify-center px-2"
              )}
            >
              <MonitorPlay className="h-[18px] w-[18px] shrink-0" strokeWidth={1.75} />
              {!collapsed && <span className="truncate">Vue projecteur</span>}
            </Link>
          </>
        ) : (
          <>
            <NavItem
              href="/dashboard"
              icon={LayoutDashboard}
              label="Tableau de bord"
              active={pathname === "/dashboard"}
              collapsed={collapsed}
            />
            {!collapsed && (
              <button
                type="button"
                onClick={() => setSectionsOpen((open) => !open)}
                className="flex w-full items-center justify-between pt-3 pb-1 px-3 text-[11px] font-semibold uppercase tracking-wider text-white/40 hover:text-white/60 transition-colors duration-150"
              >
                <span>Sections du canevas</span>
                <ChevronDown
                  className={cn(
                    "h-4 w-4 shrink-0 text-white/70 transition-transform duration-150",
                    sectionsOpen ? "" : "-rotate-90"
                  )}
                  strokeWidth={2.5}
                />
              </button>
            )}
            {(collapsed || sectionsOpen) &&
              (sections ?? []).map((s) => (
                <Link
                  key={s.code}
                  href={`/sections/${s.code}`}
                  title={`${s.code} — ${s.title}`}
                  className={cn(
                    "flex items-center gap-2 rounded-lg text-[13px] transition-colors duration-150",
                    collapsed ? "justify-center px-2 py-2" : "justify-between px-3 py-2",
                    pathname === `/sections/${s.code}`
                      ? "bg-white/10 text-white border-l-[3px] border-l-[#7FC297] pl-[9px]"
                      : "hover:bg-white/5 text-white/75"
                  )}
                >
                  {collapsed ? (
                    <span className="text-[11px] font-semibold">{s.code}</span>
                  ) : (
                    <>
                      <span className="truncate">
                        <span className="text-white/40 mr-1.5">{s.code}</span>
                        {s.title}
                      </span>
                      <StatusDot status={s.status} />
                    </>
                  )}
                </Link>
              ))}
          </>
        )}
      </nav>

      <div className="p-3 border-t border-white/10 space-y-0.5">
        {!isAdmin && (
          <button
            type="button"
            onClick={() =>
              downloadMyGroupPdf().catch((error) => toast.error(extractErrorMessage(error, "Échec de l'export PDF")))
            }
            title="Exporter en PDF"
            className={cn(
              "flex w-full items-center gap-2.5 rounded-lg px-3 py-2 text-[13px] font-medium text-white/75 hover:bg-white/5 transition-colors duration-150",
              collapsed && "justify-center px-2"
            )}
          >
            <FileDown className="h-[18px] w-[18px] shrink-0" strokeWidth={1.75} />
            {!collapsed && "Exporter en PDF"}
          </button>
        )}
      </div>
    </aside>
  );
}

function NavItem({
  href,
  icon: Icon,
  label,
  active,
  collapsed,
}: {
  href: string;
  icon: React.ElementType;
  label: string;
  active: boolean;
  collapsed?: boolean;
}) {
  return (
    <Link
      href={href}
      title={label}
      className={cn(
        "flex items-center gap-2.5 rounded-lg px-3 py-2 text-[13px] font-medium transition-colors duration-150",
        collapsed && "justify-center px-2",
        active
          ? "bg-white/10 text-white border-l-[3px] border-l-[#7FC297] pl-[9px]"
          : "hover:bg-white/5 text-white/75"
      )}
    >
      <Icon className="h-[18px] w-[18px] shrink-0" strokeWidth={1.75} />
      {!collapsed && <span className="truncate">{label}</span>}
    </Link>
  );
}
