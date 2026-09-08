"use client";

import { useState } from "react";
import { useQuery } from "@tanstack/react-query";
import { toast } from "sonner";
import { FileDown, FileText } from "lucide-react";

import { Card, CardContent, CardHeader, CardTitle } from "@/components/ui/card";
import { Button } from "@/components/ui/button";
import { listGroups } from "@/lib/api/groups";
import { downloadSynthesisNotePdf, downloadSynthesisNoteWord } from "@/lib/api/exports";
import { extractErrorMessage } from "@/lib/api-client";

/**
 * Note de synthese : le resume de toutes les directions en trois a quatre pages. Distincte
 * des autres exports, qui couvrent soit une seule direction (plan sectoriel), soit toutes
 * les rubriques en detail (Document de consolidation, Plan Strategique de SENICO).
 */
export default function SynthesisPage() {
  const [exportingPdf, setExportingPdf] = useState(false);
  const [exportingWord, setExportingWord] = useState(false);

  const { data: groups } = useQuery({ queryKey: ["admin", "groups"], queryFn: listGroups });

  async function handleExport(kind: "pdf" | "word") {
    const setBusy = kind === "pdf" ? setExportingPdf : setExportingWord;
    setBusy(true);
    try {
      await (kind === "pdf" ? downloadSynthesisNotePdf() : downloadSynthesisNoteWord());
    } catch (error) {
      toast.error(extractErrorMessage(error, "Échec de la génération de la note de synthèse"));
    } finally {
      setBusy(false);
    }
  }

  return (
    <div className="space-y-6">
      <div>
        <h1>Note de synthèse</h1>
        <p className="text-[13px] text-muted-foreground mt-1">
          Le résumé de l&apos;ensemble des directions en trois à quatre pages, sans le détail des tableaux et sans
          reprendre les rubriques direction par direction. Pour un comité de pilotage.
        </p>
      </div>

      <Card>
        <CardHeader>
          <CardTitle>Télécharger la note de synthèse</CardTitle>
        </CardHeader>
        <CardContent className="space-y-4">
          <p className="text-[13px] text-muted-foreground">
            Établie à partir des sections <strong>soumises ou validées</strong> des{" "}
            {groups?.length ?? "…"} directions. Aucune validation préalable n&apos;est nécessaire ; les brouillons
            encore en cours sont en revanche écartés.
          </p>
          <div className="flex flex-wrap gap-3">
            <Button variant="primary" onClick={() => handleExport("pdf")} loading={exportingPdf}>
              <FileDown className="h-4 w-4" /> Télécharger le PDF
            </Button>
            <Button variant="secondary" onClick={() => handleExport("word")} loading={exportingWord}>
              <FileText className="h-4 w-4" /> Télécharger le Word
            </Button>
          </div>
        </CardContent>
      </Card>

      <Card>
        <CardHeader>
          <CardTitle>Ce que contient la note</CardTitle>
        </CardHeader>
        <CardContent>
          <ol className="space-y-2 text-[13px] text-foreground/90">
            <li>
              <strong>1. Chiffres clés</strong> — directions, axes, objectifs, actions, budget global, financement,
              évolution des effectifs.
            </li>
            <li>
              <strong>2. Où nous en sommes</strong> — le SWOT de toutes les directions fondu en un seul cadran, un
              élément cité par plusieurs directions n&apos;apparaissant qu&apos;une fois.
            </li>
            <li>
              <strong>3. Ce que nous voulons</strong> — les axes stratégiques et les objectifs spécifiques, dédoublonnés.
            </li>
            <li>
              <strong>4. Avec quels moyens</strong> — budget par axe et origine du financement, consolidés toutes
              directions.
            </li>
            <li>
              <strong>5. Défis prioritaires</strong> — les défis et enjeux, dédoublonnés entre directions.
            </li>
          </ol>
        </CardContent>
      </Card>
    </div>
  );
}
