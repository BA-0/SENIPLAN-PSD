"use client";

import Link from "next/link";
import { useQuery } from "@tanstack/react-query";
import { ArrowRight, ShieldCheck } from "lucide-react";

import { Button } from "@/components/ui/button";
import { getAdminSubmissions } from "@/lib/api/admin";
import { fileDuDg } from "@/lib/dg-queue";

/**
 * Ce qui attend le DG, des son arrivee sur le tableau de bord (cf. fileDuDg). La validation ne se
 * fait que depuis l'onglet « À valider » : le bandeau se contente d'y mener.
 */
export function DgApprovalBanner() {
  const { data: submissions } = useQuery({
    queryKey: ["admin", "submissions"],
    queryFn: getAdminSubmissions,
    refetchInterval: 15_000,
  });
  const aTraiter = fileDuDg(submissions);

  if (!submissions) return null;

  if (aTraiter.length === 0) {
    return (
      <div className="flex items-center gap-2 rounded-xl border border-emerald-200 bg-emerald-50/80 px-4 py-3 text-[13px] text-emerald-800 dark:border-emerald-500/30 dark:bg-emerald-500/10 dark:text-emerald-300">
        <ShieldCheck className="h-4 w-4 shrink-0" /> Aucune section n&apos;attend votre validation.
      </div>
    );
  }

  const directions = new Set(aTraiter.map((s) => s.groupId)).size;
  const n = aTraiter.length;

  return (
    <div className="flex flex-wrap items-center gap-3 rounded-xl border border-amber-200 bg-amber-50/90 px-4 py-3 dark:border-amber-500/30 dark:bg-amber-500/10">
      <ShieldCheck className="h-5 w-5 shrink-0 text-amber-600 dark:text-amber-400" />
      <p className="mr-auto text-[14px] text-foreground">
        <span className="font-semibold">
          {n} section{n > 1 ? "s" : ""} attend{n > 1 ? "ent" : ""} votre validation
        </span>
        <span className="text-muted-foreground">
          {" "}
          · {directions} direction{directions > 1 ? "s" : ""}
        </span>
      </p>
      <Button asChild>
        <Link href="/admin/submissions?dg=PENDING">
          Aller à « À valider » <ArrowRight className="h-4 w-4" />
        </Link>
      </Button>
    </div>
  );
}
