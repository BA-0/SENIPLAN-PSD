"use client";

import Image from "next/image";
import Link from "next/link";
import { usePathname } from "next/navigation";
import { useQuery } from "@tanstack/react-query";
import { useEffect, useMemo, useState } from "react";
import {
  LayoutDashboard,
  Users,
  Inbox,
  Columns3,
  FileDown,
  Files,
  FileText,
  MonitorPlay,
  Radio,
  ClipboardList,
  ChevronDown,
  ChevronsLeft,
  ChevronsRight,
  X,
} from "lucide-react";

import { toast } from "sonner";
import { cn } from "@/lib/utils";
import { useCurrentUser } from "@/hooks/use-current-user";
import { listMySections } from "@/lib/api/me";
import { downloadMyGroupPdf } from "@/lib/api/exports";
import { extractErrorMessage } from "@/lib/api-client";
import { useMobileNavStore } from "@/store/mobile-nav-store";
import { StatusDot } from "./status-dot";
import { canPilot } from "@/lib/roles";
import { countValidated, findPartForCode, groupSectionsByPart } from "@/lib/section-groups";

export function Sidebar() {
  const { user } = useCurrentUser();
  const pathname = usePathname();
  // Les parties du canevas se replient une a une ; celle de la section ouverte
  // est depliee par defaut, tant que l'utilisateur n'a pas choisi lui-meme.
  const [openParts, setOpenParts] = useState<Record<string, boolean>>({});
  const [collapsed, setCollapsed] = useState(false);
  const mobileOpen = useMobileNavStore((s) => s.open);
  const setMobileOpen = useMobileNavStore((s) => s.setOpen);

  // Le DG partage l'espace de pilotage de l'admin ; seules les actions
  // d'administration technique, dans les pages elles-memes, lui sont fermees.
  const isAdmin = canPilot(user?.role);
  // Le repli icone n'a de sens qu'en sidebar dockee (lg+) : dans le tiroir
  // mobile, toujours ouvert, on garde les libelles lisibles.
  const showCollapsed = collapsed && !mobileOpen;

  // Sous `lg` la sidebar est un tiroir : on la referme a chaque navigation
  // plutot que de laisser l'utilisateur la fermer a la main a chaque fois.
  useEffect(() => {
    setMobileOpen(false);
  }, [pathname, setMobileOpen]);

  const { data: sections } = useQuery({
    queryKey: ["me", "sections", "nav"],
    queryFn: listMySections,
    enabled: !isAdmin,
    refetchInterval: 30_000,
  });

  const sectionParts = useMemo(() => groupSectionsByPart(sections ?? []), [sections]);
  const activeSectionCode = pathname.startsWith("/sections/") ? pathname.slice("/sections/".length) : null;
  const activePart = activeSectionCode ? findPartForCode(activeSectionCode) : null;

  return (
    <>
      {/* Fond assombri derriere le tiroir mobile : au clic, referme comme le
          reste de l'app (dialogues, menus) le fait deja. */}
      {mobileOpen && (
        <div
          aria-hidden
          onClick={() => setMobileOpen(false)}
          className="fixed inset-0 z-30 bg-black/50 lg:hidden"
        />
      )}

      <aside
        className={cn(
          "flex flex-col bg-gradient-to-b from-primary-800/90 to-primary-900/90 backdrop-blur-md text-white/80 overflow-y-auto overflow-x-hidden scrollbar-thin border-r border-white/10",
          "fixed inset-y-0 left-0 z-40 w-64 -translate-x-full transition-transform duration-200",
          "lg:sticky lg:top-0 lg:z-auto lg:h-screen lg:shrink-0 lg:translate-x-0 lg:transition-[width]",
          mobileOpen && "translate-x-0",
          collapsed ? "lg:w-[72px]" : "lg:w-64"
        )}
      >
        <div
          className={cn(
            "flex items-center gap-3 px-4 pt-5 pb-4",
            collapsed && "lg:flex-col lg:gap-2 lg:px-3"
          )}
        >
          <div className="shrink-0 rounded-xl bg-white p-2 ring-1 ring-white/20 shadow-sm">
            <Image src="/logo-senico.png" alt="SENICO" width={514} height={98} className="h-8 w-8 object-contain" />
          </div>
          {(!collapsed || mobileOpen) && (
            <div className="min-w-0 flex-1">
              <p className="truncate text-sm font-semibold tracking-tight text-white">SENICO</p>
              <p className="truncate text-[11px] text-white/50">Plan Stratégique</p>
            </div>
          )}
          {/* Replier n'est pas une destination : l'action se range en icone au bord de
              l'en-tete, la ou les applications de travail la placent, plutot que de
              s'etaler sur une bande pleine largeur au-dessus de la navigation. */}
          <button
            type="button"
            onClick={() => setCollapsed((c) => !c)}
            title={collapsed ? "Déplier le menu" : "Réduire le menu"}
            aria-label={collapsed ? "Déplier le menu" : "Réduire le menu"}
            className="hidden shrink-0 rounded-lg p-1.5 text-white/50 transition-colors duration-150 hover:bg-white/10 hover:text-white lg:block"
          >
            {collapsed ? (
              <ChevronsRight className="h-4 w-4" strokeWidth={2.5} />
            ) : (
              <ChevronsLeft className="h-4 w-4" strokeWidth={2.5} />
            )}
          </button>
          <button
            type="button"
            onClick={() => setMobileOpen(false)}
            title="Fermer le menu"
            aria-label="Fermer le menu"
            className="shrink-0 rounded-lg p-1.5 text-white/60 transition-colors duration-150 hover:bg-white/10 hover:text-white lg:hidden"
          >
            <X className="h-4 w-4" strokeWidth={2.5} />
          </button>
        </div>

        <div className={cn("mx-4 mb-2 h-px bg-white/10", collapsed && "lg:mx-3")} />

        <nav className="flex-1 space-y-0.5 px-3 pb-3">
        {isAdmin ? (
          <>
            <NavItem href="/admin" icon={LayoutDashboard} label="Tableau de bord" active={pathname === "/admin"} collapsed={showCollapsed} />
            <NavItem
              href="/admin/submissions"
              icon={Inbox}
              label="Soumissions"
              active={pathname.startsWith("/admin/submissions")}
              collapsed={showCollapsed}
            />
            <NavItem
              href="/admin/groups"
              icon={Users}
              label="Groupes de travail"
              active={pathname.startsWith("/admin/groups")}
              collapsed={showCollapsed}
            />
            <NavItem
              href="/admin/compare"
              icon={Columns3}
              label="Vue comparative"
              active={pathname.startsWith("/admin/compare")}
              collapsed={showCollapsed}
            />
            <NavItem
              href="/admin/consolidation"
              icon={Files}
              label="Document de consolidation"
              active={pathname.startsWith("/admin/consolidation")}
              collapsed={showCollapsed}
            />
            <NavItem
              href="/admin/live-consolidation"
              icon={Radio}
              label="Consolidation en direct"
              active={pathname.startsWith("/admin/live-consolidation")}
              collapsed={showCollapsed}
            />
            <NavItem
              href="/admin/synthesis"
              icon={ClipboardList}
              label="Note de synthèse"
              active={pathname.startsWith("/admin/synthesis")}
              collapsed={showCollapsed}
            />
            <NavItem
              href="/admin/psd-final"
              icon={FileText}
              label="Plan Stratégique de SENICO"
              active={pathname.startsWith("/admin/psd-final")}
              collapsed={showCollapsed}
            />
            <Link
              href="/projection"
              target="_blank"
              rel="noopener noreferrer"
              title="Vue projecteur"
              className={cn(
                "flex items-center gap-2.5 rounded-lg px-3 py-2 text-[13px] font-medium text-white/75 hover:bg-white/5 transition-colors duration-150",
                showCollapsed && "justify-center px-2"
              )}
            >
              <MonitorPlay className="h-[18px] w-[18px] shrink-0" strokeWidth={1.75} />
              {!showCollapsed && <span className="truncate">Vue projecteur</span>}
            </Link>
          </>
        ) : (
          <>
            <NavItem
              href="/dashboard"
              icon={LayoutDashboard}
              label="Tableau de bord"
              active={pathname === "/dashboard"}
              collapsed={showCollapsed}
            />
            {!showCollapsed && (
              <p className="px-3 pt-4 pb-1.5 text-[10px] font-semibold uppercase tracking-[0.12em] text-white/35">
                Sections du canevas
              </p>
            )}
            {sectionParts.map(({ part, sections: partSections }, partIndex) => {
              const open = openParts[part.id] ?? part.id === activePart?.id;
              return (
                <div
                  key={part.id}
                  className={cn(
                    "space-y-0.5",
                    partIndex > 0 && !showCollapsed && "mt-0.5",
                    showCollapsed && partIndex > 0 && "mt-1 border-t border-white/10 pt-1"
                  )}
                >
                  {!showCollapsed && (() => {
                    const valides = countValidated(partSections);
                    const complete = valides === partSections.length && partSections.length > 0;
                    return (
                      <button
                        type="button"
                        onClick={() => setOpenParts((state) => ({ ...state, [part.id]: !open }))}
                        title={part.numeral ? `${part.numeral} — ${part.title}` : part.title}
                        aria-expanded={open}
                        className={cn(
                          "flex w-full items-start gap-2 rounded-lg px-3 py-2 text-left text-[12px] font-semibold transition-colors duration-150",
                          open ? "text-white/85" : "text-white/60",
                          "hover:bg-white/5 hover:text-white"
                        )}
                      >
                        <ChevronDown
                          className={cn(
                            "mt-[3px] h-3.5 w-3.5 shrink-0 transition-transform duration-150",
                            open ? "" : "-rotate-90"
                          )}
                          strokeWidth={2.5}
                        />
                        {/* Le titre se replie sur deux lignes plutot que de se couper :
                            « Programmation et bud... » ne dit pas de quoi traite la partie. */}
                        <span className="min-w-0 flex-1 leading-snug">
                          {part.numeral && <span className="mr-1.5 text-white/40">{part.numeral}</span>}
                          {part.title}
                        </span>
                        {/* Avancement de la partie repliee, pour ne pas avoir a l'ouvrir pour le
                            voir. En pastille, il cesse de concurrencer le titre a poids egal. */}
                        <span
                          className={cn(
                            "mt-[1px] shrink-0 rounded-md px-1.5 py-0.5 text-[10px] font-semibold tabular-nums",
                            complete ? "bg-[#7FC297]/20 text-[#A7D9B9]" : "bg-white/10 text-white/55"
                          )}
                        >
                          {valides}/{partSections.length}
                        </span>
                      </button>
                    );
                  })()}
                  {(showCollapsed || open) &&
                    partSections.map((s) => (
                      <Link
                        key={s.code}
                        href={`/sections/${s.code}`}
                        title={`${s.code} — ${s.title}`}
                        className={cn(
                          "flex items-center gap-2 rounded-lg text-[13px] transition-colors duration-150",
                          showCollapsed ? "justify-center px-2 py-2" : "justify-between px-3 py-2",
                          pathname === `/sections/${s.code}`
                            ? "bg-white/10 text-white border-l-[3px] border-l-[#7FC297] pl-[9px]"
                            : "hover:bg-white/5 text-white/75"
                        )}
                      >
                        {showCollapsed ? (
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
                </div>
              );
            })}
          </>
        )}
      </nav>

      <div className="mt-auto space-y-0.5 border-t border-white/10 p-3">
        {!isAdmin && (
          <button
            type="button"
            onClick={() =>
              downloadMyGroupPdf().catch((error) => toast.error(extractErrorMessage(error, "Échec de l'export PDF")))
            }
            title="Télécharger le Plan Stratégique Sectoriel (PDF)"
            className={cn(
              "flex w-full items-center gap-2.5 rounded-lg px-3 py-2 text-[13px] font-medium text-white/75 hover:bg-white/5 transition-colors duration-150",
              showCollapsed && "justify-center px-2"
            )}
          >
            <FileDown className="h-[18px] w-[18px] shrink-0" strokeWidth={1.75} />
            {!showCollapsed && "Plan sectoriel (PDF)"}
          </button>
        )}
      </div>
      </aside>
    </>
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
