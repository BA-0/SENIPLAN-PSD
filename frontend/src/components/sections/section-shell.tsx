"use client";

import Link from "next/link";
import { ChevronLeft, ChevronRight, Lock, Save } from "lucide-react";
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
  code: string;
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
  code,
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
      <div className="flex flex-wrap items-start justify-between gap-3">
        <div>
          <div className="flex items-center gap-2.5">
            <span className="rounded-md bg-primary-50 px-2 py-0.5 text-[13px] font-semibold text-primary-700">
              {code}
            </span>
            <h1>{title}</h1>
            <StatusBadge status={status} />
          </div>
          <div className="mt-1.5 h-5">
            <SaveIndicator status={saveStatus} savedAt={savedAt} />
          </div>
        </div>

        {!locked && (
          <div className="flex items-center gap-2">
            <Button variant="secondary" size="sm" onClick={onSaveNow}>
              <Save className="h-4 w-4" /> Enregistrer
            </Button>
            <AlertDialog>
              <AlertDialogTrigger asChild>
                <Button
                  variant="submit"
                  size="lg"
                  disabled={submitting || !!submitBlockedReason}
                  title={submitBlockedReason ?? undefined}
                >
                  Soumettre la section
                </Button>
              </AlertDialogTrigger>
              <AlertDialogContent>
                <AlertDialogHeader>
                  <AlertDialogTitle>Soumettre cette section ?</AlertDialogTitle>
                  <AlertDialogDescription>
                    Une fois soumise, la section ne sera plus modifiable, sauf si l&apos;administrateur la renvoie
                    pour révision.
                  </AlertDialogDescription>
                </AlertDialogHeader>
                <AlertDialogFooter>
                  <AlertDialogCancel>Annuler</AlertDialogCancel>
                  <AlertDialogAction onClick={onSubmit}>Confirmer la soumission</AlertDialogAction>
                </AlertDialogFooter>
              </AlertDialogContent>
            </AlertDialog>
          </div>
        )}
      </div>

      {locked && (
        <div className="flex items-center gap-2.5 rounded-lg border border-slate-200 bg-slate-50 px-4 py-3 text-[13px] text-slate-600">
          <Lock className="h-4 w-4 shrink-0" />
          Cette section est {status === "VALIDATED" ? "validée" : "soumise"} et n&apos;est plus modifiable.
        </div>
      )}

      {!locked && submitBlockedReason && (
        <div className="rounded-lg border border-amber-200 bg-amber-50 px-4 py-3 text-[13px] text-amber-700">
          {submitBlockedReason}
        </div>
      )}

      {status === "REVISION_REQUESTED" && adminComment && (
        <div className="rounded-lg border border-orange-200 bg-orange-50 px-4 py-3 text-[13px] text-orange-700">
          <span className="font-medium">Révision demandée par l&apos;administrateur : </span>
          {adminComment}
        </div>
      )}

      <div className="rounded-xl border border-slate-200 bg-white p-5 shadow-sm">{children}</div>

      <div className="flex items-center justify-between pt-4 border-t border-slate-200">
        {prevSection ? (
          <Button asChild variant="secondary" size="sm">
            <Link href={`/sections/${prevSection.code}`}>
              <ChevronLeft className="h-4 w-4" /> {prevSection.code} — {prevSection.title}
            </Link>
          </Button>
        ) : (
          <span />
        )}
        {nextSection ? (
          <Button asChild variant="secondary" size="sm">
            <Link href={`/sections/${nextSection.code}`}>
              {nextSection.code} — {nextSection.title} <ChevronRight className="h-4 w-4" />
            </Link>
          </Button>
        ) : (
          <span />
        )}
      </div>
    </div>
  );
}
