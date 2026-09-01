"use client";

import { useEffect, useMemo, useState } from "react";
import { useQuery } from "@tanstack/react-query";
import { toast } from "sonner";
import { FileDown, FileSpreadsheet, Radio } from "lucide-react";

import { Card, CardContent, CardHeader, CardTitle } from "@/components/ui/card";
import { Button } from "@/components/ui/button";
import { StatusBadge } from "@/components/status-badge";
import { SectionFormRouter } from "@/components/sections/section-form-router";
import { listGroups } from "@/lib/api/groups";
import { listGroupSections, compareSection } from "@/lib/api/admin";
import { downloadConsolidatedExcelFull, downloadConsolidatedPdf } from "@/lib/api/exports";
import { extractErrorMessage } from "@/lib/api-client";
import { cn, formatDateTime } from "@/lib/utils";
import { useLiveConsolidationSync } from "@/hooks/use-live-consolidation-sync";
import { useConnectionStore } from "@/store/connection-store";
import { SECTION_CODES } from "@/types/common";
import type { SectionType } from "@/types/common";

/**
 * Regroupement purement presentatif des 17 sections du canevas (n'affecte pas
 * les donnees, qui restent lues telles quelles depuis l'API a chaque section).
 */
// Sections dont le formulaire est un (ou plusieurs) tableau large a nombreuses colonnes :
// on leur laisse toute la largeur disponible plutot que de les serrer dans une grille 2 colonnes,
// ce qui evite le defilement horizontal premature observe sur S01/S02/S03/S09-S16.
const WIDE_TABLE_TYPES = new Set<SectionType>([
  "STAKEHOLDERS",
  "RESOURCES_MATRIX",
  "PESTEL",
  "TOWS_MATRIX",
  "LOGICAL_FRAMEWORK",
  "ACTION_PLAN",
  "BUDGET",
  "PERFORMANCE_FRAMEWORK",
  "INDICATOR_SHEET",
  "RISK_MATRIX",
  "FINANCING_PLAN",
  "BUSINESS_PLAN",
]);

const SECTION_GROUPS: Record<string, (typeof SECTION_CODES)[number][]> = {
  Diagnostic: ["S01", "S02", "S03", "S04", "S05", "S06", "S07", "S07B"],
  "Stratégie": ["S08", "S09"],
  "Mise en œuvre": ["S10", "S11", "S12", "S13"],
  "Risques & financement": ["S14", "S15", "S16"],
  "Synthèse": ["S17"],
};

