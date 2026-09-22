"use client";

import { Table, TableBody, TableCell, TableEmptyRow, TableHead, TableHeader, TableRow } from "@/components/ui/table";
import { TagListEditor } from "@/components/data-table/tag-list-editor";
import { KeyedRowAdder, RemoveRowButton, insertInModelOrder, remainingOptions } from "@/components/data-table/row-actions";
import { CAUSAL_LABELS, TOWS_ACTION_LABELS } from "@/types/sections";
import type { CausalAnalysisContent, TowsActions } from "@/types/sections";
import type { SectionFormProps } from "./types";

const CAUSAL_SOURCES = Object.keys(CAUSAL_LABELS);

/**
 * S06 — analyse causale, sur le modele du canevas : un tableau « Sources | Analyse », une ligne par
 * source (manifestations, causes immediates, sous-jacentes, profondes, solutions), chaque cellule
 * pouvant contenir plusieurs elements. Les actions de la matrice de confrontation sont rappelees
 * au-dessus, en lecture seule.
 */
export function CausalAnalysisForm({ content, onChange, readOnly }: SectionFormProps<CausalAnalysisContent>) {
  function updateItems(index: number, items: string[]) {
    onChange((prev) => ({ ...prev, rows: prev.rows.map((r, i) => (i === index ? { ...r, items } : r)) }));
  }
  function addRow(source: string) {
    onChange((prev) => ({ ...prev, rows: insertInModelOrder(prev.rows, { source, items: [] }, (r) => r.source, CAUSAL_SOURCES) }));
  }
  function removeRow(index: number) {
    onChange((prev) => ({ ...prev, rows: prev.rows.filter((_, i) => i !== index) }));
  }

  const towsActions = content.syncedTowsActions;
  const towsEntries = towsActions
    ? (Object.keys(TOWS_ACTION_LABELS) as (keyof TowsActions)[])
        .map((key) => ({ label: TOWS_ACTION_LABELS[key], value: towsActions[key] }))
        .filter((e) => e.value)
    : [];
  const colCount = readOnly ? 2 : 3;

  return (
    <div className="space-y-5">
      {towsEntries.length > 0 && (
        <section className="space-y-2">
          <h3>
            Actions issues de la matrice de confrontation
            <span className="ml-2 text-[12px] font-normal text-muted-foreground">lecture seule</span>
          </h3>
          <Table>
            <TableHeader>
              <TableRow>
                <TableHead className="w-2/5 whitespace-normal">Question</TableHead>
                <TableHead className="whitespace-normal">Réponse</TableHead>
              </TableRow>
            </TableHeader>
            <TableBody>
              {towsEntries.map((e) => (
                <TableRow key={e.label}>
                  <TableCell label>{e.label}</TableCell>
                  <TableCell className="whitespace-pre-wrap align-top text-[13px] leading-snug text-foreground/90">{e.value}</TableCell>
                </TableRow>
              ))}
            </TableBody>
          </Table>
        </section>
      )}

      <section className="space-y-3">
        <Table>
          <TableHeader>
            <TableRow>
              <TableHead className="min-w-[240px] whitespace-normal">Sources</TableHead>
              <TableHead className="min-w-[320px]">Analyse</TableHead>
              {!readOnly && <TableHead className="w-10" />}
            </TableRow>
          </TableHeader>
          <TableBody>
            {content.rows.map((row, index) => (
              <TableRow key={row.source} className="hover:bg-transparent">
                <TableCell label>{CAUSAL_LABELS[row.source] ?? row.source}</TableCell>
                <TableCell className="py-3 align-top">
                  <TagListEditor
                    items={row.items}
                    onChange={(items) => updateItems(index, items)}
                    readOnly={readOnly}
                    placeholder="Ajouter un élément d'analyse…"
                  />
                </TableCell>
                {!readOnly && (
                  <TableCell className="align-top">
                    <RemoveRowButton onConfirm={() => removeRow(index)} />
                  </TableCell>
                )}
              </TableRow>
            ))}
            {content.rows.length === 0 && <TableEmptyRow colSpan={colCount}>Aucune source d&apos;analyse renseignée</TableEmptyRow>}
          </TableBody>
        </Table>
        {!readOnly && (
          <KeyedRowAdder
            options={remainingOptions(
              CAUSAL_SOURCES,
              CAUSAL_LABELS,
              content.rows.map((r) => r.source)
            )}
            onAdd={addRow}
            label="Ajouter la source"
            placeholder="Choisir une source (manifestation, causes, solutions)…"
          />
        )}
      </section>
    </div>
  );
}
