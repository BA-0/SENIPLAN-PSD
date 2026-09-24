"use client";

import Link from "next/link";
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { toast } from "sonner";
import { ArrowRight, CheckCheck, ShieldCheck } from "lucide-react";

import { Button } from "@/components/ui/button";
import {
  AlertDialog,
  AlertDialogAction,
  AlertDialogCancel,
  AlertDialogContent,
  AlertDialogDescription,
  AlertDialogFooter,
  AlertDialogHeader,
  AlertDialogTitle,
  AlertDialogTrigger,
} from "@/components/ui/alert-dialog";
import { dgApproveAllPending, getAdminSubmissions } from "@/lib/api/admin";
import { extractErrorMessage } from "@/lib/api-client";
import { fileDuDg, lienSection } from "@/lib/dg-queue";

/**
 * Ce qui attend le DG, des son arrivee sur le tableau de bord (cf. fileDuDg). Deux facons d'en
 * finir — les relire une a une, ou tout valider d'un clic : les sections entrent aussitot dans les
 * documents consolides.
 */
export function DgApprovalBanner() {
  const queryClient = useQueryClient();
  const { data: submissions } = useQuery({
    queryKey: ["admin", "submissions"],
    queryFn: getAdminSubmissions,
    refetchInterval: 15_000,
  });
  const aTraiter = fileDuDg(submissions);

  const toutValider = useMutation({
    mutationFn: () => dgApproveAllPending(),
    onSuccess: (result) => {
      toast.success(`${result.approvedCount} section(s) validée(s) — elles entrent dans les documents consolidés`);
      queryClient.invalidateQueries({ queryKey: ["admin"] });
    },
    onError: (error) => toast.error(extractErrorMessage(error, "Échec de la validation")),
  });

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
      <Button asChild variant="secondary">
        <Link href={lienSection(aTraiter[0])}>
          Commencer la revue <ArrowRight className="h-4 w-4" />
        </Link>
      </Button>
      <AlertDialog>
        <AlertDialogTrigger asChild>
          <Button disabled={toutValider.isPending}>
            <CheckCheck className="h-4 w-4" /> Tout valider ({n})
          </Button>
        </AlertDialogTrigger>
        <AlertDialogContent>
          <AlertDialogHeader>
            <AlertDialogTitle>Valider les {n} sections en attente ?</AlertDialogTitle>
            <AlertDialogDescription>
              Toutes les sections en attente seront validées, toutes directions confondues. Elles entreront aussitôt
              dans le Document de consolidation, la Note de synthèse et le Plan Stratégique de SENICO.
            </AlertDialogDescription>
          </AlertDialogHeader>
          <AlertDialogFooter>
            <AlertDialogCancel>Annuler</AlertDialogCancel>
            <AlertDialogAction onClick={() => toutValider.mutate()}>Tout valider</AlertDialogAction>
          </AlertDialogFooter>
        </AlertDialogContent>
      </AlertDialog>
    </div>
  );
}
