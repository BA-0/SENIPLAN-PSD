"use client";

import Link from "next/link";
import { useMemo, useState } from "react";
import { useQuery } from "@tanstack/react-query";
import { Users, TrendingUp, Send, CheckCircle2, RotateCcw, ArrowRight, AlertTriangle } from "lucide-react";

import { Card, CardContent, CardHeader, CardTitle } from "@/components/ui/card";
import { KpiCard } from "@/components/kpi-card";
import { GroupProgressChart } from "@/components/charts/group-progress-chart";
import { StatusHeatmap } from "@/components/charts/status-heatmap";
import { ActivityFeed } from "@/components/activity-feed";
import { getAdminActivity, getAdminDashboard, getAdminMatrix } from "@/lib/api/admin";
import { useRealtimeAdmin } from "@/hooks/use-realtime-admin";
import { useIsGroupTyping } from "@/store/presence-store";

export default function AdminDashboardPage() {
  useRealtimeAdmin();

  const { data, isLoading, isError } = useQuery({
    queryKey: ["admin", "dashboard"],
    queryFn: getAdminDashboard,
    refetchInterval: 15_000,
  });

  const { data: matrix, isError: isMatrixError } = useQuery({
    queryKey: ["admin", "matrix"],
    queryFn: getAdminMatrix,
    refetchInterval: 15_000,
  });

  const ACTIVITY_PAGE_SIZE = 20;
  const ACTIVITY_MAX = 100;
  const [activityLimit, setActivityLimit] = useState(ACTIVITY_PAGE_SIZE);

  const {
    data: activity,
    isError: isActivityError,
    isFetching: isActivityFetching,
  } = useQuery({
    queryKey: ["admin", "activity", activityLimit],
    queryFn: () => getAdminActivity(activityLimit),
    refetchInterval: 15_000,
  });

  const orderByCode = useMemo(() => {
    const map = new Map<string, number>();
    (data?.sectionAdvancement ?? []).forEach((s) => map.set(s.code, s.order));
    return map;
  }, [data]);

  function pendingSectionCode(groupId: number): string {
    const cells = (matrix ?? []).filter((c) => c.groupId === groupId);
    const bySortOrder = (a: { sectionCode: string }, b: { sectionCode: string }) =>
      (orderByCode.get(a.sectionCode) ?? 0) - (orderByCode.get(b.sectionCode) ?? 0);
    const submitted = cells.filter((c) => c.status === "SUBMITTED").sort(bySortOrder);
    if (submitted[0]) return submitted[0].sectionCode;
    const inProgress = cells.filter((c) => c.status === "IN_PROGRESS").sort(bySortOrder);
    if (inProgress[0]) return inProgress[0].sectionCode;
    return "S01";
  }

  if (isLoading) {
    return <DashboardSkeleton />;
  }

  if (isError || !data) {
    return <DashboardError />;
  }

  return (
    <div className="space-y-6">
      <div>
        <h1>Tableau de bord administrateur</h1>
        <p className="text-[13px] text-muted-foreground mt-1">Suivi en temps réel du plan stratégique PSD 2027-2031</p>
      </div>

      <div className="grid grid-cols-2 lg:grid-cols-5 gap-4">
        <KpiCard
          icon={Users}
          label="Groupes actifs"
          value={`${data.activeGroups} / ${data.totalGroups}`}
          subtitle="groupes de travail"
          color="orange"
        />
        <KpiCard
          icon={TrendingUp}
          label="Complétion globale"
          value={`${data.globalCompletionPercent}%`}
          subtitle="sections soumises ou validées / total"
          color="emerald"
        />
        <KpiCard
          icon={Send}
          label="Sections soumises"
          value={data.sectionsSubmitted}
          subtitle="en attente de validation"
          color="blue"
        />
        <KpiCard
          icon={CheckCircle2}
          label="Sections validées"
          value={data.sectionsValidated}
          subtitle="validées par l'admin"
          color="violet"
        />
        <KpiCard
          icon={RotateCcw}
          label="En révision"
          value={data.sectionsRevisionRequested}
          subtitle="renvoyées pour correction"
          color="amber"
        />
      </div>

      <div className="grid grid-cols-1 lg:grid-cols-3 gap-4">
        <Card className="lg:col-span-2">
          <CardHeader>
            <CardTitle>Progression par groupe</CardTitle>
          </CardHeader>
          <CardContent>
            <GroupProgressChart groups={data.groups} />
          </CardContent>
        </Card>

        <Card>
          <CardHeader>
            <CardTitle>Activité en direct</CardTitle>
          </CardHeader>
          <CardContent className="max-h-[340px] overflow-y-auto scrollbar-thin">
            {isActivityError ? (
              <InlineError message="Impossible de charger l'activité récente." />
            ) : (
              <>
                <ActivityFeed entries={activity ?? []} />
                {(activity?.length ?? 0) >= activityLimit && activityLimit < ACTIVITY_MAX && (
                  <button
                    type="button"
                    onClick={() => setActivityLimit((l) => Math.min(l + ACTIVITY_PAGE_SIZE, ACTIVITY_MAX))}
                    disabled={isActivityFetching}
                    className="mt-3 w-full text-center text-[12px] font-medium text-primary-600 hover:text-primary-700 disabled:opacity-50"
                  >
                    {isActivityFetching ? "Chargement…" : "Voir plus"}
                  </button>
                )}
              </>
            )}
          </CardContent>
        </Card>
      </div>

      <Card>
        <CardHeader>
          <CardTitle>Avancement par section (groupes × sections)</CardTitle>
        </CardHeader>
        <CardContent>
          {isMatrixError ? (
            <InlineError message="Impossible de charger l'avancement par section." />
          ) : (
            <StatusHeatmap cells={matrix ?? []} />
          )}
        </CardContent>
      </Card>

      <Card>
        <CardHeader className="flex flex-row items-center justify-between">
          <CardTitle>Groupes de travail</CardTitle>
          <Link
            href="/admin/submissions?status=SUBMITTED"
            className="flex items-center gap-1 text-[13px] font-medium text-primary-600 hover:text-primary-700"
          >
            Voir toutes les soumissions <ArrowRight className="h-3.5 w-3.5" />
          </Link>
        </CardHeader>
        <CardContent className="p-0">
          <div className="divide-y divide-border">
            {data.groups.map((g) => (
              <Link
                key={g.groupId}
                href={`/admin/groups/${g.groupId}/sections/${pendingSectionCode(g.groupId)}`}
                className="flex items-center justify-between px-5 py-3 hover:bg-primary-50/60 dark:hover:bg-white/5 transition-colors"
              >
                <div>
                  <div className="flex items-center gap-2">
                    <p className="text-[13px] font-medium text-foreground">{g.groupName}</p>
                    <TypingIndicator groupId={g.groupId} />
                  </div>
                  <p className="text-[12px] text-muted-foreground">{g.leaderFullName}</p>
                </div>
                <div className="flex items-center gap-6 text-[13px] text-muted-foreground">
                  <span>{g.submitted} soumises</span>
                  <span>{g.validated} validées</span>
                  <span
                    className="font-semibold text-primary-600 w-12 text-right"
                    title="Part des sections soumises ou validées — les brouillons en cours ne sont pas comptés"
                  >
                    {g.completionPercent}%
                  </span>
                </div>
              </Link>
            ))}
          </div>
        </CardContent>
      </Card>
    </div>
  );
}

