"use client";

import Link from "next/link";
import { ChevronLeft, ChevronRight, Lock, Save, Send } from "lucide-react";
import { Button } from "@/components/ui/button";
import { StatusBadge } from "@/components/status-badge";
import { SaveIndicator } from "./save-indicator";
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
import type { SaveStatus } from "@/hooks/use-section-autosave";
import type { SectionStatus } from "@/types/common";

interface SectionShellProps {
  title: string;
  status: SectionStatus;
  locked: boolean;
  adminComment?: string | null;
  saveStatus: SaveStatus;
  savedAt: Date | null;
  onSaveNow: () => void;
  onSubmit: () => void;
  submitting?: boolean;
  submitBlockedReason?: string | null;
  prevSection?: { code: string; title: string } | null;
  nextSection?: { code: string; title: string } | null;
  children: React.ReactNode;
}

export function SectionShell({
  title,
  status,
  locked,
  adminComment,
  saveStatus,
  savedAt,
  onSaveNow,
  onSubmit,
  submitting,
  submitBlockedReason,
  prevSection,
  nextSection,
  children,
}: SectionShellProps) {
  return (
    <div className="space-y-5">
      <div className="flex items-center gap-2.5">
        <h1>{title}</h1>
        <StatusBadge status={status} />
      </div>

      {/* Barre d'actions collee en haut au defilement : les formulaires sont longs, et il ne faut
          jamais remonter toute la page pour enregistrer ou soumettre. */}
      <div className="sticky top-16 z-[5] flex flex-wrap items-center gap-2 rounded-xl border border-border bg-card/95 px-4 py-2.5 shadow-sm backdrop-blur">
        <div className="mr-auto min-h-5">
          {locked ? (
            <span className="flex items-center gap-1.5 text-[13px] text-muted-foreground">
              <Lock className="h-3.5 w-3.5" />
              {status === "VALIDATED"
                ? "Section validée — elle n'est plus modifiable."
                : "Section soumise — en attente de validation par la Direction Générale."}
            </span>
          ) : (
            <SaveIndicator status={saveStatus} savedAt={savedAt} />
          )}
        </div>

        {!locked && (
          <>
            <Button variant="secondary" size="sm" onClick={onSaveNow} disabled={saveStatus === "saving"}>
              <Save className="h-4 w-4" /> Enregistrer
            </Button>
            <AlertDialog>
              <AlertDialogTrigger asChild>
                <Button
                  variant="submit"
                  disabled={submitting || !!submitBlockedReason}
                  title={submitBlockedReason ?? undefined}
                >
                  <Send className="h-4 w-4" /> Soumettre
                </Button>
              </AlertDialogTrigger>
              <AlertDialogContent>
                <AlertDialogHeader>
                  <AlertDialogTitle>Soumettre cette section ?</AlertDialogTitle>
                  <AlertDialogDescription>
                    Votre saisie est enregistrée puis transmise à la Direction Générale pour validation. La section ne
                    sera plus modifiable, sauf si elle vous est renvoyée pour révision.
                    {nextSection && " Vous passerez ensuite à la section suivante."}
                  </AlertDialogDescription>
                </AlertDialogHeader>
                <AlertDialogFooter>
                  <AlertDialogCancel>Annuler</AlertDialogCancel>
                  <AlertDialogAction onClick={onSubmit}>Soumettre</AlertDialogAction>
                </AlertDialogFooter>
              </AlertDialogContent>
            </AlertDialog>
          </>
        )}
      </div>

      {!locked && submitBlockedReason && (
        <div className="rounded-lg border border-amber-200 dark:border-amber-500/30 bg-amber-50 dark:bg-amber-500/10 px-4 py-3 text-[13px] text-amber-700 dark:text-amber-300">
          {submitBlockedReason}
        </div>
      )}

      {status === "REVISION_REQUESTED" && adminComment && (
        <div className="rounded-lg border border-orange-200 dark:border-orange-500/30 bg-orange-50 dark:bg-orange-500/10 px-4 py-3 text-[13px] text-orange-700 dark:text-orange-300">
          <span className="font-medium">Révision demandée : </span>
          {adminComment}
        </div>
      )}

      <div className="rounded-xl border border-border bg-card p-5 shadow-sm">{children}</div>

      <div className="flex flex-col sm:flex-row items-stretch sm:items-center justify-between gap-2 pt-4 border-t border-border">
        {prevSection ? (
          <Button asChild variant="secondary" size="sm" className="justify-start sm:justify-center">
            <Link href={`/sections/${prevSection.code}`} className="min-w-0">
              <ChevronLeft className="h-4 w-4 shrink-0" /> <span className="truncate">{prevSection.title}</span>
            </Link>
          </Button>
        ) : (
          <span className="hidden sm:inline" />
        )}
        {nextSection ? (
          <Button asChild variant="secondary" size="sm" className="justify-end sm:justify-center">
            <Link href={`/sections/${nextSection.code}`} className="min-w-0">
              <span className="truncate">{nextSection.title}</span> <ChevronRight className="h-4 w-4 shrink-0" />
            </Link>
          </Button>
        ) : (
          <span className="hidden sm:inline" />
        )}
      </div>
    </div>
  );
}
