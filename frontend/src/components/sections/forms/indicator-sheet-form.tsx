"use client";

import { Table, TableBody, TableCell, TableHead, TableHeader, TableRow } from "@/components/ui/table";
import { EditableCell } from "@/components/data-table/editable-cell";
import { AddRowButton, RemoveRowButton } from "@/components/data-table/row-actions";
import type { IndicatorRow, IndicatorSheetContent } from "@/types/sections";
import type { SectionFormProps } from "./types";

const EMPTY_ROW: IndicatorRow = {
  indicatorTitle: "",
  calculationMethod: "",
  periodicity: "",
  collectionSource: "",
  verificationSource: "",
  responsibleStructure: "",
};

export function IndicatorSheetForm({ content, onChange, readOnly }: SectionFormProps<IndicatorSheetContent>) {
  function updateRow(index: number, patch: Partial<IndicatorRow>) {
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
            <TableHead className="min-w-[180px]">Intitulé indicateur</TableHead>
            <TableHead className="min-w-[180px]">Mode de calcul</TableHead>
            <TableHead className="min-w-[120px]">Périodicité</TableHead>
            <TableHead className="min-w-[180px]">Sources et moyens de collecte</TableHead>
            <TableHead className="min-w-[180px]">Sources de vérification</TableHead>
            <TableHead className="min-w-[160px]">Structures responsables</TableHead>
            {!readOnly && <TableHead className="w-10" />}
          </TableRow>
        </TableHeader>
        <TableBody>
          {content.rows.map((row, index) => (
            <TableRow key={index}>
              <TableCell><EditableCell value={row.indicatorTitle} onChange={(v) => updateRow(index, { indicatorTitle: v })} readOnly={readOnly} multiline /></TableCell>
              <TableCell><EditableCell value={row.calculationMethod} onChange={(v) => updateRow(index, { calculationMethod: v })} readOnly={readOnly} multiline /></TableCell>
              <TableCell><EditableCell value={row.periodicity} onChange={(v) => updateRow(index, { periodicity: v })} readOnly={readOnly} /></TableCell>
              <TableCell><EditableCell value={row.collectionSource} onChange={(v) => updateRow(index, { collectionSource: v })} readOnly={readOnly} multiline /></TableCell>
              <TableCell><EditableCell value={row.verificationSource} onChange={(v) => updateRow(index, { verificationSource: v })} readOnly={readOnly} multiline /></TableCell>
              <TableCell><EditableCell value={row.responsibleStructure} onChange={(v) => updateRow(index, { responsibleStructure: v })} readOnly={readOnly} /></TableCell>
              {!readOnly && (
                <TableCell>
                  <RemoveRowButton onConfirm={() => removeRow(index)} />
                </TableCell>
              )}
            </TableRow>
          ))}
          {content.rows.length === 0 && (
            <TableRow>
              <TableCell colSpan={7} className="text-center text-slate-400 py-8">Aucun indicateur renseigné</TableCell>
            </TableRow>
          )}
        </TableBody>
      </Table>
      {!readOnly && <AddRowButton onAdd={addRow} label="Ajouter un indicateur" />}
    </div>
  );
}
