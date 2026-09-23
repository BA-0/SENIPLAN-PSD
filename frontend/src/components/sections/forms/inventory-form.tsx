"use client";

import { Table, TableBody, TableCell, TableEmptyRow, TableHead, TableHeader, TableRow } from "@/components/ui/table";
import { NoteTable } from "@/components/data-table/note-table";
import { TagListEditor } from "@/components/data-table/tag-list-editor";
import { CAUSAL_LABELS, PESTEL_LABELS, STAKEHOLDER_CATEGORY_LABELS, STAKEHOLDER_SCOPE_LABELS } from "@/types/sections";
import type { InventoryContent, Level, StakeholderCategory, StakeholderScope } from "@/types/sections";
import type { SectionFormProps } from "./types";
import { SwotTable } from "./swot-table";

const LEVEL_LABELS: Record<Level, string> = { FORT: "Fort", MOYEN: "Moyen", FAIBLE: "Faible" };

function text(value: string | null | undefined) {
  return value?.trim() || "—";
}

/**
 * S07 — inventaire du diagnostic : la note de synthese de la direction, puis le rappel en lecture
 * seule, tableau par tableau, de ce qu'elle a saisi dans les sections parties prenantes, PESTEL,
 * SWOT et analyse causale (agrege a la lecture par DerivedFieldsService).
 */
export function InventoryForm({ content, onChange, readOnly }: SectionFormProps<InventoryContent>) {
  const stakeholders = content.stakeholders ?? [];
  const pestel = content.pestel ?? [];
  const causalAnalysis = content.causalAnalysis ?? [];
  const readOnlyHint = <span className="ml-2 text-[12px] font-normal text-muted-foreground">lecture seule — repris de la section</span>;
  const cellClass = "whitespace-pre-wrap align-top text-[13px] leading-snug text-foreground/90";

  return (
    <div className="space-y-5">
      <NoteTable
        title="Note de synthèse"
        value={content.synthesisNote}
        onChange={(v) => onChange((prev) => ({ ...prev, synthesisNote: v }))}
        readOnly={readOnly}
        placeholder="Synthèse consolidée du diagnostic (parties prenantes, PESTEL, SWOT, analyse causale)…"
      />

      <section className="space-y-2">
        <h3>Analyse des parties prenantes{readOnlyHint}</h3>
        <Table>
          <TableHeader>
            <TableRow>
              <TableHead className="min-w-[150px]">Acteur (PP)</TableHead>
              <TableHead className="min-w-[110px]">Portée</TableHead>
              <TableHead className="min-w-[180px] whitespace-normal">Rôles / Responsabilités</TableHead>
              <TableHead className="min-w-[180px] whitespace-normal">Attentes / Intérêt / Priorités</TableHead>
              <TableHead className="min-w-[180px] whitespace-normal">Stratégie d&apos;adaptation</TableHead>
              <TableHead>Niveau importance</TableHead>
              <TableHead>Niveau influence</TableHead>
              <TableHead className="min-w-[160px]">Actions</TableHead>
            </TableRow>
          </TableHeader>
          <TableBody>
            {stakeholders.map((s, i) => (
              <TableRow key={i}>
                <TableCell label>
                  {s.category ? (STAKEHOLDER_CATEGORY_LABELS[s.category as StakeholderCategory] ?? s.category) : "—"}
                </TableCell>
                <TableCell className={cellClass}>
                  {s.scope ? (STAKEHOLDER_SCOPE_LABELS[s.scope as StakeholderScope] ?? s.scope) : "—"}
                </TableCell>
                <TableCell className={cellClass}>{text(s.roles)}</TableCell>
                <TableCell className={cellClass}>{text(s.expectations)}</TableCell>
                <TableCell className={cellClass}>{text(s.adaptationStrategy)}</TableCell>
                <TableCell className={cellClass}>{s.importance ? LEVEL_LABELS[s.importance as Level] : "—"}</TableCell>
                <TableCell className={cellClass}>{s.influence ? LEVEL_LABELS[s.influence as Level] : "—"}</TableCell>
                <TableCell className={cellClass}>{text(s.actions)}</TableCell>
              </TableRow>
            ))}
            {stakeholders.length === 0 && <TableEmptyRow colSpan={8}>Aucune partie prenante renseignée</TableEmptyRow>}
          </TableBody>
        </Table>
      </section>

      <section className="space-y-2">
        <h3>Analyse PESTEL{readOnlyHint}</h3>
        <Table>
          <TableHeader>
            <TableRow>
              <TableHead className="min-w-[160px]">Items</TableHead>
              <TableHead className="min-w-[220px]">Menaces</TableHead>
              <TableHead className="min-w-[220px]">Opportunités</TableHead>
              <TableHead className="min-w-[220px] whitespace-normal">Actions pour atténuer les menaces ou saisir les opportunités</TableHead>
            </TableRow>
          </TableHeader>
          <TableBody>
            {pestel.map((p, i) => (
              <TableRow key={i}>
                <TableCell label>{PESTEL_LABELS[p.axis] ?? p.axis}</TableCell>
                <TableCell className={cellClass}>{text(p.threats)}</TableCell>
                <TableCell className={cellClass}>{text(p.opportunities)}</TableCell>
                <TableCell className={cellClass}>{text(p.actions)}</TableCell>
              </TableRow>
            ))}
            {pestel.length === 0 && <TableEmptyRow colSpan={4}>Aucun item PESTEL renseigné</TableEmptyRow>}
          </TableBody>
        </Table>
      </section>

      <section className="space-y-2">
        <h3>Analyse SWOT{readOnlyHint}</h3>
        <SwotTable swot={content.swot} readOnly />
      </section>

      <section className="space-y-2">
        <h3>Analyse causale{readOnlyHint}</h3>
        <Table>
          <TableHeader>
            <TableRow>
              <TableHead className="min-w-[240px] whitespace-normal">Sources</TableHead>
              <TableHead className="min-w-[320px]">Analyse</TableHead>
            </TableRow>
          </TableHeader>
          <TableBody>
            {causalAnalysis.map((c, i) => (
              <TableRow key={i}>
                <TableCell label>{CAUSAL_LABELS[c.source] ?? c.source}</TableCell>
                <TableCell className="py-3 align-top">
                  <TagListEditor items={(c.items ?? []).filter(Boolean)} onChange={() => undefined} readOnly />
                </TableCell>
              </TableRow>
            ))}
            {causalAnalysis.length === 0 && <TableEmptyRow colSpan={2}>Aucune source d&apos;analyse renseignée</TableEmptyRow>}
          </TableBody>
        </Table>
      </section>
    </div>
  );
}
