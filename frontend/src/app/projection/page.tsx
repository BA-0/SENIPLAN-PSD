"use client";

import { useEffect, useMemo, useRef, useState } from "react";
import Image from "next/image";
import Link from "next/link";
import { useQuery } from "@tanstack/react-query";
import {
  Activity,
  CheckCircle2,
  LogIn,
  Maximize2,
  Minimize2,
  RotateCcw,
  Send,
  TrendingUp,
  Users,
  X,
} from "lucide-react";

import { AuthGuard } from "@/components/layout/auth-guard";
import { getAdminActivity, getAdminDashboard } from "@/lib/api/admin";
import { useRealtimeAdmin } from "@/hooks/use-realtime-admin";
import { useConnectionStore } from "@/store/connection-store";
import { useIsGroupTyping } from "@/store/presence-store";
import { cn, timeAgo } from "@/lib/utils";
import type { ActivityEntryDto, AdminDashboardDto } from "@/types/api";

const ACTION_CONFIG: Record<
  string,
  { label: (a: ActivityEntryDto) => string; icon: React.ElementType; color: string }
> = {
  SAVE_DRAFT: {
    label: (a) => `${a.groupName} enregistre un brouillon — ${a.sectionCode}`,
    icon: Activity,
    color: "text-sky-300",
  },
  SUBMIT: {
    label: (a) => `${a.groupName} a soumis la Section ${a.sectionCode?.replace("S", "")}`,
    icon: Send,
    color: "text-primary-300",
  },
  VALIDATE: {
    label: (a) => `${a.sectionCode} validée pour ${a.groupName}`,
    icon: CheckCircle2,
    color: "text-emerald-300",
  },
  REQUEST_REVISION: {
    label: (a) => `${a.sectionCode} renvoyée pour révision — ${a.groupName}`,
    icon: RotateCcw,
    color: "text-amber-300",
  },
  LOGIN: {
    label: (a) => `${a.userFullName ?? a.groupName} s'est connecté(e)`,
    icon: LogIn,
    color: "text-white/50",
  },
};

export default function ProjectionRoute() {
  return (
    <AuthGuard requiredRole="ADMIN">
      <ProjectionPage />
    </AuthGuard>
  );
}

