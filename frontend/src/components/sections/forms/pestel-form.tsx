"use client";

import { Table, TableBody, TableCell, TableHead, TableHeader, TableRow } from "@/components/ui/table";
import { EditableCell } from "@/components/data-table/editable-cell";
import { KeyedRowAdder, RemoveRowButton, insertInModelOrder, remainingOptions } from "@/components/data-table/row-actions";
import { PESTEL_LABELS } from "@/types/sections";
import type { PestelRow, PestelContent } from "@/types/sections";
import type { SectionFormProps } from "./types";

const PESTEL_AXES = Object.keys(PESTEL_LABELS);

export function PestelForm({ content, onChange, readOnly }: SectionFormProps<PestelContent>) {
  function updateRow(index: number, patch: Partial<PestelRow>) {
    onChange((prev) => ({
      rows: prev.rows.map((r, i) => (i === index ? { ...r, ...patch } : r)),
    }));
  }
  function addRow(axis: string) {
    onChange((prev) => ({
      rows: insertInModelOrder(prev.rows, { axis, analysis: "", threats: "", opportunities: "", actions: "" }, (r) => r.axis, PESTEL_AXES),
    }));
  }
  function removeRow(index: number) {
    onChange((prev) => ({ rows: prev.rows.filter((_, i) => i !== index) }));
  }

  return (
    <div className="space-y-3">
      <Table>
        <TableHeader>
          <TableRow>
            <TableHead className="min-w-[160px]">Items</TableHead>
            <TableHead className="min-w-[240px]">Analyses</TableHead>
            <TableHead className="min-w-[240px]">Menaces</TableHead>
            <TableHead className="min-w-[240px]">Opportunités</TableHead>
            <TableHead className="min-w-[240px]">Actions pour atténuer les menaces ou saisir les opportunités</TableHead>
            {!readOnly && <TableHead className="w-10" />}
          </TableRow>
        </TableHeader>
        <TableBody>
          {content.rows.map((row, index) => (
            <TableRow key={row.axis}>
              <TableCell className="font-medium text-foreground/90 text-[13px]">{PESTEL_LABELS[row.axis] ?? row.axis}</TableCell>
              <TableCell>
                <EditableCell
                  value={row.analysis ?? ""}
                  onChange={(v) => updateRow(index, { analysis: v })}
                  readOnly={readOnly}
                  multiline
                />
              </TableCell>
              <TableCell>
                <EditableCell value={row.threats} onChange={(v) => updateRow(index, { threats: v })} readOnly={readOnly} multiline />
              </TableCell>
              <TableCell>
                <EditableCell
                  value={row.opportunities}
                  onChange={(v) => updateRow(index, { opportunities: v })}
                  readOnly={readOnly}
                  multiline
                />
              </TableCell>
              <TableCell>
                <EditableCell value={row.actions} onChange={(v) => updateRow(index, { actions: v })} readOnly={readOnly} multiline />
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
              <TableCell colSpan={readOnly ? 5 : 6} className="py-8 text-center text-muted-foreground">
                Aucun item renseigné
              </TableCell>
            </TableRow>
          )}
        </TableBody>
      </Table>
      {!readOnly && (
        <KeyedRowAdder
          options={remainingOptions(
            PESTEL_AXES,
            PESTEL_LABELS,
            content.rows.map((r) => r.axis)
          )}
          onAdd={addRow}
          label="Ajouter l'item"
          placeholder="Choisir un item PESTEL…"
        />
      )}
    </div>
  );
}
