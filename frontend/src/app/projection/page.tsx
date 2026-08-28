"use client";

import { useCallback, useEffect, useMemo, useRef, useState } from "react";
import Image from "next/image";
import Link from "next/link";
import { useQuery } from "@tanstack/react-query";
import {
  Activity,
  CheckCircle2,
  Maximize2,
  Minimize2,
  RotateCcw,
  Send,
  TrendingUp,
  Users,
  Volume2,
  VolumeX,
  X,
} from "lucide-react";

import { AuthGuard } from "@/components/layout/auth-guard";
import { getAdminActivity, getAdminDashboard, getAdminMatrix } from "@/lib/api/admin";
import { useRealtimeAdmin } from "@/hooks/use-realtime-admin";
import { useConnectionStore } from "@/store/connection-store";
import { useIsGroupTyping } from "@/store/presence-store";
import { useVoiceNotificationsStore } from "@/store/voice-notifications-store";
import { speak } from "@/lib/voice";
import { cn, timeAgo } from "@/lib/utils";
import type { ActivityEntryDto, AdminDashboardDto, MatrixCellDto } from "@/types/api";
import type { SectionStatus } from "@/types/common";

/** Seuls les jalons marquants sont affiches sur cet ecran projete : les brouillons et actions techniques (SAVE_DRAFT, ADMIN_EDIT, RESET, RETURN_TO_GROUP, LOGIN) sont volontairement masques pour ne pas noyer l'audience. */
const MILESTONE_ACTIONS = new Set(["SUBMIT", "SUBMIT_ALL", "VALIDATE", "REQUEST_REVISION"]);
const PROJECTION_ACTIVITY_DISPLAY_LIMIT = 14;

const ACTION_CONFIG: Record<
  string,
  { label: (a: ActivityEntryDto) => string; icon: React.ElementType; color: string }
