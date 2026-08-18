"use client";

import Link from "next/link";
import { useQuery } from "@tanstack/react-query";
import { ArrowRight, CheckCircle2, Circle, Loader2, MessageSquare, Send } from "lucide-react";

import { Card, CardContent, CardHeader, CardTitle } from "@/components/ui/card";
import { Button } from "@/components/ui/button";
import { StatusBadge } from "@/components/status-badge";
import { CompletionGauge } from "@/components/charts/completion-gauge";
import { KpiCard } from "@/components/kpi-card";
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
        <p className="text-[13px] text-muted-foreground mt-1">
          Suivi de l&apos;avancement du plan stratégique PSD 2027-2031
        </p>
      </div>

      <div className="grid grid-cols-1 lg:grid-cols-3 gap-4">
        <Card className="lg:col-span-1 bg-violet-200/70 border-violet-300 dark:bg-violet-500/30 dark:border-violet-500/40">
          <CardContent className="flex flex-col items-center justify-center pt-6">
            <CompletionGauge percent={data.completionPercent} />
            <p className="text-[13px] text-muted-foreground mt-3">17 sections au total</p>
          </CardContent>
        </Card>

        <div className="lg:col-span-2 grid grid-cols-2 gap-4">
          <KpiCard icon={Circle} label="Non commencées" value={data.sectionsNotStarted} subtitle="sur 17 sections" color="slate" />
          <KpiCard icon={Loader2} label="En cours" value={data.sectionsInProgress} subtitle="en cours de saisie" color="blue" />
          <KpiCard icon={Send} label="Soumises" value={data.sectionsSubmitted} subtitle="en attente de validation" color="orange" />
          <KpiCard icon={CheckCircle2} label="Validées" value={data.sectionsValidated} subtitle="validées par l'admin" color="emerald" />
        </div>
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
                className="flex items-start gap-3 rounded-lg border border-orange-100 dark:border-orange-500/20 bg-orange-50/60 dark:bg-orange-500/10 p-3 hover:bg-orange-50 dark:hover:bg-orange-500/15 transition-colors"
              >
                <MessageSquare className="h-4 w-4 text-orange-600 dark:text-orange-400 mt-0.5 shrink-0" />
                <div className="min-w-0">
                  <p className="text-[13px] font-medium text-foreground">
                    {s.code} — {s.title}
                  </p>
                  <p className="text-[13px] text-muted-foreground mt-0.5">{s.adminComment}</p>
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
          <div className="divide-y divide-border">
            {data.checklist.map((s) => (
              <Link
                key={s.code}
                href={`/sections/${s.code}`}
                className="flex items-center justify-between gap-4 px-5 py-3 hover:bg-primary-50/60 dark:hover:bg-white/5 transition-colors"
              >
                <div className="flex items-center gap-3 min-w-0">
                  <span className="text-[13px] text-muted-foreground w-8 shrink-0">{s.code}</span>
                  <span className="text-[13px] text-foreground truncate">{s.title}</span>
                </div>
                <div className="flex items-center gap-3 shrink-0">
                  <StatusBadge status={s.status} />
                  <ArrowRight className="h-4 w-4 text-muted-foreground" />
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

function DashboardSkeleton() {
  return (
    <div className="space-y-6 animate-pulse">
      <div className="h-8 w-64 bg-muted rounded" />
      <div className="grid grid-cols-1 lg:grid-cols-3 gap-4">
        <div className="h-52 bg-muted rounded-xl lg:col-span-1" />
        <div className="h-52 bg-muted rounded-xl lg:col-span-2" />
      </div>
      <div className="h-96 bg-muted rounded-xl" />
    </div>
  );
}
