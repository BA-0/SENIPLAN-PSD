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
  { title: "Sigles, mot du Directeur Général, l'essentiel du plan", description: "chiffres clés et budget par axe en une page." },
  { title: "I. Contexte et justification", description: "introduction, objet et périmètre, code couleur des directions." },
  { title: "II. Approche méthodologique", description: "les phases d'élaboration du plan." },
  { title: "III. Présentation de SENICO", description: "historique, missions, gouvernance, organisation, ressources." },
  { title: "IV. Analyse des parties prenantes", description: "le tableau du canevas (rôles, attentes, importance, influence, actions)." },
  {
    title: "V. Bilan des performances des années précédentes",
    description:
      "enseignements, puis les performances des années passées (les cinq exercices écoulés dans un seul tableau) avec leur résultat obtenu, et celles de 2026 avec leurs tendances, dans les sept colonnes du modèle client.",
  },
  {
    title: "VI. Diagnostic stratégique",
    description:
      "ressources et compétences et leur synthèse, PESTEL, SWOT, mise en relation du diagnostic, analyse causale, risques de criticité élevée.",
  },
  {
    title: "VII. Principaux enjeux et défis — VIII. Facteurs clés de réussite et d'échec",
    description: "synthèse des contraintes, enjeux et défis par domaine d'activités, puis les enjeux et défis arrêtés par la Direction Générale.",
  },
  {
    title: "IX. Cadre stratégique",
    description: "vision, mission, valeurs et le tableau des axes : objectif, objectifs spécifiques et axes des directions regroupés.",
  },
  {
    title: "X. Cadre de mise en œuvre",
    description: "synthèse du cadre logique, budget par axe et par exercice, plan de financement, effectifs par hiérarchie, statut et genre.",
  },
  { title: "XI. Cadre de pilotage et de suivi-évaluation", description: "dispositif de pilotage et renvoi au cadre de mesure de rendement (annexe 5)." },
  {
    title: "XII. Synthèse du cadre stratégique",
    description: "le tableau du modèle client : OS, actions, budget, objectif, contraintes ou opportunités, par axe.",
  },
  {
    title: "XIII. Conclusion, puis cinq annexes par axe",
    description:
      "1. matrice des risques, 2. cadre logique, 3. planification, 4. budget détaillé, 5. fiche des indicateurs puis cadre de mesure de rendement.",
  },
];

/**
 * Note de synthese : le Plan Stratégique 2027-2031 présenté sur le plan d'un plan stratégique publié
 * (page de garde, parties numérotées, tableaux du canevas, graphiques), à partir des contributions des
 * directions et des textes arrêtés par la Direction Générale. Plus courte que le Plan Stratégique
 * de SENICO, qui reprend en détail les tableaux de chaque direction. Sans page de sommaire et avec
 * chaque tableau présent même vide, depuis la revue de l'auditeur du 22/09/2026.
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
          Le Plan Stratégique 2027-2031 présenté comme un plan stratégique publié — page de garde, parties
          numérotées, graphiques — avec tous les tableaux du canevas consolidés axe par axe (cadre logique, plan
          d&apos;actions, budget, cadre de mesure de rendement…), présents même vides, chaque ligne à la couleur
          de sa direction. Pour le Conseil d&apos;Administration et le comité de pilotage.
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
            Un tableau que rien n&apos;alimente encore garde ses colonnes et les lignes du modèle, avec un tiret par
            case. Les constats des directions portent leur couleur ; les textes de la Direction Générale engagent
            l&apos;entreprise entière.
          </p>
        </CardContent>
      </Card>
    </div>
  );
}