> = {
  SUBMIT: {
    label: (a) => `${a.groupName} a soumis la Section ${a.sectionCode?.replace("S", "")}`,
    icon: Send,
    color: "text-primary-300",
  },
  SUBMIT_ALL: {
    label: (a) => `${a.groupName} a soumis la totalité de ses sections`,
    icon: CheckCircle2,
    color: "text-emerald-300",
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
};

const DEFAULT_ACTION_CONFIG = { label: (a: ActivityEntryDto) => `${a.groupName}`, icon: Activity, color: "text-white/50" };

const SECTION_STATUS_META: Record<SectionStatus, { label: string; className: string }> = {
  NOT_STARTED: { label: "Non commencé", className: "bg-white/10 text-white/50" },
  IN_PROGRESS: { label: "En cours", className: "bg-sky-500/15 text-sky-300" },
  SUBMITTED: { label: "Soumis", className: "bg-primary-500/15 text-primary-300" },
  VALIDATED: { label: "Validé", className: "bg-emerald-500/15 text-emerald-300" },
  REVISION_REQUESTED: { label: "À réviser", className: "bg-amber-500/15 text-amber-300" },
};

type DetailTarget =
  | { kind: "kpi"; id: "groups" | "completion" | "submitted" | "validated" | "revision" }
  | { kind: "group"; groupId: number }
  | { kind: "section"; sectionId: number };

export default function ProjectionRoute() {
  return (
    <AuthGuard requiredRole="ADMIN">
      <ProjectionPage />
    </AuthGuard>
  );
}

function ProjectionPage() {
  const [submitAllBanner, setSubmitAllBanner] = useState<{ entry: ActivityEntryDto; key: number } | null>(null);
  const bannerKeyRef = useRef(0);
  const [detail, setDetail] = useState<DetailTarget | null>(null);

  const dismissSubmitAllBanner = useCallback(() => setSubmitAllBanner(null), []);

  useRealtimeAdmin({
    voice: true,
    onSubmitAll: (entry) => {
      bannerKeyRef.current += 1;
      setSubmitAllBanner({ entry, key: bannerKeyRef.current });
    },
  });
  const connected = useConnectionStore((s) => s.connected);
  const voiceEnabled = useVoiceNotificationsStore((s) => s.enabled);
  const toggleVoice = useVoiceNotificationsStore((s) => s.toggle);

  const handleToggleVoice = useCallback(() => {
    const enabling = !voiceEnabled;
    toggleVoice();
    if (enabling) speak("Notifications vocales activées.");
  }, [voiceEnabled, toggleVoice]);

  const { data, isLoading } = useQuery({
    queryKey: ["admin", "dashboard"],
    queryFn: getAdminDashboard,
    refetchInterval: 15_000,
  });

  const { data: activity } = useQuery({
    queryKey: ["admin", "activity"],
    queryFn: () => getAdminActivity(50),
    refetchInterval: 15_000,
  });

  const { data: matrix } = useQuery({
    queryKey: ["admin", "matrix"],
    queryFn: getAdminMatrix,
    enabled: detail !== null,
    refetchInterval: detail !== null ? 15_000 : false,
  });

  const milestoneActivity = useMemo(
    () =>
      (activity ?? [])
        .filter((entry) => MILESTONE_ACTIONS.has(entry.action))
        .slice(0, PROJECTION_ACTIVITY_DISPLAY_LIMIT),
    [activity]
  );

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

  /** Section de la derniere activite marquante (soumission/validation) : sert a repondre visuellement dans "Avancement par section". */
  const latestSectionCode = useMemo(
    () => milestoneActivity.find((entry) => entry.sectionCode)?.sectionCode ?? null,
    [milestoneActivity]
  );

  return (
    <div className="relative h-screen w-full overflow-y-auto overflow-x-hidden bg-gradient-to-br from-[#0d1220] via-[#151d33] to-[#0d1220] text-white flex flex-col">
      <BackgroundDecor />

      {submitAllBanner && (
        <SubmitAllBanner
          key={submitAllBanner.key}
          entry={submitAllBanner.entry}
          onDismiss={dismissSubmitAllBanner}
        />
      )}

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
            onClick={handleToggleVoice}
            title={voiceEnabled ? "Désactiver les notifications vocales" : "Activer les notifications vocales"}
            className="h-9 w-9 rounded-full bg-white/5 border border-white/10 flex items-center justify-center hover:bg-white/10 hover:scale-110 active:scale-95 transition-all duration-200"
          >
            {voiceEnabled ? <Volume2 className="h-4 w-4" /> : <VolumeX className="h-4 w-4" />}
          </button>
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
        <>
        <main className="relative z-10 flex-1 min-h-0 flex flex-col gap-5 px-8 py-6">
          <div className="grid grid-cols-2 lg:grid-cols-5 gap-4 shrink-0">
            <KpiTile
              icon={Users}
              label="Groupes actifs"
              value={data.activeGroups}
              suffix={` / ${data.totalGroups}`}
              accent="bg-sky-500/20 text-sky-200"
              delay={0}
              onClick={() => setDetail({ kind: "kpi", id: "groups" })}
            />
            <KpiTile
              icon={TrendingUp}
              label="Complétion globale"
              value={data.globalCompletionPercent}
              suffix="%"
              accent="bg-primary-500/20 text-primary-200"
              delay={70}
              onClick={() => setDetail({ kind: "kpi", id: "completion" })}
            />
            <KpiTile
              icon={Send}
              label="Sections soumises"
              value={data.sectionsSubmitted}
              accent="bg-violet-500/20 text-violet-200"
              delay={140}
              onClick={() => setDetail({ kind: "kpi", id: "submitted" })}
            />
            <KpiTile
              icon={CheckCircle2}
              label="Sections validées"
              value={data.sectionsValidated}
              accent="bg-emerald-500/20 text-emerald-200"
              delay={210}
              onClick={() => setDetail({ kind: "kpi", id: "validated" })}
            />
            <KpiTile
              icon={RotateCcw}
              label="En révision"
              value={data.sectionsRevisionRequested}
              accent="bg-amber-500/20 text-amber-200"
              delay={280}
              onClick={() => setDetail({ kind: "kpi", id: "revision" })}
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
                  sortedGroups.map((g, i) => (
                    <GroupRow
                      key={g.groupId}
                      rank={i + 1}
                      group={g}
                      onClick={() => setDetail({ kind: "group", groupId: g.groupId })}
                    />
                  ))
                )}
              </div>
            </section>

            <section className="flex-1 min-w-0 rounded-2xl bg-white/[0.07] border border-white/15 flex flex-col min-h-0 animate-fade-in-up" style={{ animationDelay: "220ms" }}>
              <div className="px-6 py-4 border-b border-white/10 shrink-0">
                <h2 className="text-[15px] font-semibold uppercase tracking-wide text-white/80">Activité en direct</h2>
              </div>
              <div className="flex-1 min-h-0 overflow-y-auto px-6 py-5 space-y-4 scrollbar-thin">
                {milestoneActivity.length === 0 ? (
                  <p className="text-white/40 text-[13px] italic">Aucune activité pour l&apos;instant</p>
                ) : (
                  milestoneActivity.map((entry, i) => (
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
                  <SectionBar
                    key={s.sectionId}
                    code={s.code}
                    done={s.groupsSubmittedOrValidated}
                    total={s.totalGroups}
                    delay={i * 30}
                    onClick={() => setDetail({ kind: "section", sectionId: s.sectionId })}
                    highlighted={s.code === latestSectionCode}
                  />
                ))}
              </div>
            </section>
          )}
        </main>
        {detail && (
          <DetailPanel detail={detail} data={data} sections={sections} matrix={matrix} onClose={() => setDetail(null)} />
        )}
        </>
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
  onClick,
}: {
  icon: React.ElementType;
  label: string;
  value: number;
  suffix?: string;
  accent: string;
  delay?: number;
  onClick?: () => void;
}) {
  const display = useCountUp(value);

  return (
    <div
      role="button"
      tabIndex={0}
      onClick={onClick}
      onKeyDown={(e) => {
        if (e.key === "Enter" || e.key === " ") {
          e.preventDefault();
          onClick?.();
        }
      }}
      className="group rounded-2xl bg-white/[0.06] border border-white/10 px-6 py-5 flex items-center gap-4 animate-fade-in-up hover:-translate-y-1 hover:bg-white/[0.1] hover:border-white/20 hover:shadow-xl hover:shadow-black/20 transition-all duration-300 cursor-pointer focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-white/40"
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

function GroupRow({
  rank,
  group,
  onClick,
}: {
  rank: number;
  group: AdminDashboardDto["groups"][number];
  onClick?: () => void;
}) {
  const isTyping = useIsGroupTyping(group.groupId);

  return (
    <div
      role="button"
      tabIndex={0}
      onClick={onClick}
      onKeyDown={(e) => {
        if (e.key === "Enter" || e.key === " ") {
          e.preventDefault();
          onClick?.();
        }
      }}
      className="flex items-center gap-4 animate-fade-in-up -mx-2 px-2 py-1 rounded-xl cursor-pointer hover:bg-white/[0.05] transition-colors duration-200 focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-white/40"
      style={{ animationDelay: `${Math.min(rank - 1, 8) * 60}ms` }}
    >
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

function SubmitAllBanner({ entry, onDismiss }: { entry: ActivityEntryDto; onDismiss: () => void }) {
  const [show, setShow] = useState(false);

  useEffect(() => {
    const showTimer = setTimeout(() => setShow(true), 20);
    const hideTimer = setTimeout(() => setShow(false), 5000);
    const dismissTimer = setTimeout(onDismiss, 5600);
    return () => {
      clearTimeout(showTimer);
      clearTimeout(hideTimer);
      clearTimeout(dismissTimer);
    };
  }, [entry, onDismiss]);

  return (
    <div
      className={cn(
        "pointer-events-none fixed inset-x-0 top-6 z-50 flex justify-center px-4 transition-all duration-500 ease-out",
        show ? "opacity-100 translate-y-0" : "opacity-0 -translate-y-4"
      )}
    >
      <div className="flex items-center gap-4 rounded-2xl border border-emerald-300/40 bg-emerald-600 px-6 py-4 shadow-2xl shadow-emerald-950/50">
        <span className="relative flex h-11 w-11 shrink-0 items-center justify-center rounded-full bg-white/15">
          <span className="absolute inset-0 rounded-full bg-white/25 animate-glow-pulse" />
          <CheckCircle2 className="relative h-6 w-6 text-white" />
        </span>
        <div className="min-w-0">
          <p className="text-[16px] font-bold text-white leading-tight">
            {entry.groupName ?? "Une direction"} a soumis la totalité de ses sections !
          </p>
          <p className="text-[12px] text-white/80 mt-0.5">Toutes les sections ont été transmises pour validation.</p>
        </div>
      </div>
    </div>
  );
}

function ActivityRow({ entry, isNewest }: { entry: ActivityEntryDto; isNewest: boolean }) {
  const config = ACTION_CONFIG[entry.action] ?? DEFAULT_ACTION_CONFIG;
  const Icon = config.icon;
  return (
    <div
      className={cn(
        "flex items-start gap-3 animate-fade-in-right rounded-lg -mx-2 px-2 py-1.5 border-l-2 transition-colors duration-300",
        isNewest ? "border-l-sky-400 animate-spotlight-pulse" : "border-l-transparent"
      )}
    >
      <Icon className={cn("h-4 w-4 mt-0.5 shrink-0", config.color)} />
      <div className="min-w-0 flex-1">
        <div className="flex items-center gap-2">
          <p className="text-[13px] text-white/85 leading-snug">{config.label(entry)}</p>
          {isNewest && <span className="h-1.5 w-1.5 rounded-full bg-sky-400 shrink-0 animate-pulse" />}
        </div>
        <p className="text-[11px] text-white/40 mt-0.5">{timeAgo(entry.timestamp)}</p>
      </div>
    </div>
  );
}

function SectionBar({
  code,
  done,
  total,
  delay = 0,
  onClick,
  highlighted = false,
}: {
  code: string;
  done: number;
  total: number;
  delay?: number;
  onClick?: () => void;
  highlighted?: boolean;
}) {
  const ratio = total > 0 ? done / total : 0;
  const complete = ratio >= 1 && total > 0;
  return (
    <button
      type="button"
      onClick={onClick}
      className={cn(
        "flex flex-col items-center gap-1.5 w-14 shrink-0 animate-fade-in-up cursor-pointer group/section focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-white/40 rounded-md",
        highlighted && "animate-spotlight-pulse"
      )}
      style={{ animationDelay: `${delay}ms` }}
    >
      <div
        className={cn(
          "h-16 w-full rounded-md flex items-end overflow-hidden transition-colors duration-200",
          highlighted ? "bg-sky-400/15 ring-1 ring-sky-400/60" : "bg-white/10 group-hover/section:bg-white/15"
        )}
      >
        <div
          className={cn(
            "relative w-full overflow-hidden transition-all duration-700 ease-out",
            complete ? "bg-primary-300" : highlighted ? "bg-sky-300" : "bg-primary-500/60"
          )}
          style={{ height: `${Math.round(ratio * 100)}%` }}
        >
          {complete && (
            <span className="absolute inset-0 -skew-x-12 bg-gradient-to-r from-transparent via-white/50 to-transparent animate-shimmer" />
          )}
        </div>
      </div>
      <span className={cn("flex items-center gap-1 text-[10px] font-medium", highlighted ? "text-sky-300" : "text-white/60")}>
        {code}
        {highlighted && <span className="h-1.5 w-1.5 rounded-full bg-sky-400 animate-pulse" />}
      </span>
      <span className="text-[10px] text-white/35 tabular-nums">
        {done}/{total}
      </span>
    </button>
  );
}

function DetailPanel({
  detail,
  data,
  sections,
  matrix,
  onClose,
}: {
  detail: DetailTarget;
  data: AdminDashboardDto;
  sections: AdminDashboardDto["sectionAdvancement"];
  matrix: MatrixCellDto[] | undefined;
  onClose: () => void;
}) {
  useEffect(() => {
    const handler = (e: KeyboardEvent) => {
      if (e.key === "Escape") onClose();
    };
    window.addEventListener("keydown", handler);
    return () => window.removeEventListener("keydown", handler);
  }, [onClose]);

  const groupsByCompletion = useMemo(
    () => [...data.groups].sort((a, b) => b.completionPercent - a.completionPercent),
    [data.groups]
  );
  const groupsBySubmitted = useMemo(() => [...data.groups].sort((a, b) => b.submitted - a.submitted), [data.groups]);
  const groupsByValidated = useMemo(() => [...data.groups].sort((a, b) => b.validated - a.validated), [data.groups]);

  let title = "";
  let subtitle = "";
  let body: React.ReactNode = null;

  if (detail.kind === "kpi" && detail.id === "groups") {
    title = "Groupes actifs";
    subtitle = `${data.activeGroups} actif(s) sur ${data.totalGroups} groupe(s)`;
    body = (
      <div className="space-y-3">
        {[...data.groups]
          .sort((a, b) => a.groupName.localeCompare(b.groupName))
          .map((g) => (
            <div
              key={g.groupId}
              className="flex items-center justify-between gap-3 rounded-xl bg-white/[0.04] border border-white/10 px-4 py-3"
            >
              <div className="min-w-0">
                <div className="flex items-center gap-2">
                  <p className="text-[14px] font-semibold truncate">{g.groupName}</p>
                  <span
                    className={cn(
                      "text-[10px] px-1.5 py-0.5 rounded-full shrink-0",
                      g.enabled ? "bg-emerald-500/15 text-emerald-300" : "bg-white/10 text-white/50"
                    )}
                  >
                    {g.enabled ? "Actif" : "Désactivé"}
                  </span>
                </div>
                <p className="text-[12px] text-white/45 mt-0.5 truncate">{g.leaderFullName ?? "Aucun responsable"}</p>
              </div>
              <div className="text-right shrink-0">
                <p className="text-[16px] font-bold tabular-nums text-primary-200">{g.completionPercent}%</p>
                <p className="text-[11px] text-white/40">
                  {g.submitted} soumises · {g.validated} validées
                </p>
              </div>
            </div>
          ))}
      </div>
    );
  } else if (detail.kind === "kpi" && detail.id === "completion") {
    title = "Complétion globale";
    subtitle = `${data.globalCompletionPercent}% de moyenne sur ${data.totalGroups} groupes`;
    body = (
      <div className="space-y-4">
        {groupsByCompletion.map((g, i) => (
          <div key={g.groupId}>
            <div className="flex items-center justify-between gap-3 mb-1.5">
              <p className="text-[13px] font-medium truncate">
                {i + 1}. {g.groupName}
              </p>
              <span className="text-[14px] font-bold tabular-nums text-primary-200 shrink-0">{g.completionPercent}%</span>
            </div>
            <div className="h-2 rounded-full bg-white/10 overflow-hidden">
              <div
                className="h-full rounded-full bg-gradient-to-r from-primary-400 to-primary-200"
                style={{ width: `${g.completionPercent}%` }}
              />
            </div>
          </div>
        ))}
      </div>
    );
  } else if (detail.kind === "kpi" && detail.id === "submitted") {
    title = "Sections soumises";
    subtitle = `${data.sectionsSubmitted} soumission(s) au total`;
    body = (
      <div className="space-y-6">
        <div>
          <h4 className="text-[11px] font-semibold uppercase tracking-wide text-white/50 mb-2.5">Par groupe</h4>
          <div className="space-y-2">
            {groupsBySubmitted.map((g) => (
              <div key={g.groupId} className="flex items-center justify-between text-[13px]">
                <span className="text-white/80 truncate">{g.groupName}</span>
                <span className="font-semibold tabular-nums text-violet-300 shrink-0">{g.submitted}</span>
              </div>
            ))}
          </div>
        </div>
        <div>
          <h4 className="text-[11px] font-semibold uppercase tracking-wide text-white/50 mb-2.5">Par section</h4>
          <div className="space-y-2">
            {sections.map((s) => (
              <div key={s.sectionId} className="flex items-center justify-between text-[13px] gap-3">
                <span className="text-white/80 truncate">
                  {s.code} — {s.title}
                </span>
                <span className="font-medium tabular-nums text-white/50 shrink-0">
                  {s.groupsSubmittedOrValidated}/{s.totalGroups}
                </span>
              </div>
            ))}
          </div>
        </div>
      </div>
    );
  } else if (detail.kind === "kpi" && detail.id === "validated") {
    title = "Sections validées";
    subtitle = `${data.sectionsValidated} section(s) validée(s) au total`;
    const validatedCells = (matrix ?? []).filter((c) => c.status === "VALIDATED");
    body = (
      <div className="space-y-6">
        <div>
          <h4 className="text-[11px] font-semibold uppercase tracking-wide text-white/50 mb-2.5">Par groupe</h4>
          <div className="space-y-2">
            {groupsByValidated.map((g) => (
              <div key={g.groupId} className="flex items-center justify-between text-[13px]">
                <span className="text-white/80 truncate">{g.groupName}</span>
                <span className="font-semibold tabular-nums text-emerald-300 shrink-0">{g.validated}</span>
              </div>
            ))}
          </div>
        </div>
        <div>
          <h4 className="text-[11px] font-semibold uppercase tracking-wide text-white/50 mb-2.5">Détail des sections validées</h4>
          {!matrix ? (
            <LoadingRow />
          ) : validatedCells.length === 0 ? (
            <EmptyRow label="Aucune section validée pour l'instant." />
          ) : (
            <div className="space-y-2">
              {validatedCells.map((c) => (
                <div key={`${c.groupId}-${c.sectionId}`} className="flex items-center justify-between text-[13px] gap-3">
                  <span className="text-white/80 truncate">
                    {c.groupName} — {c.sectionCode}
                  </span>
                  <SectionStatusPill status={c.status} />
                </div>
              ))}
            </div>
          )}
        </div>
      </div>
    );
  } else if (detail.kind === "kpi" && detail.id === "revision") {
    title = "Sections en révision";
    subtitle = `${data.sectionsRevisionRequested} section(s) actuellement en révision`;
    const revisionCells = (matrix ?? []).filter((c) => c.status === "REVISION_REQUESTED");
    body = !matrix ? (
      <LoadingRow />
    ) : revisionCells.length === 0 ? (
      <EmptyRow label="Aucune section en révision actuellement." positive />
    ) : (
      <div className="space-y-2">
        {revisionCells.map((c) => (
          <div
            key={`${c.groupId}-${c.sectionId}`}
            className="flex items-center justify-between text-[13px] gap-3 rounded-xl bg-white/[0.04] border border-white/10 px-4 py-3"
          >
            <span className="text-white/80 truncate">
              {c.groupName} — {c.sectionCode}
            </span>
            <SectionStatusPill status={c.status} />
          </div>
        ))}
      </div>
    );
  } else if (detail.kind === "group") {
    const group = data.groups.find((g) => g.groupId === detail.groupId);
    title = group?.groupName ?? "Groupe";
    subtitle = group?.leaderFullName ?? "Aucun responsable";
    const rows = (matrix ?? [])
      .filter((c) => c.groupId === detail.groupId)
      .map((c) => ({ cell: c, section: sections.find((s) => s.code === c.sectionCode) }))
      .sort((a, b) => (a.section?.order ?? 0) - (b.section?.order ?? 0));
    body = (
      <div className="space-y-5">
        {group && (
          <div className="grid grid-cols-3 gap-3">
            <StatBox label="Complétion" value={`${group.completionPercent}%`} />
            <StatBox label="Soumises" value={String(group.submitted)} />
            <StatBox label="Validées" value={String(group.validated)} />
          </div>
        )}
        {group?.lastActivityAt && (
          <p className="text-[12px] text-white/45">Dernière activité : {timeAgo(group.lastActivityAt)}</p>
        )}
        <div>
          <h4 className="text-[11px] font-semibold uppercase tracking-wide text-white/50 mb-2.5">Sections</h4>
          {!matrix ? (
            <LoadingRow />
          ) : (
            <div className="space-y-2">
              {rows.map(({ cell, section }) => (
                <div
                  key={cell.sectionId}
                  className="flex items-center justify-between text-[13px] gap-3 rounded-xl bg-white/[0.04] border border-white/10 px-4 py-2.5"
                >
                  <span className="text-white/80 truncate">
                    {cell.sectionCode} — {section?.title ?? ""}
                  </span>
                  <SectionStatusPill status={cell.status} />
                </div>
              ))}
            </div>
          )}
        </div>
      </div>
    );
  } else if (detail.kind === "section") {
    const section = sections.find((s) => s.sectionId === detail.sectionId);
    title = section ? `Section ${section.code}` : "Section";
    subtitle = section ? `${section.title} · ${section.groupsSubmittedOrValidated}/${section.totalGroups} groupes` : "";
    const rows = (matrix ?? [])
      .filter((c) => c.sectionId === detail.sectionId)
      .sort((a, b) => a.groupName.localeCompare(b.groupName));
    body = !matrix ? (
      <LoadingRow />
    ) : (
      <div className="space-y-2">
        {rows.map((c) => (
          <div
            key={c.groupId}
            className="flex items-center justify-between text-[13px] gap-3 rounded-xl bg-white/[0.04] border border-white/10 px-4 py-3"
          >
            <span className="text-white/80 truncate">{c.groupName}</span>
            <SectionStatusPill status={c.status} />
          </div>
        ))}
      </div>
    );
  }

  return (
    <div
      className="fixed inset-0 z-[60] flex items-center justify-center bg-black/60 backdrop-blur-sm px-6 animate-fade-in"
      onClick={onClose}
    >
      <div
        className="w-full max-w-2xl max-h-[80vh] rounded-2xl bg-[#141b2e] border border-white/15 shadow-2xl shadow-black/50 flex flex-col animate-fade-scale-in"
        onClick={(e) => e.stopPropagation()}
      >
        <div className="flex items-center justify-between gap-4 px-6 py-4 border-b border-white/10 shrink-0">
          <div className="min-w-0">
            <h3 className="text-[16px] font-bold truncate">{title}</h3>
            {subtitle && <p className="text-[12px] text-white/50 mt-0.5 truncate">{subtitle}</p>}
          </div>
          <button
            type="button"
            onClick={onClose}
            className="h-8 w-8 rounded-full bg-white/5 border border-white/10 flex items-center justify-center hover:bg-white/10 shrink-0"
          >
            <X className="h-4 w-4" />
          </button>
        </div>
        <div className="flex-1 min-h-0 overflow-y-auto px-6 py-5 scrollbar-thin">{body}</div>
      </div>
    </div>
  );
}

function SectionStatusPill({ status }: { status: SectionStatus }) {
  const meta = SECTION_STATUS_META[status] ?? SECTION_STATUS_META.NOT_STARTED;
  return (
    <span className={cn("text-[11px] px-2 py-0.5 rounded-full font-medium shrink-0", meta.className)}>{meta.label}</span>
  );
}

function StatBox({ label, value }: { label: string; value: string }) {
  return (
    <div className="rounded-xl bg-white/[0.04] border border-white/10 px-3 py-2.5 text-center">
      <p className="text-[18px] font-bold tabular-nums">{value}</p>
      <p className="text-[10px] text-white/45 uppercase tracking-wide mt-0.5">{label}</p>
    </div>
  );
}

function LoadingRow() {
  return (
    <div className="flex items-center justify-center py-8">
      <div className="h-6 w-6 rounded-full border-2 border-white/20 border-t-white/70 animate-spin" />
    </div>
  );
}

function EmptyRow({ label, positive = false }: { label: string; positive?: boolean }) {
  return <p className={cn("text-[13px] italic", positive ? "text-emerald-300/80" : "text-white/40")}>{label}</p>;
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
