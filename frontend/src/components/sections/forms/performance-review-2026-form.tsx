"use client";

import { Table, TableBody, TableCell, TableHead, TableHeader, TableRow } from "@/components/ui/table";
import { EditableCell, EditableNumberCell } from "@/components/data-table/editable-cell";
import { AddRowButton, RemoveRowButton } from "@/components/data-table/row-actions";
import type { PerformanceReview2026Content, PerformanceReview2026Row } from "@/types/sections";
import type { SectionFormProps } from "./types";

const EMPTY_ROW: PerformanceReview2026Row = {
  domain: "",
  indicator: "",
  target2026: 0,
  achieved2026: 0,
  comment: "",
};

/**
 * S01B — bilan de l'annee 2026, point de depart du diagnostic : ce qui etait vise
 * face a ce qui a ete realise. Le taux est calcule cote serveur (DerivedFieldsService)
 * et reste vide quand la cible vaut 0, ou un pourcentage n'aurait pas de sens.
 */
export function PerformanceReview2026Form({ content, onChange, readOnly }: SectionFormProps<PerformanceReview2026Content>) {
  function updateRow(index: number, patch: Partial<PerformanceReview2026Row>) {
    onChange((prev) => ({ rows: prev.rows.map((r, i) => (i === index ? { ...r, ...patch } : r)) }));
  }
  function addRow() {
    onChange((prev) => ({ rows: [...prev.rows, { ...EMPTY_ROW }] }));
  }
  function removeRow(index: number) {
    onChange((prev) => ({ rows: prev.rows.filter((_, i) => i !== index) }));
  }

  return (
    <div className="space-y-3">
      <Table>
        <TableHeader>
          <TableRow>
            <TableHead className="min-w-[180px]">Domaine / Activité</TableHead>
            <TableHead className="min-w-[200px]">Indicateur</TableHead>
            <TableHead className="text-right">Cible 2026</TableHead>
            <TableHead className="text-right">Réalisé 2026</TableHead>
            <TableHead className="text-right">Taux</TableHead>
            <TableHead className="min-w-[220px]">Écart / Commentaire</TableHead>
            {!readOnly && <TableHead className="w-10" />}
          </TableRow>
        </TableHeader>
        <TableBody>
          {content.rows.map((row, index) => (
            <TableRow key={index}>
              <TableCell>
                <EditableCell value={row.domain} onChange={(v) => updateRow(index, { domain: v })} readOnly={readOnly} multiline />
              </TableCell>
              <TableCell>
                <EditableCell value={row.indicator} onChange={(v) => updateRow(index, { indicator: v })} readOnly={readOnly} multiline />
              </TableCell>
              <TableCell>
                <EditableNumberCell value={row.target2026} onChange={(v) => updateRow(index, { target2026: v })} readOnly={readOnly} />
              </TableCell>
              <TableCell>
                <EditableNumberCell value={row.achieved2026} onChange={(v) => updateRow(index, { achieved2026: v })} readOnly={readOnly} />
              </TableCell>
              <TableCell className="text-right tabular-nums text-muted-foreground">
                {row.rate == null ? "—" : `${new Intl.NumberFormat("fr-FR", { maximumFractionDigits: 1 }).format(row.rate)} %`}
              </TableCell>
              <TableCell>
                <EditableCell value={row.comment} onChange={(v) => updateRow(index, { comment: v })} readOnly={readOnly} multiline />
              </TableCell>
              {!readOnly && (
                <TableCell>
                  <RemoveRowButton onConfirm={() => removeRow(index)} />
                </TableCell>
              )}
            </TableRow>
          ))}
          {content.rows.length === 0 && (
            <TableRow>
              <TableCell colSpan={7} className="text-center text-muted-foreground py-8">
                Aucune performance 2026 renseignée
              </TableCell>
            </TableRow>
          )}
        </TableBody>
      </Table>
      {!readOnly && <AddRowButton onAdd={addRow} label="Ajouter une ligne" />}
    </div>
  );
}
