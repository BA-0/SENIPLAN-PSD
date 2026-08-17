"use client";

import Image from "next/image";
import Link from "next/link";
import { usePathname } from "next/navigation";
import { useQuery } from "@tanstack/react-query";
import { LayoutDashboard, Users, Columns3, FileDown, MonitorPlay } from "lucide-react";

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

  const isAdmin = user?.role === "ADMIN";

  const { data: sections } = useQuery({
    queryKey: ["me", "sections", "nav"],
    queryFn: listMySections,
    enabled: !isAdmin,
    refetchInterval: 30_000,
  });

  return (
    <aside className="w-64 shrink-0 h-screen sticky top-0 flex flex-col bg-gradient-to-b from-primary-800 to-primary-900 text-white/80 overflow-y-auto scrollbar-thin">
      <div className="p-5 flex items-center gap-3">
        <div className="bg-white rounded-lg p-2 shrink-0">
          <Image src="/logo-senico.png" alt="SENICO" width={32} height={32} className="h-8 w-8 object-contain" />
        </div>
        <div className="min-w-0">
          <p className="text-white text-sm font-semibold truncate">SENICO</p>
          <p className="text-[11px] text-white/50 truncate">Diagnostic Stratégique</p>
        </div>
      </div>

      <nav className="flex-1 px-3 py-2 space-y-0.5">
        {isAdmin ? (
          <>
            <NavItem href="/admin" icon={LayoutDashboard} label="Tableau de bord" active={pathname === "/admin"} />
            <NavItem href="/admin/groups" icon={Users} label="Groupes de travail" active={pathname.startsWith("/admin/groups")} />
            <NavItem href="/admin/compare" icon={Columns3} label="Vue comparative" active={pathname.startsWith("/admin/compare")} />
            <Link
              href="/projection"
              target="_blank"
              rel="noopener noreferrer"
              className="flex items-center gap-2.5 rounded-lg px-3 py-2 text-[13px] font-medium text-white/75 hover:bg-white/5 transition-colors duration-150"
            >
              <MonitorPlay className="h-[18px] w-[18px] shrink-0" strokeWidth={1.75} />
              <span className="truncate">Vue projecteur</span>
            </Link>
          </>
        ) : (
          <>
            <NavItem href="/dashboard" icon={LayoutDashboard} label="Tableau de bord" active={pathname === "/dashboard"} />
            <div className="pt-3 pb-1 px-3 text-[11px] font-semibold uppercase tracking-wider text-white/40">
              Sections du canevas
            </div>
            {(sections ?? []).map((s) => (
              <Link
                key={s.code}
                href={`/sections/${s.code}`}
                className={cn(
                  "flex items-center justify-between gap-2 rounded-lg px-3 py-2 text-[13px] transition-colors duration-150",
                  pathname === `/sections/${s.code}`
                    ? "bg-white/10 text-white border-l-[3px] border-l-[#7FC297] pl-[9px]"
                    : "hover:bg-white/5 text-white/75"
                )}
              >
                <span className="truncate">
                  <span className="text-white/40 mr-1.5">{s.code}</span>
                  {s.title}
                </span>
                <StatusDot status={s.status} />
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
            className="flex w-full items-center gap-2.5 rounded-lg px-3 py-2 text-[13px] font-medium text-white/75 hover:bg-white/5 transition-colors duration-150"
          >
            <FileDown className="h-[18px] w-[18px] shrink-0" strokeWidth={1.75} />
            Exporter en PDF
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
}: {
  href: string;
  icon: React.ElementType;
  label: string;
  active: boolean;
}) {
  return (
    <Link
      href={href}
      className={cn(
        "flex items-center gap-2.5 rounded-lg px-3 py-2 text-[13px] font-medium transition-colors duration-150",
        active
          ? "bg-white/10 text-white border-l-[3px] border-l-[#7FC297] pl-[9px]"
          : "hover:bg-white/5 text-white/75"
      )}
    >
      <Icon className="h-[18px] w-[18px] shrink-0" strokeWidth={1.75} />
      <span className="truncate">{label}</span>
    </Link>
  );
}
