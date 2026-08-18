"use client";

import { Table, TableBody, TableCell, TableHead, TableHeader, TableRow } from "@/components/ui/table";
import { EditableCell } from "@/components/data-table/editable-cell";
import { PESTEL_LABELS } from "@/types/sections";
import type { PestelRow, PestelContent } from "@/types/sections";
import type { SectionFormProps } from "./types";

export function PestelForm({ content, onChange, readOnly }: SectionFormProps<PestelContent>) {
  function updateRow(index: number, patch: Partial<PestelRow>) {
    onChange((prev) => ({
      rows: prev.rows.map((r, i) => (i === index ? { ...r, ...patch } : r)),
    }));
  }

  return (
    <Table>
      <TableHeader>
        <TableRow>
          <TableHead className="min-w-[160px]">Axe</TableHead>
          <TableHead className="min-w-[240px]">Menaces</TableHead>
          <TableHead className="min-w-[240px]">Opportunités</TableHead>
          <TableHead className="min-w-[240px]">Actions pour atténuer les menaces / saisir les opportunités</TableHead>
        </TableRow>
      </TableHeader>
      <TableBody>
        {content.rows.map((row, index) => (
          <TableRow key={row.axis}>
            <TableCell className="font-medium text-foreground/90 text-[13px]">{PESTEL_LABELS[row.axis] ?? row.axis}</TableCell>
            <TableCell>
              <EditableCell value={row.threats} onChange={(v) => updateRow(index, { threats: v })} readOnly={readOnly} multiline />
            </TableCell>
            <TableCell>
              <EditableCell value={row.opportunities} onChange={(v) => updateRow(index, { opportunities: v })} readOnly={readOnly} multiline />
            </TableCell>
            <TableCell>
              <EditableCell value={row.actions} onChange={(v) => updateRow(index, { actions: v })} readOnly={readOnly} multiline />
            </TableCell>
          </TableRow>
        ))}
      </TableBody>
    </Table>
  );
}