function ProjectionPage() {
  useRealtimeAdmin();
  const connected = useConnectionStore((s) => s.connected);

  const { data, isLoading } = useQuery({
    queryKey: ["admin", "dashboard"],
    queryFn: getAdminDashboard,
    refetchInterval: 15_000,
  });

  const { data: activity } = useQuery({
    queryKey: ["admin", "activity"],
    queryFn: () => getAdminActivity(14),
    refetchInterval: 15_000,
  });

  const now = useClock();
  const { isFullscreen, toggleFullscreen } = useFullscreen();

  const sortedGroups = useMemo(
    () => [...(data?.groups ?? [])].sort((a, b) => b.completionPercent - a.completionPercent),
    [data]
  );

  const sections = useMemo(
    () => [...(data?.sectionAdvancement ?? [])].sort((a, b) => a.order - b.order),
    [data]
  );

  return (
    <div className="relative h-screen w-full overflow-y-auto overflow-x-hidden bg-gradient-to-br from-[#0d1220] via-[#151d33] to-[#0d1220] text-white flex flex-col">
      <BackgroundDecor />

      <header className="relative z-10 flex items-center justify-between gap-4 px-8 py-5 border-b border-white/10 shrink-0 backdrop-blur-sm">
        <div className="flex items-center gap-4 min-w-0">
          <div className="bg-white rounded-xl p-2.5 shadow-lg shadow-black/20 shrink-0 animate-fade-scale-in">
            <Image src="/logo-senico.png" alt="SENICO" width={514} height={98} className="h-10 w-10 object-contain" />
          </div>
          <div className="min-w-0 animate-fade-in-up" style={{ animationDelay: "80ms" }}>
            <p className="text-[22px] font-bold leading-tight truncate">Plan Stratégique — SENICO</p>
            <p className="text-[13px] text-white/60 truncate">
              Plan Stratégique de Développement 2027-2031 · Séance de travail des groupes
            </p>
          </div>
        </div>

        <div className="flex items-center gap-5 shrink-0 animate-fade-in" style={{ animationDelay: "120ms" }}>
          <div className="text-right">
            <p className="text-[28px] font-bold tabular-nums leading-none">{now ? formatTime(now) : "--:--:--"}</p>
            <p className="text-[12px] text-white/60 capitalize mt-1">{now ? formatDate(now) : ""}</p>
          </div>
          <div className="flex items-center gap-2 rounded-full bg-white/5 border border-white/10 px-3 py-1.5">
            <span className="relative flex h-2 w-2">
              {connected && (
                <span className="absolute inline-flex h-full w-full rounded-full bg-emerald-400 animate-ping opacity-75" />
              )}
              <span className={cn("relative inline-flex h-2 w-2 rounded-full", connected ? "bg-emerald-400" : "bg-amber-400")} />
            </span>
            <span className="text-[12px] font-medium text-white/70">{connected ? "En direct" : "Synchronisation..."}</span>
          </div>
          <button
            type="button"
            onClick={toggleFullscreen}
            title={isFullscreen ? "Quitter le plein écran" : "Plein écran"}
            className="h-9 w-9 rounded-full bg-white/5 border border-white/10 flex items-center justify-center hover:bg-white/10 hover:scale-110 active:scale-95 transition-all duration-200"
          >
            {isFullscreen ? <Minimize2 className="h-4 w-4" /> : <Maximize2 className="h-4 w-4" />}
          </button>
          <Link
            href="/admin"
            title="Quitter la projection"
            className="h-9 w-9 rounded-full bg-white/5 border border-white/10 flex items-center justify-center hover:bg-white/10 hover:scale-110 active:scale-95 transition-all duration-200"
          >
            <X className="h-4 w-4" />
          </Link>
        </div>
      </header>

      {isLoading || !data ? (
        <div className="relative z-10 flex-1 flex items-center justify-center">
          <div className="h-10 w-10 rounded-full border-2 border-white/20 border-t-white/70 animate-spin" />
        </div>
      ) : (
        <main className="relative z-10 flex-1 min-h-0 flex flex-col gap-5 px-8 py-6">
          <div className="grid grid-cols-2 lg:grid-cols-5 gap-4 shrink-0">
            <KpiTile
              icon={Users}
              label="Groupes actifs"
              value={data.activeGroups}
              suffix={` / ${data.totalGroups}`}
              accent="bg-sky-500/20 text-sky-200"
              delay={0}
            />
            <KpiTile
              icon={TrendingUp}
              label="Complétion globale"
              value={data.globalCompletionPercent}
              suffix="%"
              accent="bg-primary-500/20 text-primary-200"
              delay={70}
            />
            <KpiTile
              icon={Send}
              label="Sections soumises"
              value={data.sectionsSubmitted}
              accent="bg-violet-500/20 text-violet-200"
              delay={140}
            />
            <KpiTile
              icon={CheckCircle2}
              label="Sections validées"
              value={data.sectionsValidated}
              accent="bg-emerald-500/20 text-emerald-200"
              delay={210}
            />
            <KpiTile
              icon={RotateCcw}
              label="En révision"
              value={data.sectionsRevisionRequested}
              accent="bg-amber-500/20 text-amber-200"
              delay={280}
            />
          </div>

          <div className="flex-1 min-h-0 flex gap-5">
            <section className="flex-[2] min-w-0 rounded-2xl bg-white/[0.07] border border-white/15 flex flex-col min-h-0 animate-fade-in-up" style={{ animationDelay: "160ms" }}>
              <div className="px-6 py-4 border-b border-white/10 flex items-center justify-between shrink-0">
                <h2 className="text-[15px] font-semibold uppercase tracking-wide text-white/80">Progression des groupes</h2>
                <span className="text-[12px] text-white/50">{sortedGroups.length} groupes</span>
              </div>
              <div className="flex-1 min-h-0 overflow-y-auto px-6 py-5 space-y-5 scrollbar-thin">
                {sortedGroups.length === 0 ? (
                  <p className="text-white/40 text-[13px] italic">Aucun groupe configuré</p>
                ) : (
                  sortedGroups.map((g, i) => <GroupRow key={g.groupId} rank={i + 1} group={g} />)
                )}
              </div>
            </section>

            <section className="flex-1 min-w-0 rounded-2xl bg-white/[0.07] border border-white/15 flex flex-col min-h-0 animate-fade-in-up" style={{ animationDelay: "220ms" }}>
              <div className="px-6 py-4 border-b border-white/10 shrink-0">
                <h2 className="text-[15px] font-semibold uppercase tracking-wide text-white/80">Activité en direct</h2>
              </div>
              <div className="flex-1 min-h-0 overflow-y-auto px-6 py-5 space-y-4 scrollbar-thin">
                {!activity || activity.length === 0 ? (
                  <p className="text-white/40 text-[13px] italic">Aucune activité pour l&apos;instant</p>
                ) : (
                  activity.map((entry, i) => (
                    <ActivityRow
                      key={`${entry.timestamp}-${entry.groupId}-${entry.action}-${entry.sectionCode}`}
                      entry={entry}
                      isNewest={i === 0}
                    />
                  ))
                )}
              </div>
            </section>
          </div>

          {sections.length > 0 && (
            <section className="shrink-0 rounded-2xl bg-white/[0.07] border border-white/15 px-6 py-4 animate-fade-in-up" style={{ animationDelay: "280ms" }}>
              <h2 className="text-[12px] font-semibold uppercase tracking-wide text-white/60 mb-3">Avancement par section</h2>
              <div className="flex gap-2 overflow-x-auto scrollbar-thin pb-1">
                {sections.map((s, i) => (
                  <SectionBar key={s.sectionId} code={s.code} done={s.groupsSubmittedOrValidated} total={s.totalGroups} delay={i * 30} />
                ))}
              </div>
            </section>
          )}
        </main>
      )}
    </div>
  );
}

