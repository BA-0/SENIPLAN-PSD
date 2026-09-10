"use client";

import { useState } from "react";
import Link from "next/link";
import { useQuery } from "@tanstack/react-query";
import { toast } from "sonner";
import { FileDown, FileText } from "lucide-react";

import { Card, CardContent, CardHeader, CardTitle } from "@/components/ui/card";
import { Button } from "@/components/ui/button";
import { listGroups } from "@/lib/api/groups";
import { downloadSynthesisNotePdf, downloadSynthesisNoteWord } from "@/lib/api/exports";
import { extractErrorMessage } from "@/lib/api-client";

/** Les parties de la note, dans l'ordre du document (cf. PsdBriefBuilder côté serveur). */
const PARTS: { title: string; description: string }[] = [
  { title: "Sigles, mot du Directeur Général, l'essentiel du plan", description: "chiffres clés, vision et axes en une page." },
  { title: "I. Contexte et justification", description: "introduction, objet et périmètre, lecture des couleurs." },
  { title: "II. Approche méthodologique", description: "les phases d'élaboration du plan." },
  { title: "III. Présentation de SENICO", description: "missions, organisation, ressources." },
  { title: "IV. Analyse des parties prenantes", description: "matrice intérêt / pouvoir d'influence." },
  {
    title: "V. Diagnostic stratégique",
    description: "performances 2026, PESTEL, SWOT, orientations croisées et risques de criticité élevée.",
  },
  { title: "VI. Bilan du plan précédent", description: "résultats et enseignements." },
  { title: "VII. Enjeux et défis — VIII. Facteurs clés de réussite et d'échec", description: "" },
  {
    title: "IX. Cadre stratégique",
    description: "vision, mission, valeurs et axes arrêtés par la Direction Générale, avec les objectifs des directions.",
  },
  { title: "X. Cadre de mise en œuvre", description: "budget par axe et par exercice, plan de financement, effectifs." },
  { title: "XI. Cadre de pilotage et de suivi-évaluation", description: "dispositif de pilotage et indicateurs." },
  { title: "XII. Synthèse du cadre stratégique", description: "objectifs, actions, coûts et responsables par axe." },
  {
    title: "XIII. Conclusion et annexes",
    description: "contraintes par domaine, parties prenantes, matrice des risques, fiche des indicateurs.",
  },
];

/**
 * Note de synthese : le Plan Stratégique de Développement présenté sur le plan d'un PSD publié
 * (page de garde, sommaire paginé, parties numérotées, graphiques), à partir des contributions des
 * directions et des textes arrêtés par la Direction Générale. Plus courte que le Plan Stratégique
 * de SENICO, qui reprend en détail les tableaux de chaque direction.
 *
 * Même périmètre que ce dernier depuis la validation à deux niveaux : seules les sections
 * approuvées par le DG y figurent.
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
          Le Plan Stratégique de Développement présenté comme un plan stratégique publié — page de garde, sommaire
          paginé, parties numérotées, graphiques — sans le détail des tableaux de chaque direction. Pour le Conseil
          d&apos;Administration et le comité de pilotage.
        </p>
      </div>

      <Card>
        <CardHeader>
          <CardTitle>Télécharger la note de synthèse</CardTitle>
        </CardHeader>
        <CardContent className="space-y-4">
          <p className="text-[13px] text-muted-foreground">
            Établie à partir des seules sections <strong>approuvées par la Direction Générale</strong> des{" "}
            {groups?.length ?? "…"} directions. Une section encore en cours, soumise, ou validée mais pas encore
            approuvée par le DG n&apos;est pas reprise dans la note.
          </p>
          <p className="text-[13px] text-muted-foreground">
            Le mot du DG, la vision, la mission, les valeurs, les axes stratégiques et le dispositif de pilotage se
            rédigent sur la page{" "}
            <Link href="/admin/psd-final" className="font-medium text-primary-600 underline-offset-2 hover:underline">
              Plan Stratégique de SENICO
            </Link>
            . Tant qu&apos;un de ces textes manque, la note le signale à l&apos;endroit concerné.
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
            {PARTS.map((part) => (
              <li key={part.title}>
                <strong>{part.title}</strong>
                {part.description && <> — {part.description}</>}
              </li>
            ))}
          </ol>
          <p className="text-[13px] text-muted-foreground mt-4">
            Chaque tableau chiffré est suivi de la lecture qu&apos;on en attend (« Analyse : … »). Les constats des
            directions portent leur couleur ; les textes de la Direction Générale engagent l&apos;entreprise entière.
          </p>
        </CardContent>
      </Card>
    </div>
  );
}
