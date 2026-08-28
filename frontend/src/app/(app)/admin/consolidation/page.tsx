"use client";

import { useState } from "react";
import { useQuery } from "@tanstack/react-query";
import { toast } from "sonner";
import { FileDown, FileSpreadsheet } from "lucide-react";

import { Card, CardContent, CardHeader, CardTitle } from "@/components/ui/card";
import { Button } from "@/components/ui/button";
import { StatusHeatmap } from "@/components/charts/status-heatmap";
import { listGroups } from "@/lib/api/groups";
import { getAdminMatrix } from "@/lib/api/admin";
import { downloadConsolidatedExcelFull, downloadConsolidatedPdf } from "@/lib/api/exports";
import { extractErrorMessage } from "@/lib/api-client";

export default function AdminConsolidationPage() {
  const [exportingPdf, setExportingPdf] = useState(false);
  const [exportingExcel, setExportingExcel] = useState(false);

  const { data: groups } = useQuery({ queryKey: ["admin", "groups"], queryFn: listGroups });
  const { data: matrix, isError: isMatrixError } = useQuery({
    queryKey: ["admin", "matrix"],
    queryFn: getAdminMatrix,
  });

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

  return (
    <div className="space-y-6">
      <div>
        <h1>Document de consolidation</h1>
        <p className="text-[13px] text-muted-foreground mt-1">
          Toutes les réponses de toutes les directions, réunies dans un seul document — code couleur par direction —
          pour permettre de trancher directement.
        </p>
      </div>

      <Card>
        <CardHeader>
          <CardTitle>Télécharger le document consolidé</CardTitle>
        </CardHeader>
        <CardContent className="space-y-4">
          <p className="text-[13px] text-muted-foreground">
            Le document reprend les mêmes tableaux que le canevas de diagnostic, section par section, avec le
            contenu actuel de chaque direction identifié par sa couleur.
          </p>
          <div className="flex flex-wrap gap-3">
            <Button variant="primary" onClick={handleExportPdf} loading={exportingPdf}>
              <FileDown className="h-4 w-4" /> Télécharger le PDF consolidé
            </Button>
            <Button variant="secondary" onClick={handleExportExcel} loading={exportingExcel}>
              <FileSpreadsheet className="h-4 w-4" /> Télécharger l&apos;Excel consolidé
            </Button>
          </div>
        </CardContent>
      </Card>

      <Card>
        <CardHeader>
          <CardTitle>Légende des directions</CardTitle>
        </CardHeader>
        <CardContent>
          <div className="flex flex-wrap gap-4">
            {(groups ?? []).map((g) => (
              <span key={g.id} className="flex items-center gap-2 text-[13px] text-foreground">
                <span
                  className="h-3 w-3 shrink-0 rounded-full border border-border/60"
                  style={{ backgroundColor: g.color ?? "transparent" }}
                  title={g.color ?? undefined}
                />
                {g.name}
              </span>
            ))}
          </div>
        </CardContent>
      </Card>

      <Card>
        <CardHeader>
          <CardTitle>Avancement par section (groupes × sections)</CardTitle>
        </CardHeader>
        <CardContent>
          {isMatrixError ? (
            <p className="text-[13px] text-amber-600 dark:text-amber-400">
              Impossible de charger l&apos;avancement par section.
            </p>
          ) : (
            <StatusHeatmap cells={matrix ?? []} />
          )}
        </CardContent>
      </Card>
    </div>
  );
}
