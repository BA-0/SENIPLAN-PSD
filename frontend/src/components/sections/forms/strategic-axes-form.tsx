"use client";

import { Table, TableBody, TableCell, TableHead, TableHeader, TableRow } from "@/components/ui/table";
import { EditableCell } from "@/components/data-table/editable-cell";
import { TagListEditor } from "@/components/data-table/tag-list-editor";
import { directionAxisLabel } from "@/lib/utils";
import type { StrategicAxesContent, StrategicAxis } from "@/types/sections";
import type { SectionFormProps } from "./types";

/**
 * S08 — axes stratégiques / orientations, sur l'agencement du canevas : une colonne par axe (Axe 1 à Axe 4).
 * Sous chaque axe, en bandeaux : l'orientation stratégique (l'intitulé repris par toutes les sections
 * suivantes), l'objectif de l'axe, puis ses objectifs spécifiques.
 */
export function StrategicAxesForm({ content, onChange, readOnly }: SectionFormProps<StrategicAxesContent>) {
  const axes = content.axes ?? [];
  const columnCount = Math.max(axes.length, 1);

  function updateAxis(index: number, patch: Partial<StrategicAxis>) {
    onChange((prev) => ({ axes: prev.axes.map((a, i) => (i === index ? { ...a, ...patch } : a)) }));
  }

  const band = (label: string, required = false) => (
    <TableRow band>
      <TableCell colSpan={columnCount}>
        {label}
        {required && (
          <span className="ml-0.5 text-accent-500" aria-hidden>
            *
          </span>
        )}
      </TableCell>
    </TableRow>
  );

  return (
    <Table>
      <TableHeader>
        <TableRow>
          {axes.map((axis) => (
            <TableHead key={axis.axisCode} className="min-w-[220px] text-center">
              {directionAxisLabel(axis.axisCode, "")}
            </TableHead>
          ))}
        </TableRow>
      </TableHeader>
      <TableBody>
        {band("Orientation stratégique", true)}
        <TableRow className="hover:bg-transparent">
          {axes.map((axis, index) => (
            <TableCell key={axis.axisCode} className="py-3 align-top">
              <EditableCell
                value={axis.title}
                onChange={(v) => updateAxis(index, { title: v })}
                readOnly={readOnly}
                placeholder="Intitulé de l'orientation stratégique…"
                multiline
              />
            </TableCell>
          ))}
        </TableRow>
        {band("Objectif de l'axe (Orientation stratégique)")}
        <TableRow className="hover:bg-transparent">
          {axes.map((axis, index) => (
            <TableCell key={axis.axisCode} className="py-3 align-top">
              <EditableCell
                value={axis.objective ?? ""}
                onChange={(v) => updateAxis(index, { objective: v })}
                readOnly={readOnly}
                placeholder="Objectif de l'axe…"
                multiline
              />
            </TableCell>
          ))}
        </TableRow>
        {band("Objectifs spécifiques")}
        <TableRow className="hover:bg-transparent">
          {axes.map((axis, index) => (
            <TableCell key={axis.axisCode} className="py-3 align-top">
              <TagListEditor
                items={axis.specificObjectives}
                onChange={(items) => updateAxis(index, { specificObjectives: items })}
                readOnly={readOnly}
                placeholder="Saisir un objectif spécifique…"
              />
            </TableCell>
          ))}
        </TableRow>
      </TableBody>
    </Table>
  );
}