function BackgroundDecor() {
  return (
    <div className="pointer-events-none absolute inset-0 overflow-hidden z-0">
      <div className="absolute -top-24 -left-16 h-96 w-96 rounded-full bg-sky-400/20 blur-3xl animate-float" />
      <div className="absolute top-1/3 -right-24 h-[28rem] w-[28rem] rounded-full bg-primary-400/15 blur-3xl animate-float-slow" />
      <div className="absolute -bottom-32 left-1/4 h-80 w-80 rounded-full bg-violet-500/10 blur-3xl animate-float" style={{ animationDelay: "2s" }} />
      <div className="absolute inset-0 bg-gradient-to-t from-[#0d1220]/70 via-transparent to-[#0d1220]/30" />
    </div>
  );
}

function KpiTile({
  icon: Icon,
  label,
  value,
  suffix = "",
  accent,
  delay = 0,
}: {
  icon: React.ElementType;
  label: string;
  value: number;
  suffix?: string;
  accent: string;
  delay?: number;
}) {
  const display = useCountUp(value);

  return (
    <div
      className="group rounded-2xl bg-white/[0.06] border border-white/10 px-6 py-5 flex items-center gap-4 animate-fade-in-up hover:-translate-y-1 hover:bg-white/[0.1] hover:border-white/20 hover:shadow-xl hover:shadow-black/20 transition-all duration-300"
      style={{ animationDelay: `${delay}ms` }}
    >
      <div className={cn("relative h-12 w-12 rounded-xl flex items-center justify-center shrink-0", accent)}>
        <span className="absolute inset-0 rounded-xl bg-current animate-glow-pulse" />
        <Icon className="relative h-6 w-6" strokeWidth={1.75} />
      </div>
      <div className="min-w-0">
        <p className="text-[34px] font-bold tabular-nums leading-none">
          {display}
          {suffix}
        </p>
        <p className="text-[11px] text-white/60 mt-1.5 uppercase tracking-wide truncate">{label}</p>
      </div>
    </div>
  );
}

function GroupRow({ rank, group }: { rank: number; group: AdminDashboardDto["groups"][number] }) {
  const isTyping = useIsGroupTyping(group.groupId);

  return (
    <div className="flex items-center gap-4 animate-fade-in-up" style={{ animationDelay: `${Math.min(rank - 1, 8) * 60}ms` }}>
      <span className="text-[14px] font-bold text-white/30 w-5 shrink-0 text-right">{rank}</span>
      <div className="flex-1 min-w-0">
        <div className="flex items-center justify-between gap-3 mb-1.5">
          <div className="flex items-center gap-2 min-w-0">
            <p className="text-[15px] font-semibold truncate">{group.groupName}</p>
            {!group.enabled && (
              <span className="text-[10px] px-1.5 py-0.5 rounded bg-white/10 text-white/50 shrink-0">Désactivé</span>
            )}
            {isTyping && (
              <span className="flex items-center gap-1 text-[11px] text-sky-300 shrink-0">
                <span className="h-1.5 w-1.5 rounded-full bg-sky-300 animate-pulse" />
                saisie en cours
              </span>
            )}
          </div>
          <span className="text-[18px] font-bold tabular-nums text-primary-200 shrink-0">{group.completionPercent}%</span>
        </div>
        <div className="h-2.5 rounded-full bg-white/10 overflow-hidden">
          <div
            className="relative h-full rounded-full bg-gradient-to-r from-primary-400 to-primary-200 overflow-hidden transition-[width] duration-700 ease-out"
            style={{ width: `${group.completionPercent}%` }}
          >
            <span className="absolute inset-0 -skew-x-12 bg-gradient-to-r from-transparent via-white/50 to-transparent animate-shimmer" />
          </div>
        </div>
        <div className="flex items-center gap-3 mt-1.5 text-[11px] text-white/45">
          <span className="truncate">{group.leaderFullName ?? "—"}</span>
          <span>·</span>
          <span className="shrink-0">{group.submitted} soumises</span>
          <span className="shrink-0">{group.validated} validées</span>
          {group.lastActivityAt && <span className="ml-auto shrink-0">{timeAgo(group.lastActivityAt)}</span>}
        </div>
      </div>
    </div>
  );
}

