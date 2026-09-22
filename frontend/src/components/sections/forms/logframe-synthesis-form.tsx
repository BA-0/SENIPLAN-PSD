"use client";

import { Table, TableBody, TableCell, TableEmptyRow, TableHead, TableHeader, TableRow } from "@/components/ui/table";
import { NoteTable } from "@/components/data-table/note-table";
import { directionAxisLabel } from "@/lib/utils";
import { LOGFRAME_LEVELS, LOGFRAME_LABELS } from "@/types/sections";
import type { LogframeSynthesisAxis, LogframeSynthesisContent } from "@/types/sections";
import type { SectionFormProps } from "./types";

/**
 * S09B — synthese du cadre logique : un axe par ligne, chaque niveau du cadre logique (S09)
 * ramene a une cellule. Le tableau est entierement reconstruit a la lecture depuis S09
 * (DerivedFieldsService) ; seule la note de synthese est saisie ici.
 */
export function LogframeSynthesisForm({ content, onChange, readOnly }: SectionFormProps<LogframeSynthesisContent>) {
  const axes = content.axes ?? [];

  return (
    <div className="space-y-5">
      <NoteTable
        title="Note de synthèse"
        value={content.synthesisNote ?? ""}
        onChange={(v) => onChange((prev) => ({ ...prev, synthesisNote: v }))}
        readOnly={readOnly}
        rows={5}
        placeholder="Ce que le cadre logique met en évidence, tous axes confondus…"
      />

      <section className="space-y-2">
        <h3>
          Cadre logique par axe
          <span className="ml-2 text-[12px] font-normal text-muted-foreground">lecture seule — repris du cadre logique</span>
        </h3>
        <Table>
          <TableHeader>
            <TableRow>
              <TableHead className="min-w-[160px]">Axe</TableHead>
              <TableHead className="min-w-[180px]">Objectif</TableHead>
              {LOGFRAME_LEVELS.map((level) => (
                <TableHead key={level} className="min-w-[180px] whitespace-normal">
                  {LOGFRAME_LABELS[level]}
                </TableHead>
              ))}
            </TableRow>
          </TableHeader>
          <TableBody>
            {axes.map((axis, index) => (
              <TableRow key={axis.axisCode || index}>
                <TableCell label>{directionAxisLabel(axis.axisCode, axis.axisTitle)}</TableCell>
                <TableCell className="whitespace-pre-wrap align-top text-[13px] text-foreground/90">{axis.objective || "—"}</TableCell>
                {LOGFRAME_LEVELS.map((level) => (
                  <TableCell key={level} className="whitespace-pre-wrap align-top text-[13px] text-foreground/90">
                    {axis[level as keyof LogframeSynthesisAxis] || "—"}
                  </TableCell>
                ))}
              </TableRow>
            ))}
            {axes.length === 0 && (
              <TableEmptyRow colSpan={LOGFRAME_LEVELS.length + 2}>Le cadre logique n&apos;est pas encore renseigné.</TableEmptyRow>
            )}
          </TableBody>
        </Table>
      </section>
    </div>
  );
}
