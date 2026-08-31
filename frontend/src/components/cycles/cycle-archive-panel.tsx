"use client";

import { useState } from "react";
import { useQuery } from "@tanstack/react-query";

import { Dialog, DialogContent, DialogHeader, DialogTitle } from "@/components/ui/dialog";
import { Button } from "@/components/ui/button";
import { StatusBadge } from "@/components/status-badge";
import { SectionFormRouter } from "@/components/sections/section-form-router";
import { formatDateTime } from "@/lib/utils";
import type { GroupCycleSectionContentDto, GroupCycleSummaryDto } from "@/types/api";
import type { SectionStatusSummary, SectionType } from "@/types/common";

interface CycleArchivePanelProps {
  open: boolean;
  onOpenChange: (open: boolean) => void;
  title: string;
  queryKeyPrefix: (string | number)[];
  listCycles: () => Promise<GroupCycleSummaryDto[]>;
  listSections: (cycleNumber: number) => Promise<SectionStatusSummary[]>;
  getSectionContent: (cycleNumber: number, code: string) => Promise<GroupCycleSectionContentDto>;
}

/**
 * Consultation en lecture seule des cycles de saisie clôturés : liste des cycles, puis des 17
 * sections archivées de chaque cycle, puis le contenu figé de chaque section. Rien n'est
 * modifiable ici — c'est l'historique des anciennes saisies soumises, jamais effacé.
 */
export function CycleArchivePanel({
  open,
  onOpenChange,
  title,
  queryKeyPrefix,
  listCycles,
  listSections,
  getSectionContent,
}: CycleArchivePanelProps) {
  const [openCycle, setOpenCycle] = useState<number | null>(null);
  const [openSection, setOpenSection] = useState<string | null>(null);

  const { data: cycles, isLoading } = useQuery({
    queryKey: [...queryKeyPrefix, "cycles"],
    queryFn: listCycles,
    enabled: open,
  });

  const { data: sections, isFetching: isFetchingSections } = useQuery({
    queryKey: [...queryKeyPrefix, "cycles", openCycle, "sections"],
    queryFn: () => listSections(openCycle as number),
    enabled: openCycle !== null,
  });

  const { data: sectionContent, isFetching: isFetchingContent } = useQuery({
    queryKey: [...queryKeyPrefix, "cycles", openCycle, "sections", openSection],
    queryFn: () => getSectionContent(openCycle as number, openSection as string),
    enabled: openCycle !== null && openSection !== null,
  });

  return (
    <>
      <Dialog
        open={open}
        onOpenChange={(v) => {
          onOpenChange(v);
          if (!v) {
            setOpenCycle(null);
            setOpenSection(null);
          }
        }}
      >
        <DialogContent className="max-w-2xl">
          <DialogHeader>
            <DialogTitle>{title}</DialogTitle>
          </DialogHeader>
          {isLoading && <p className="text-[13px] text-muted-foreground py-6 text-center">Chargement…</p>}
          {!isLoading && (!cycles || cycles.length === 0) && (
            <p className="text-[13px] text-muted-foreground py-6 text-center">
              Aucun cycle clôturé pour l&apos;instant. Une fois qu&apos;un nouveau cycle sera démarré, l&apos;ancienne
              saisie soumise apparaîtra ici, consultable en lecture seule.
            </p>
          )}
          {cycles && cycles.length > 0 && (
            <div className="divide-y divide-border/60">
              {cycles.map((c) => (
                <div key={c.cycleNumber} className="flex items-center justify-between py-3">
                  <div>
                    <p className="text-[13px] font-medium text-foreground">Cycle {c.cycleNumber}</p>
                    <p className="text-[12px] text-muted-foreground mt-0.5">
                      Clôturé le {formatDateTime(c.archivedAt)}
                      {c.archivedByName ? ` par ${c.archivedByName}` : ""} · {c.sectionsCount} sections
                    </p>
                  </div>
                  <Button variant="ghost" size="sm" onClick={() => setOpenCycle(c.cycleNumber)}>
                    Consulter
                  </Button>
                </div>
              ))}
            </div>
          )}
        </DialogContent>
      </Dialog>

      <Dialog
        open={openCycle !== null}
        onOpenChange={(v) => {
          if (!v) {
            setOpenCycle(null);
            setOpenSection(null);
          }
        }}
      >
        <DialogContent className="max-w-2xl">
          <DialogHeader>
            <DialogTitle>Cycle {openCycle} — sections archivées</DialogTitle>
          </DialogHeader>
          {isFetchingSections && <div className="h-48 bg-muted rounded-xl animate-pulse" />}
          {sections && (
            <div className="divide-y divide-border/60">
              {sections.map((s) => (
                <div key={s.code} className="flex items-center justify-between py-3">
                  <div className="flex items-center gap-3 min-w-0">
                    <span className="text-[13px] text-muted-foreground w-10 shrink-0">{s.code}</span>
                    <span className="text-[13px] text-foreground truncate">{s.title}</span>
                  </div>
                  <div className="flex items-center gap-3 shrink-0">
                    <StatusBadge status={s.status} />
                    <Button variant="ghost" size="sm" onClick={() => setOpenSection(s.code)}>
                      Consulter
                    </Button>
                  </div>
                </div>
              ))}
            </div>
          )}
        </DialogContent>
      </Dialog>

      <Dialog open={openSection !== null} onOpenChange={(v) => !v && setOpenSection(null)}>
        <DialogContent className="max-w-3xl">
          <DialogHeader>
            <DialogTitle>
              {sectionContent ? `${sectionContent.title} — cycle ${sectionContent.cycleNumber}` : openSection}
            </DialogTitle>
          </DialogHeader>
          {isFetchingContent || !sectionContent ? (
            <div className="h-64 bg-muted rounded-xl animate-pulse" />
          ) : (
            <SectionFormRouter
              type={sectionContent.type as SectionType}
              content={sectionContent.content}
              onChange={() => {}}
              readOnly
            />
          )}
        </DialogContent>
      </Dialog>
    </>
  );
}
