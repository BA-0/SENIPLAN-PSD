"use client";

import Link from "next/link";
import { useQuery } from "@tanstack/react-query";
import { ArrowRight, MessageSquare } from "lucide-react";

import { Card, CardContent, CardHeader, CardTitle } from "@/components/ui/card";
import { Button } from "@/components/ui/button";
import { StatusBadge } from "@/components/status-badge";
import { CompletionGauge } from "@/components/charts/completion-gauge";
import { getMyDashboard } from "@/lib/api/me";

export default function GroupDashboardPage() {
  const { data, isLoading } = useQuery({ queryKey: ["me", "dashboard"], queryFn: getMyDashboard });

  if (isLoading || !data) {
    return <DashboardSkeleton />;
  }

  return (
    <div className="space-y-6">
      <div>
        <h1>Tableau de bord — {data.groupName}</h1>
        <p className="text-[13px] text-slate-500 mt-1">
          Suivi de l&apos;avancement du diagnostic stratégique PSD 2027-2031
        </p>
      </div>

      <div className="grid grid-cols-1 lg:grid-cols-3 gap-4">
        <Card className="lg:col-span-1">
          <CardContent className="flex flex-col items-center justify-center pt-6">
            <CompletionGauge percent={data.completionPercent} />
            <p className="text-[13px] text-slate-500 mt-3">17 sections au total</p>
          </CardContent>
        </Card>

        <Card className="lg:col-span-2">
          <CardHeader>
            <CardTitle>Répartition par statut</CardTitle>
          </CardHeader>
          <CardContent className="grid grid-cols-2 sm:grid-cols-4 gap-4">
            <StatTile label="Non commencées" value={data.sectionsNotStarted} />
            <StatTile label="En cours" value={data.sectionsInProgress} />
            <StatTile label="Soumises" value={data.sectionsSubmitted} />
            <StatTile label="Validées" value={data.sectionsValidated} />
          </CardContent>
        </Card>
      </div>

      {data.sectionsWithAdminComment.length > 0 && (
        <Card>
          <CardHeader>
            <CardTitle>Commentaires de l&apos;administrateur</CardTitle>
          </CardHeader>
          <CardContent className="space-y-2">
            {data.sectionsWithAdminComment.map((s) => (
              <Link
                key={s.code}
                href={`/sections/${s.code}`}
                className="flex items-start gap-3 rounded-lg border border-orange-100 bg-orange-50/60 p-3 hover:bg-orange-50 transition-colors"
              >
                <MessageSquare className="h-4 w-4 text-orange-600 mt-0.5 shrink-0" />
                <div className="min-w-0">
                  <p className="text-[13px] font-medium text-slate-800">
                    {s.code} — {s.title}
                  </p>
                  <p className="text-[13px] text-slate-600 mt-0.5">{s.adminComment}</p>
                </div>
              </Link>
            ))}
          </CardContent>
        </Card>
      )}

      <Card>
        <CardHeader>
          <CardTitle>Checklist des 17 sections</CardTitle>
        </CardHeader>
        <CardContent className="p-0">
          <div className="divide-y divide-slate-100">
            {data.checklist.map((s) => (
              <Link
                key={s.code}
                href={`/sections/${s.code}`}
                className="flex items-center justify-between gap-4 px-5 py-3 hover:bg-primary-50/60 transition-colors"
              >
                <div className="flex items-center gap-3 min-w-0">
                  <span className="text-[13px] text-slate-400 w-8 shrink-0">{s.code}</span>
                  <span className="text-[13px] text-slate-800 truncate">{s.title}</span>
                </div>
                <div className="flex items-center gap-3 shrink-0">
                  <StatusBadge status={s.status} />
                  <ArrowRight className="h-4 w-4 text-slate-400" />
                </div>
              </Link>
            ))}
          </div>
        </CardContent>
      </Card>

      {data.nextSections.length > 0 && (
        <div className="flex justify-end">
          <Button asChild variant="primary">
            <Link href={`/sections/${data.nextSections[0].code}`}>
              Continuer avec {data.nextSections[0].code} <ArrowRight className="h-4 w-4" />
            </Link>
          </Button>
        </div>
      )}
    </div>
  );
}

function StatTile({ label, value }: { label: string; value: number }) {
  return (
    <div className="rounded-lg bg-slate-50 p-4">
      <p className="text-[30px] font-bold text-slate-800 tabular-nums leading-none">{value}</p>
      <p className="text-[13px] text-slate-500 mt-1.5">{label}</p>
    </div>
  );
}

function DashboardSkeleton() {
  return (
    <div className="space-y-6 animate-pulse">
      <div className="h-8 w-64 bg-slate-200 rounded" />
      <div className="grid grid-cols-1 lg:grid-cols-3 gap-4">
        <div className="h-52 bg-slate-200 rounded-xl lg:col-span-1" />
        <div className="h-52 bg-slate-200 rounded-xl lg:col-span-2" />
      </div>
      <div className="h-96 bg-slate-200 rounded-xl" />
    </div>
  );
}
