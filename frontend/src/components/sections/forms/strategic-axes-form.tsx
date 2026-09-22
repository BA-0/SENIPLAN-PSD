"use client";

import { Table, TableBody, TableCell, TableHead, TableHeader, TableRow } from "@/components/ui/table";
import { EditableCell } from "@/components/data-table/editable-cell";
import { TagListEditor } from "@/components/data-table/tag-list-editor";
import { directionAxisLabel } from "@/lib/utils";
import type { StrategicAxesContent } from "@/types/sections";
import type { SectionFormProps } from "./types";

/**
 * S08 — axes strategiques et orientations, sur le modele du canevas : un axe par ligne, avec son
 * orientation strategique (l'intitule repris par toutes les sections suivantes), son objectif et
 * ses objectifs specifiques.
 */
export function StrategicAxesForm({ content, onChange, readOnly }: SectionFormProps<StrategicAxesContent>) {
  function updateAxis(index: number, patch: Partial<StrategicAxesContent["axes"][number]>) {
    onChange((prev) => ({ axes: prev.axes.map((a, i) => (i === index ? { ...a, ...patch } : a)) }));
  }

  return (
    <Table>
      <TableHeader>
        <TableRow>
          <TableHead className="w-[110px]">Axe</TableHead>
          <TableHead className="min-w-[240px]">
            Orientation stratégique
            <span className="ml-0.5 text-accent-500" aria-hidden>
              *
            </span>
          </TableHead>
          <TableHead className="min-w-[240px]">Objectif de l&apos;axe</TableHead>
          <TableHead className="min-w-[280px]">Objectifs spécifiques</TableHead>
        </TableRow>
      </TableHeader>
      <TableBody>
        {content.axes.map((axis, index) => (
          <TableRow key={axis.axisCode} className="hover:bg-transparent">
            <TableCell label>{directionAxisLabel(axis.axisCode, "")}</TableCell>
            <TableCell className="py-3 align-top">
              <EditableCell
                value={axis.title}
                onChange={(v) => updateAxis(index, { title: v })}
                readOnly={readOnly}
                placeholder="Intitulé de l'orientation stratégique…"
                multiline
              />
            </TableCell>
            <TableCell className="py-3 align-top">
              <EditableCell
                value={axis.objective ?? ""}
                onChange={(v) => updateAxis(index, { objective: v })}
                readOnly={readOnly}
                placeholder="Objectif de l'axe…"
                multiline
              />
            </TableCell>
            <TableCell className="py-3 align-top">
              <TagListEditor
                items={axis.specificObjectives}
                onChange={(items) => updateAxis(index, { specificObjectives: items })}
                readOnly={readOnly}
                placeholder="Saisir un objectif spécifique…"
              />
            </TableCell>
          </TableRow>
        ))}
      </TableBody>
    </Table>
  );
}
