"use client";

import { Table, TableBody, TableCell, TableHead, TableHeader, TableRow } from "@/components/ui/table";
import { EditableCell } from "@/components/data-table/editable-cell";
import { AddRowButton, RemoveRowButton } from "@/components/data-table/row-actions";
import { TagListEditor } from "@/components/data-table/tag-list-editor";
import type { ConstraintsSynthesisContent, ConstraintsSynthesisRow } from "@/types/sections";
import type { SectionFormProps } from "./types";

const EMPTY_ROW: ConstraintsSynthesisRow = {
  domain: "",
  constraints: [],
  challenges: [],
};

/**
 * S06B — reprise du "TABLEAU 3" du client : une ligne par domaine d'activites, avec
 * ses contraintes prioritaires d'un cote et ses defis/enjeux prioritaires de l'autre.
 * Les domaines sont pre-remplis mais librement modifiables : chaque direction a les siens.
 */
export function ConstraintsSynthesisForm({ content, onChange, readOnly }: SectionFormProps<ConstraintsSynthesisContent>) {
  function updateRow(index: number, patch: Partial<ConstraintsSynthesisRow>) {
    onChange((prev) => ({ rows: prev.rows.map((r, i) => (i === index ? { ...r, ...patch } : r)) }));
  }
  function addRow() {
    onChange((prev) => ({ rows: [...prev.rows, { ...EMPTY_ROW, constraints: [], challenges: [] }] }));
  }
  function removeRow(index: number) {
    onChange((prev) => ({ rows: prev.rows.filter((_, i) => i !== index) }));
  }

  return (
    <div className="space-y-3">
      <Table>
        <TableHeader>
          <TableRow>
            <TableHead className="min-w-[200px]">Domaine d&apos;activités</TableHead>
            <TableHead className="min-w-[280px]">Contraintes prioritaires</TableHead>
            <TableHead className="min-w-[280px]">Défis et enjeux prioritaires</TableHead>
            {!readOnly && <TableHead className="w-10" />}
          </TableRow>
        </TableHeader>
        <TableBody>
          {content.rows.map((row, index) => (
            <TableRow key={index}>
              <TableCell className="align-top">
                <EditableCell
                  value={row.domain}
                  onChange={(v) => updateRow(index, { domain: v })}
                  readOnly={readOnly}
                  multiline
                  placeholder="Ex. Transport de fret"
                />
              </TableCell>
              <TableCell className="align-top">
                <TagListEditor
                  items={row.constraints ?? []}
                  onChange={(items) => updateRow(index, { constraints: items })}
                  readOnly={readOnly}
                  placeholder="Une contrainte prioritaire…"
                />
              </TableCell>
              <TableCell className="align-top">
                <TagListEditor
                  items={row.challenges ?? []}
                  onChange={(items) => updateRow(index, { challenges: items })}
                  readOnly={readOnly}
                  placeholder="Un défi ou un enjeu prioritaire…"
                />
              </TableCell>
              {!readOnly && (
                <TableCell className="align-top">
                  <RemoveRowButton onConfirm={() => removeRow(index)} />
                </TableCell>
              )}
            </TableRow>
          ))}
          {content.rows.length === 0 && (
            <TableRow>
              <TableCell colSpan={4} className="py-8 text-center text-muted-foreground">
                Aucun domaine d&apos;activités renseigné
              </TableCell>
            </TableRow>
          )}
        </TableBody>
      </Table>
      {!readOnly && <AddRowButton onAdd={addRow} label="Ajouter un domaine d'activités" />}
    </div>
  );
}
