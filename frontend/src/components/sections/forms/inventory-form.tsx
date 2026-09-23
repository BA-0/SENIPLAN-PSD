"use client";

import { Table, TableBody, TableCell, TableHead, TableHeader, TableRow } from "@/components/ui/table";
import { NoteTable } from "@/components/data-table/note-table";
import { CAUSAL_LABELS, PESTEL_LABELS, STAKEHOLDER_CATEGORY_LABELS } from "@/types/sections";
import type { InventoryContent, Level, StakeholderCategory, SwotContent } from "@/types/sections";
import type { SectionFormProps } from "./types";

const LEVEL_LABELS: Record<Level, string> = { FORT: "fort", MOYEN: "moyen", FAIBLE: "faible" };

function clean(value: string | null | undefined): string {
  return (value ?? "").replace(/^•\s*/gm, "").replace(/\s*\n\s*/g, " ; ").trim();
}

/** Les quatre colonnes du canevas, chacune la liste de ce que la direction a retenu dans l'analyse. */
function inventoryColumns(content: InventoryContent): string[][] {
  const swot: Partial<SwotContent> = content.swot ?? {};
  const swotItems = [
    ...[...(swot.strengths ?? []), ...(swot.strengthsExternal ?? [])].map((s) => `Force : ${s}`),
    ...[...(swot.weaknesses ?? []), ...(swot.weaknessesExternal ?? [])].map((s) => `Faiblesse : ${s}`),
    ...[...(swot.opportunitiesInternal ?? []), ...(swot.opportunities ?? [])].map((s) => `Opportunité : ${s}`),
    ...[...(swot.threatsInternal ?? []), ...(swot.threats ?? [])].map((s) => `Menace : ${s}`),
  ];
  const pestelItems = (content.pestel ?? []).flatMap((p) => {
    const item = PESTEL_LABELS[p.axis] ?? p.axis;
    return [
      clean(p.analysis) && `${item} — analyses : ${clean(p.analysis)}`,
      clean(p.threats) && `${item} — menaces : ${clean(p.threats)}`,
      clean(p.opportunities) && `${item} — opportunités : ${clean(p.opportunities)}`,
    ].filter(Boolean) as string[];
  });
  const stakeholderItems = (content.stakeholders ?? []).map((s) => {
    const actor = STAKEHOLDER_CATEGORY_LABELS[s.category as StakeholderCategory] ?? s.category ?? "—";
    const levels = [
      s.importance && `importance ${LEVEL_LABELS[s.importance as Level]}`,
      s.influence && `influence ${LEVEL_LABELS[s.influence as Level]}`,
    ].filter(Boolean);
    return levels.length ? `${actor} (${levels.join(", ")})` : actor;
  });
  const causalItems = (content.causalAnalysis ?? []).flatMap((c) =>
    (c.items ?? []).filter(Boolean).map((item) => `${CAUSAL_LABELS[c.source] ?? c.source} : ${item}`)
  );
  return [swotItems, pestelItems, stakeholderItems, causalItems];
}

const HEADERS = ["SWOT", "PESTEL", "Analyse des parties prenantes", "Analyse causale et autres"];

/**
 * S07 — inventaire, sur l'agencement du canevas : un seul tableau à quatre colonnes (SWOT, PESTEL, analyse
 * des parties prenantes, analyse causale), repris en lecture seule des sections correspondantes (agrégé à la
 * lecture par DerivedFieldsService), précédé de la note de synthèse de la direction.
 */
export function InventoryForm({ content, onChange, readOnly }: SectionFormProps<InventoryContent>) {
  const columns = inventoryColumns(content);
  const rowCount = Math.max(...columns.map((c) => c.length), 0);

  return (
    <div className="space-y-5">
      <NoteTable
        title="Note de synthèse"
        value={content.synthesisNote}
        onChange={(v) => onChange((prev) => ({ ...prev, synthesisNote: v }))}
        readOnly={readOnly}
        placeholder="Synthèse consolidée du diagnostic (SWOT, PESTEL, parties prenantes, analyse causale)…"
      />

      <section className="space-y-2">
        <h3>
          Synthèse des recommandations stratégiques
          <span className="ml-2 text-[12px] font-normal text-muted-foreground">lecture seule — repris des sections</span>
        </h3>
        <Table>
          <TableHeader>
            <TableRow>
              {HEADERS.map((h) => (
                <TableHead key={h} className="w-1/4 whitespace-normal text-center">
                  {h}
                </TableHead>
              ))}
            </TableRow>
          </TableHeader>
          <TableBody>
            {Array.from({ length: rowCount }, (_, row) => (
              <TableRow key={row} className="hover:bg-transparent">
                {columns.map((items, col) => (
                  <TableCell key={col} className="align-top whitespace-pre-wrap text-[13px] leading-snug text-foreground/90">
                    {items[row] ?? ""}
                  </TableCell>
                ))}
              </TableRow>
            ))}
            {rowCount === 0 && (
              <TableRow className="hover:bg-transparent">
                <TableCell colSpan={4} className="py-8 text-center text-[13px] text-muted-foreground">
                  Rien à inventorier : les sections SWOT, PESTEL, parties prenantes et analyse causale ne sont pas encore renseignées.
                </TableCell>
              </TableRow>
            )}
          </TableBody>
        </Table>
      </section>
    </div>
  );
}
