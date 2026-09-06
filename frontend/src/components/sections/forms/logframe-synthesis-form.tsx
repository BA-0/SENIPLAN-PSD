"use client";

import { Label } from "@/components/ui/label";
import { Textarea } from "@/components/ui/textarea";
import { Table, TableBody, TableCell, TableHead, TableHeader, TableRow } from "@/components/ui/table";
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
    <div className="space-y-4">
      <div className="space-y-1.5">
        <Label htmlFor="logframe-synthesis-note">Note de synthèse</Label>
        <Textarea
          id="logframe-synthesis-note"
          value={content.synthesisNote ?? ""}
          onChange={(e) => onChange((prev) => ({ ...prev, synthesisNote: e.target.value }))}
          readOnly={readOnly}
          rows={5}
          placeholder="Ce que le cadre logique met en évidence, tous axes confondus…"
        />
      </div>

      <div className="space-y-2">
        <p className="text-[13px] font-medium text-foreground/90">
          Cadre logique par axe
          <span className="ml-2 font-normal text-[12px] text-muted-foreground">
            lecture seule — repris du cadre logique (S09)
          </span>
        </p>
        <Table>
          <TableHeader>
            <TableRow>
              <TableHead className="min-w-[160px]">Axe</TableHead>
              <TableHead className="min-w-[180px]">Objectif</TableHead>
              {LOGFRAME_LEVELS.map((level) => (
                <TableHead key={level} className="min-w-[180px]">
                  {LOGFRAME_LABELS[level]}
                </TableHead>
              ))}
            </TableRow>
          </TableHeader>
          <TableBody>
            {axes.map((axis, index) => (
              <TableRow key={axis.axisCode || index}>
                <TableCell className="text-[13px] font-medium text-foreground/90">
                  {axis.axisCode?.replace("AXE", "Axe ")}
                  {axis.axisTitle ? ` — ${axis.axisTitle}` : ""}
                </TableCell>
                <TableCell className="whitespace-pre-wrap text-[13px] text-foreground/90">{axis.objective || "—"}</TableCell>
                {LOGFRAME_LEVELS.map((level) => (
                  <TableCell key={level} className="whitespace-pre-wrap text-[13px] text-foreground/90">
                    {axis[level as keyof LogframeSynthesisAxis] || "—"}
                  </TableCell>
                ))}
              </TableRow>
            ))}
            {axes.length === 0 && (
              <TableRow>
                <TableCell colSpan={LOGFRAME_LEVELS.length + 2} className="py-8 text-center text-muted-foreground">
                  Le cadre logique (S09) n&apos;est pas encore renseigné.
                </TableCell>
              </TableRow>
            )}
          </TableBody>
        </Table>
      </div>
    </div>
  );
}
