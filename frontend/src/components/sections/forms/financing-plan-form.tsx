"use client";

import { Table, TableBody, TableCell, TableHead, TableHeader, TableRow } from "@/components/ui/table";
import { EditableCell, EditableNumberCell } from "@/components/data-table/editable-cell";
import { formatFcfa, formatNumber } from "@/lib/utils";
import { FINANCING_LABELS } from "@/types/sections";
import type { FinancingPlanContent, FinancingRow } from "@/types/sections";
import type { SectionFormProps } from "./types";

export function FinancingPlanForm({ content, onChange, readOnly }: SectionFormProps<FinancingPlanContent>) {
  function updateRow(index: number, patch: Partial<FinancingRow>) {
    onChange((prev) => ({ ...prev, rows: prev.rows.map((r, i) => (i === index ? { ...r, ...patch } : r)) }));
  }

  return (
    <Table>
      <TableHeader>
        <TableRow>
          <TableHead className="min-w-[200px]">Source</TableHead>
          <TableHead className="text-right">Montant (FCFA)</TableHead>
          <TableHead className="text-right">%</TableHead>
          <TableHead className="min-w-[160px]">Modalités de mobilisation</TableHead>
          <TableHead className="min-w-[120px]">Période</TableHead>
          <TableHead className="min-w-[140px]">Responsables</TableHead>
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
              <EditableCell value={row.modalities} onChange={(v) => updateRow(index, { modalities: v })} readOnly={readOnly} />
            </TableCell>
            <TableCell>
              <EditableCell value={row.period} onChange={(v) => updateRow(index, { period: v })} readOnly={readOnly} />
            </TableCell>
            <TableCell>
              <EditableCell value={row.responsible} onChange={(v) => updateRow(index, { responsible: v })} readOnly={readOnly} />
            </TableCell>
          </TableRow>
        ))}
        <TableRow total>
          <TableCell>TOTAL</TableCell>
          <TableCell className="text-right tabular-nums">{formatFcfa(content.total)}</TableCell>
          <TableCell className="text-right tabular-nums">{content.total ? "100%" : "—"}</TableCell>
          <TableCell colSpan={3} />
        </TableRow>
      </TableBody>
    </Table>
  );
}
