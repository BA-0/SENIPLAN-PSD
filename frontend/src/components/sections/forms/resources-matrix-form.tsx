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
            <TableCell className="border-l-2 border-l-primary-300 py-2.5 align-top font-medium text-slate-700 text-[13px] leading-snug">
              {RESOURCE_LABELS[row.resourceKey] ?? row.resourceKey}
            </TableCell>
            <TableCell className="py-2.5 align-top">
              <EditableCell
                value={row.strengths}
                onChange={(v) => updateRow(index, { strengths: v })}
                readOnly={readOnly}
                placeholder="Ajouter une force / un acquis…"
                multiline
              />
            </TableCell>
            <TableCell className="py-2.5 align-top">
              <EditableCell
                value={row.weaknesses}
                onChange={(v) => updateRow(index, { weaknesses: v })}
                readOnly={readOnly}
                placeholder="Ajouter une faiblesse…"
                multiline
              />
            </TableCell>
            <TableCell className="py-2.5 align-top">
              <EditableCell
                value={row.challenges}
                onChange={(v) => updateRow(index, { challenges: v })}
                readOnly={readOnly}
                placeholder="Ajouter un défi à relever…"
                multiline
              />
            </TableCell>
          </TableRow>
        ))}
      </TableBody>
    </Table>
  );
}
