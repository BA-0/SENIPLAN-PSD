"use client";

import { Table, TableBody, TableCell, TableHead, TableHeader, TableRow } from "@/components/ui/table";
import { EditableCell } from "@/components/data-table/editable-cell";
import { TagListEditor } from "@/components/data-table/tag-list-editor";
import type { StrategicFrameworkContent } from "@/types/sections";
import type { SectionFormProps } from "./types";

/** S07B — cadre strategique : la vision, la mission et les valeurs, un element par ligne du tableau. */
export function StrategicFrameworkForm({ content, onChange, readOnly }: SectionFormProps<StrategicFrameworkContent>) {
  return (
    <Table>
      <TableHeader>
        <TableRow>
          <TableHead className="w-[200px]">Élément</TableHead>
          <TableHead className="min-w-[360px]">Contenu</TableHead>
        </TableRow>
      </TableHeader>
      <TableBody>
        <TableRow className="hover:bg-transparent">
          <TableCell label>
            Vision
            <span className="ml-0.5 text-accent-500" aria-hidden>
              *
            </span>
          </TableCell>
          <TableCell className="py-3">
            <EditableCell
              value={content.vision}
              onChange={(v) => onChange((prev) => ({ ...prev, vision: v }))}
              readOnly={readOnly}
              placeholder="Vision globale du Plan Stratégique 2027-2031…"
              multiline
              rows={3}
            />
          </TableCell>
        </TableRow>
        <TableRow className="hover:bg-transparent">
          <TableCell label>Mission</TableCell>
          <TableCell className="py-3">
            <TagListEditor
              items={content.mission}
              onChange={(items) => onChange((prev) => ({ ...prev, mission: items }))}
              readOnly={readOnly}
              placeholder="Saisir un élément de mission…"
            />
          </TableCell>
        </TableRow>
        <TableRow className="hover:bg-transparent">
          <TableCell label>Valeurs</TableCell>
          <TableCell className="py-3">
            <TagListEditor
              items={content.values}
              onChange={(items) => onChange((prev) => ({ ...prev, values: items }))}
              readOnly={readOnly}
              placeholder="Saisir une valeur…"
            />
          </TableCell>
        </TableRow>
      </TableBody>
    </Table>
  );
}
