"use client";

import { Table, TableBody, TableCell, TableHead, TableHeader, TableRow } from "@/components/ui/table";
import { EditableCell, EditableNumberCell } from "@/components/data-table/editable-cell";
import { KeyedRowAdder, RemoveRowButton, insertInModelOrder, remainingOptions } from "@/components/data-table/row-actions";
import { formatFcfa, formatNumber } from "@/lib/utils";
import { FINANCING_LABELS } from "@/types/sections";
import type { FinancingPlanContent, FinancingRow } from "@/types/sections";
import type { SectionFormProps } from "./types";

const FINANCING_SOURCES = Object.keys(FINANCING_LABELS);

export function FinancingPlanForm({ content, onChange, readOnly }: SectionFormProps<FinancingPlanContent>) {
  function updateRow(index: number, patch: Partial<FinancingRow>) {
    onChange((prev) => ({
      ...prev,
      rows: prev.rows.map((r, i) => (i === index ? { ...r, ...patch } : r)),
    }));
  }
  function addRow(source: string) {
    const row: FinancingRow = {
      source,
      amount: 0,
      modalities: "",
      period: "",
      responsible: "",
    };
    onChange((prev) => ({
      ...prev,
      rows: insertInModelOrder(prev.rows, row, (r) => r.source, FINANCING_SOURCES),
    }));
  }
  function removeRow(index: number) {
    onChange((prev) => ({
      ...prev,
      rows: prev.rows.filter((_, i) => i !== index),
    }));
  }

  return (
    <div className="space-y-3">
      <Table>
        <TableHeader>
          <TableRow>
            <TableHead className="min-w-[200px]">Source</TableHead>
            <TableHead className="text-right">Montant (FCFA)</TableHead>
            <TableHead className="text-right">%</TableHead>
            <TableHead className="min-w-[220px]">Modalités de mobilisation</TableHead>
            <TableHead className="min-w-[120px]">Période</TableHead>
            <TableHead className="min-w-[140px]">Responsables</TableHead>
            {!readOnly && <TableHead className="w-10" />}
          </TableRow>
        </TableHeader>
        <TableBody>
          {content.rows.map((row, index) => (
            <TableRow key={row.source}>
              <TableCell className="font-medium text-foreground/90 text-[13px]">{FINANCING_LABELS[row.source] ?? row.source}</TableCell>
              <TableCell>
                <EditableNumberCell value={row.amount} onChange={(v) => updateRow(index, { amount: v })} readOnly={readOnly} />
              </TableCell>
              <TableCell className="text-right tabular-nums text-muted-foreground">{formatNumber(row.percent)}%</TableCell>
              <TableCell>
                <EditableCell value={row.modalities} onChange={(v) => updateRow(index, { modalities: v })} readOnly={readOnly} multiline />
              </TableCell>
              <TableCell>
                <EditableCell value={row.period} onChange={(v) => updateRow(index, { period: v })} readOnly={readOnly} />
              </TableCell>
              <TableCell>
                <EditableCell value={row.responsible} onChange={(v) => updateRow(index, { responsible: v })} readOnly={readOnly} />
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
              <TableCell colSpan={readOnly ? 6 : 7} className="py-8 text-center text-muted-foreground">
                Aucune source de financement renseignée
              </TableCell>
            </TableRow>
          )}
          <TableRow total>
            <TableCell>TOTAL</TableCell>
            <TableCell className="text-right tabular-nums">{formatFcfa(content.total)}</TableCell>
            <TableCell className="text-right tabular-nums">{content.total ? "100%" : "—"}</TableCell>
            <TableCell colSpan={readOnly ? 3 : 4} />
          </TableRow>
        </TableBody>
      </Table>
      {!readOnly && (
        <KeyedRowAdder
          options={remainingOptions(
            FINANCING_SOURCES,
            FINANCING_LABELS,
            content.rows.map((r) => r.source)
          )}
          onAdd={addRow}
          label="Ajouter la source"
          placeholder="Choisir une source de financement…"
        />
      )}
    </div>
  );
}