function ActivityRow({ entry, isNewest }: { entry: ActivityEntryDto; isNewest: boolean }) {
  const config = ACTION_CONFIG[entry.action] ?? ACTION_CONFIG.SAVE_DRAFT;
  const Icon = config.icon;
  return (
    <div
      className={cn(
        "flex items-start gap-3 animate-fade-in-right rounded-lg -mx-2 px-2 py-1",
        isNewest && "animate-flash-highlight"
      )}
    >
      <Icon className={cn("h-4 w-4 mt-0.5 shrink-0", config.color)} />
      <div className="min-w-0">
        <p className="text-[13px] text-white/85 leading-snug">{config.label(entry)}</p>
        <p className="text-[11px] text-white/40 mt-0.5">{timeAgo(entry.timestamp)}</p>
      </div>
    </div>
  );
}

function SectionBar({ code, done, total, delay = 0 }: { code: string; done: number; total: number; delay?: number }) {
  const ratio = total > 0 ? done / total : 0;
  const complete = ratio >= 1 && total > 0;
  return (
    <div className="flex flex-col items-center gap-1.5 w-14 shrink-0 animate-fade-in-up" style={{ animationDelay: `${delay}ms` }}>
      <div className="h-16 w-full rounded-md bg-white/10 flex items-end overflow-hidden">
        <div
          className={cn("relative w-full overflow-hidden transition-all duration-700 ease-out", complete ? "bg-primary-300" : "bg-primary-500/60")}
          style={{ height: `${Math.round(ratio * 100)}%` }}
        >
          {complete && (
            <span className="absolute inset-0 -skew-x-12 bg-gradient-to-r from-transparent via-white/50 to-transparent animate-shimmer" />
          )}
        </div>
      </div>
      <span className="text-[10px] text-white/60 font-medium">{code}</span>
      <span className="text-[10px] text-white/35 tabular-nums">
        {done}/{total}
      </span>
    </div>
  );
}

function formatTime(date: Date): string {
  return new Intl.DateTimeFormat("fr-FR", { hour: "2-digit", minute: "2-digit", second: "2-digit" }).format(date);
}

function formatDate(date: Date): string {
  return new Intl.DateTimeFormat("fr-FR", { weekday: "long", day: "numeric", month: "long", year: "numeric" }).format(date);
}

function useClock(): Date | null {
  const [now, setNow] = useState<Date | null>(null);
  useEffect(() => {
    setNow(new Date());
    const id = setInterval(() => setNow(new Date()), 1000);
    return () => clearInterval(id);
  }, []);
  return now;
}

function useFullscreen() {
  const [isFullscreen, setIsFullscreen] = useState(false);

  useEffect(() => {
    const handler = () => setIsFullscreen(!!document.fullscreenElement);
    document.addEventListener("fullscreenchange", handler);
    return () => document.removeEventListener("fullscreenchange", handler);
  }, []);

  function toggleFullscreen() {
    if (document.fullscreenElement) {
      document.exitFullscreen().catch(() => {});
    } else {
      document.documentElement.requestFullscreen().catch(() => {});
    }
  }

  return { isFullscreen, toggleFullscreen };
}

function useCountUp(value: number, duration = 900): number {
  const [display, setDisplay] = useState(value);
  const fromRef = useRef(value);

  useEffect(() => {
    const from = fromRef.current;
    const to = value;
    if (from === to) return;

    const start = performance.now();
    let frame: number;

    const tick = (t: number) => {
      const progress = Math.min(1, (t - start) / duration);
      const eased = 1 - Math.pow(1 - progress, 3);
      setDisplay(Math.round(from + (to - from) * eased));
      if (progress < 1) {
        frame = requestAnimationFrame(tick);
      } else {
        fromRef.current = to;
      }
    };

    frame = requestAnimationFrame(tick);
    return () => cancelAnimationFrame(frame);
  }, [value, duration]);

  return display;
}
