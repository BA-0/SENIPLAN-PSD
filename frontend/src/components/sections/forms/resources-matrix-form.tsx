"use client";

import { Table, TableBody, TableCell, TableHead, TableHeader, TableRow } from "@/components/ui/table";
import { EditableCell } from "@/components/data-table/editable-cell";
import { RESOURCE_LABELS } from "@/types/sections";
import type { ResourceRow, ResourcesMatrixContent } from "@/types/sections";
import type { SectionFormProps } from "./types";

export function ResourcesMatrixForm({ content, onChange, readOnly }: SectionFormProps<ResourcesMatrixContent>) {
  function updateRow(index: number, patch: Partial<ResourceRow>) {
    onChange((prev) => ({
      rows: prev.rows.map((r, i) => (i === index ? { ...r, ...patch } : r)),
    }));
  }

  return (
    <Table>
      <TableHeader>
        <TableRow>
          <TableHead className="min-w-[280px]">Ressource</TableHead>
          <TableHead className="min-w-[220px]">Forces / Acquis</TableHead>
          <TableHead className="min-w-[220px]">Faiblesses</TableHead>
          <TableHead className="min-w-[220px]">Défis à relever</TableHead>
        </TableRow>
      </TableHeader>
      <TableBody>
        {content.rows.map((row, index) => (
          <TableRow key={row.resourceKey}>
            <TableCell className="font-medium text-slate-700 text-[13px]">
              {RESOURCE_LABELS[row.resourceKey] ?? row.resourceKey}
            </TableCell>
            <TableCell>
              <EditableCell value={row.strengths} onChange={(v) => updateRow(index, { strengths: v })} readOnly={readOnly} multiline />
            </TableCell>
            <TableCell>
              <EditableCell value={row.weaknesses} onChange={(v) => updateRow(index, { weaknesses: v })} readOnly={readOnly} multiline />
            </TableCell>
            <TableCell>
              <EditableCell value={row.challenges} onChange={(v) => updateRow(index, { challenges: v })} readOnly={readOnly} multiline />
            </TableCell>
          </TableRow>
        ))}
      </TableBody>
    </Table>
  );
}
