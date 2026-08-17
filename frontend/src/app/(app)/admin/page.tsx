"use client";

import Link from "next/link";
import { useQuery } from "@tanstack/react-query";
import { Users, TrendingUp, Send, CheckCircle2 } from "lucide-react";

import { Card, CardContent, CardHeader, CardTitle } from "@/components/ui/card";
import { GroupProgressChart } from "@/components/charts/group-progress-chart";
import { StatusHeatmap } from "@/components/charts/status-heatmap";
import { ActivityFeed } from "@/components/activity-feed";
import { getAdminActivity, getAdminDashboard, getAdminMatrix } from "@/lib/api/admin";
import { useRealtimeAdmin } from "@/hooks/use-realtime-admin";
import { useIsGroupTyping } from "@/store/presence-store";

export default function AdminDashboardPage() {
  useRealtimeAdmin();

  const { data, isLoading } = useQuery({
    queryKey: ["admin", "dashboard"],
    queryFn: getAdminDashboard,
    refetchInterval: 15_000,
  });

  const { data: matrix } = useQuery({
    queryKey: ["admin", "matrix"],
    queryFn: getAdminMatrix,
    refetchInterval: 15_000,
  });

  const { data: activity } = useQuery({
    queryKey: ["admin", "activity"],
    queryFn: () => getAdminActivity(20),
    refetchInterval: 15_000,
  });

  if (isLoading || !data) {
    return <DashboardSkeleton />;
  }

  return (
    <div className="space-y-6">
      <div>
        <h1>Tableau de bord administrateur</h1>
        <p className="text-[13px] text-slate-500 mt-1">Suivi en temps réel du diagnostic stratégique PSD 2027-2031</p>
      </div>

      <div className="grid grid-cols-2 lg:grid-cols-4 gap-4">
        <KpiCard icon={Users} label="Groupes actifs" value={`${data.activeGroups} / ${data.totalGroups}`} />
        <KpiCard icon={TrendingUp} label="Complétion globale" value={`${data.globalCompletionPercent}%`} />
        <KpiCard icon={Send} label="Sections soumises" value={data.sectionsSubmitted} />
        <KpiCard icon={CheckCircle2} label="Sections validées" value={data.sectionsValidated} />
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
            <ActivityFeed entries={activity ?? []} />
          </CardContent>
        </Card>
      </div>

      <Card>
        <CardHeader>
          <CardTitle>Avancement par section (groupes × sections)</CardTitle>
        </CardHeader>
        <CardContent>
          <StatusHeatmap cells={matrix ?? []} />
        </CardContent>
      </Card>

      <Card>
        <CardHeader>
          <CardTitle>Groupes de travail</CardTitle>
        </CardHeader>
        <CardContent className="p-0">
          <div className="divide-y divide-slate-100">
            {data.groups.map((g) => (
              <Link
                key={g.groupId}
                href={`/admin/groups/${g.groupId}/sections/S01`}
                className="flex items-center justify-between px-5 py-3 hover:bg-primary-50/60 transition-colors"
              >
                <div>
                  <div className="flex items-center gap-2">
                    <p className="text-[13px] font-medium text-slate-800">{g.groupName}</p>
                    <TypingIndicator groupId={g.groupId} />
                  </div>
                  <p className="text-[12px] text-slate-500">{g.leaderFullName}</p>
                </div>
                <div className="flex items-center gap-6 text-[13px] text-slate-600">
                  <span>{g.submitted} soumises</span>
                  <span>{g.validated} validées</span>
                  <span className="font-semibold text-primary-600 w-12 text-right">{g.completionPercent}%</span>
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
    <span className="flex items-center gap-1 text-[11px] text-blue-600 bg-blue-50 rounded-full px-2 py-0.5">
      <span className="h-1.5 w-1.5 rounded-full bg-blue-500 animate-pulse" />
      en train de saisir
    </span>
  );
}

function KpiCard({ icon: Icon, label, value }: { icon: React.ElementType; label: string; value: string | number }) {
  return (
    <Card>
      <CardContent className="pt-5 flex items-start justify-between">
        <div>
          <p className="text-[13px] text-slate-500">{label}</p>
          <p className="text-[30px] font-bold text-slate-800 tabular-nums leading-tight mt-1">{value}</p>
        </div>
        <div className="h-10 w-10 rounded-lg bg-primary-50 flex items-center justify-center">
          <Icon className="h-5 w-5 text-primary-600" strokeWidth={1.75} />
        </div>
      </CardContent>
    </Card>
  );
}

function DashboardSkeleton() {
  return (
    <div className="space-y-6 animate-pulse">
      <div className="h-8 w-96 bg-slate-200 rounded" />
      <div className="grid grid-cols-2 lg:grid-cols-4 gap-4">
        {[...Array(4)].map((_, i) => (
          <div key={i} className="h-24 bg-slate-200 rounded-xl" />
        ))}
      </div>
      <div className="h-80 bg-slate-200 rounded-xl" />
    </div>
  );
}