export default function LiveConsolidationPage() {
  useLiveConsolidationSync();
  const connected = useConnectionStore((s) => s.connected);

  const [activeCode, setActiveCode] = useState<string>(SECTION_CODES[0]);
  const [exportingPdf, setExportingPdf] = useState(false);
  const [exportingExcel, setExportingExcel] = useState(false);

  async function handleExportPdf() {
    setExportingPdf(true);
    try {
      await downloadConsolidatedPdf();
    } catch (error) {
      toast.error(extractErrorMessage(error, "Échec de l'export PDF"));
    } finally {
      setExportingPdf(false);
    }
  }

  async function handleExportExcel() {
    setExportingExcel(true);
    try {
      await downloadConsolidatedExcelFull();
    } catch (error) {
      toast.error(extractErrorMessage(error, "Échec de l'export Excel"));
    } finally {
      setExportingExcel(false);
    }
  }

  const { data: groups, isLoading: groupsLoading } = useQuery({
    queryKey: ["admin", "groups"],
    queryFn: listGroups,
  });
  const groupIds = useMemo(() => (groups ?? []).map((g) => g.id), [groups]);
  const firstGroupId = groupIds[0];

  // Source des titres/ordre officiels des sections : le referentiel exact utilise pour la saisie,
  // interroge via un groupe existant (le contenu par groupe est lu a part, via /admin/compare).
  const { data: sectionDefs } = useQuery({
    queryKey: ["admin", "groups", firstGroupId, "sections"],
    queryFn: () => listGroupSections(firstGroupId as number),
    enabled: !!firstGroupId,
  });
  const titleByCode = useMemo(() => {
    const map = new Map<string, string>();
    (sectionDefs ?? []).forEach((s) => map.set(s.code, s.title));
    return map;
  }, [sectionDefs]);

  const {
    data: comparisons,
    isFetching: sectionFetching,
    isError: sectionError,
  } = useQuery({
    queryKey: ["admin", "live", "compare", activeCode, groupIds],
    queryFn: () => compareSection(activeCode, groupIds),
    enabled: groupIds.length > 0,
    refetchInterval: 20_000,
  });

  // Si le referentiel des sections arrive apres coup et ne contient pas le code actif
  // (ne devrait pas arriver, les 18 codes sont fixes), on ne change rien : SECTION_CODES fait foi.
  useEffect(() => {
    if (!SECTION_CODES.includes(activeCode as (typeof SECTION_CODES)[number])) {
      setActiveCode(SECTION_CODES[0]);
    }
  }, [activeCode]);

  const orderedComparisons = useMemo(() => {
    if (!comparisons) return [];
    const orderIndex = new Map(groupIds.map((id, i) => [id, i]));
    return [...comparisons].sort((a, b) => (orderIndex.get(a.groupId) ?? 0) - (orderIndex.get(b.groupId) ?? 0));
  }, [comparisons, groupIds]);

  const groupColorById = useMemo(() => {
    const map = new Map<number, string | null>();
    (groups ?? []).forEach((g) => map.set(g.id, g.color));
    return map;
  }, [groups]);

  const isWideSection = WIDE_TABLE_TYPES.has(orderedComparisons[0]?.type as SectionType);
  const gridClass = cn("grid gap-4", isWideSection ? "grid-cols-1" : "grid-cols-1 xl:grid-cols-2");

  return (
    <div className="space-y-5">
      <div className="flex flex-wrap items-start justify-between gap-3">
        <div>
          <h1>Consolidation — vue en direct</h1>
          <p className="text-[13px] text-muted-foreground mt-1">
            Les 17 sections, dans les mêmes formulaires que ceux remplis par les directions — sans export, toujours
            à jour.
          </p>
        </div>
        <div className="flex flex-wrap items-center gap-2 shrink-0">
          <Button variant="secondary" size="sm" onClick={handleExportPdf} loading={exportingPdf}>
            <FileDown className="h-4 w-4" /> Télécharger le PDF consolidé
          </Button>
          <Button variant="secondary" size="sm" onClick={handleExportExcel} loading={exportingExcel}>
            <FileSpreadsheet className="h-4 w-4" /> Télécharger l&apos;Excel consolidé
          </Button>
          <LiveIndicator connected={connected} />
        </div>
      </div>

      <div className="grid grid-cols-1 lg:grid-cols-[240px_1fr] gap-4 items-start">
        <nav className="space-y-4 lg:sticky lg:top-4">
          {Object.entries(SECTION_GROUPS).map(([groupLabel, codes]) => (
            <div key={groupLabel}>
              <p className="px-2 pb-1 text-[11px] font-semibold uppercase tracking-wider text-muted-foreground">
                {groupLabel}
              </p>
              <div className="space-y-0.5">
                {codes.map((code) => (
                  <button
                    key={code}
                    type="button"
                    onClick={() => setActiveCode(code)}
                    className={cn(
                      "flex w-full items-center gap-2 rounded-lg px-2.5 py-1.5 text-left text-[13px] transition-colors duration-150",
                      activeCode === code
                        ? "bg-primary-50 text-primary-800 dark:bg-primary-500/15 dark:text-primary-200 font-medium"
                        : "text-foreground/80 hover:bg-muted"
                    )}
                  >
                    <span className="text-[11px] font-mono text-muted-foreground w-9 shrink-0">{code}</span>
                    <span className="truncate">{titleByCode.get(code) ?? "…"}</span>
                  </button>
                ))}
              </div>
            </div>
          ))}
        </nav>

        <div className="space-y-4 min-w-0">
          <div className="flex items-baseline gap-2">
            <span className="text-[11px] font-mono rounded-md bg-primary-50 dark:bg-primary-500/15 text-primary-700 dark:text-primary-300 px-2 py-0.5">
              {activeCode}
            </span>
            <h2 className="text-[17px] font-semibold text-foreground">
              {titleByCode.get(activeCode) ?? "Chargement…"}
            </h2>
          </div>

          {(groupsLoading || (sectionFetching && !comparisons)) && (
            <div className="grid grid-cols-1 gap-4">
              {Array.from({ length: 3 }).map((_, i) => (
                <div key={i} className="h-40 bg-muted rounded-xl animate-pulse" />
              ))}
            </div>
          )}

          {sectionError && (
            <p className="text-[13px] text-amber-600 dark:text-amber-400">
              Impossible de charger cette section pour le moment.
            </p>
          )}

          {!!comparisons && (
            <div className={gridClass}>
              {orderedComparisons.map((response) => (
                <Card
                  key={response.groupId}
                  className="border-l-[3px] overflow-hidden"
                  style={{ borderLeftColor: groupColorById.get(response.groupId) ?? undefined }}
                >
                  <CardHeader className="flex flex-row items-center justify-between gap-2">
                    <CardTitle className="text-[15px] flex items-center gap-2">
                      <span
                        className="h-2.5 w-2.5 shrink-0 rounded-full"
                        style={{ backgroundColor: groupColorById.get(response.groupId) ?? "transparent" }}
                      />
                      {response.groupName}
                    </CardTitle>
                    <div className="flex items-center gap-2 shrink-0">
                      <StatusBadge status={response.status} />
                    </div>
                  </CardHeader>
                  <CardContent>
                    {(response.submittedAt || response.validatedAt) && (
                      <div className="mb-3 flex flex-wrap gap-x-4 gap-y-1 text-[11.5px] text-muted-foreground">
                        {response.submittedAt && <span>Soumis le {formatDateTime(response.submittedAt)}</span>}
                        {response.validatedAt && <span>Validé le {formatDateTime(response.validatedAt)}</span>}
                        <span className="text-muted-foreground/70">v{response.version}</span>
                      </div>
                    )}
                    <SectionFormRouter
                      type={response.type as SectionType}
                      content={response.content}
                      onChange={() => {}}
                      readOnly
                    />
                  </CardContent>
                </Card>
              ))}
            </div>
          )}
        </div>
      </div>
    </div>
  );
}

function LiveIndicator({ connected }: { connected: boolean }) {
  return (
    <div
      className={cn(
        "flex items-center gap-2 rounded-full px-3 py-1.5 text-[12px] font-medium border shrink-0",
        connected
          ? "border-primary-200 bg-primary-50 text-primary-700 dark:border-primary-500/30 dark:bg-primary-500/10 dark:text-primary-300"
          : "border-border bg-muted text-muted-foreground"
      )}
      title={connected ? "Connecté au flux temps réel — la page se met à jour automatiquement" : "Reconnexion en cours…"}
    >
      <span className="relative flex h-2 w-2">
        {connected && (
          <span className="absolute inline-flex h-full w-full animate-ping rounded-full bg-primary-400 opacity-75" />
        )}
        <span className={cn("relative inline-flex h-2 w-2 rounded-full", connected ? "bg-primary-500" : "bg-muted-foreground/50")} />
      </span>
      <Radio className="h-3.5 w-3.5" strokeWidth={2} />
      {connected ? "En direct" : "Connexion…"}
    </div>
  );
}