function TypingIndicator({ groupId }: { groupId: number }) {
  const isTyping = useIsGroupTyping(groupId);
  if (!isTyping) return null;
  return (
    <span className="flex items-center gap-1 text-[11px] text-blue-600 bg-blue-50 dark:text-blue-300 dark:bg-blue-500/15 rounded-full px-2 py-0.5">
      <span className="h-1.5 w-1.5 rounded-full bg-blue-500 animate-pulse" />
      en train de saisir
    </span>
  );
}

function DashboardError() {
  return (
    <div role="alert" className="flex flex-col items-center gap-3 py-24 text-center">
      <AlertTriangle className="h-8 w-8 text-amber-500" aria-hidden="true" />
      <p className="text-[14px] font-medium text-foreground">Impossible de charger le tableau de bord</p>
      <p className="text-[13px] text-muted-foreground">Vérifiez votre connexion, la page se rechargera automatiquement.</p>
    </div>
  );
}

function InlineError({ message }: { message: string }) {
  return (
    <div role="alert" className="flex items-center gap-2 py-6 text-[13px] text-amber-600 dark:text-amber-400">
      <AlertTriangle className="h-4 w-4 shrink-0" aria-hidden="true" />
      <span>{message}</span>
    </div>
  );
}

function DashboardSkeleton() {
  return (
    <div className="space-y-6 animate-pulse">
      <div className="h-8 w-96 bg-muted rounded" />
      <div className="grid grid-cols-2 lg:grid-cols-5 gap-4">
        {[...Array(5)].map((_, i) => (
          <div key={i} className="h-24 bg-muted rounded-xl" />
        ))}
      </div>
      <div className="h-80 bg-muted rounded-xl" />
    </div>
  );
}
