"use client";

import { Table, TableBody, TableCell, TableHead, TableHeader, TableRow } from "@/components/ui/table";
import { EditableCell } from "@/components/data-table/editable-cell";
import { AddRowButton, RemoveRowButton } from "@/components/data-table/row-actions";
import { LevelSelect } from "./level-select";
import type { SectionFormProps } from "./types";
import type { StakeholderRow, StakeholdersContent } from "@/types/sections";

const EMPTY_ROW: StakeholderRow = {
  actor: "",
  roles: "",
  expectations: "",
  adaptationStrategy: "",
  importance: "",
  influence: "",
  actions: "",
};

export function StakeholdersForm({ content, onChange, readOnly }: SectionFormProps<StakeholdersContent>) {
  function updateRow(index: number, patch: Partial<StakeholderRow>) {
    onChange((prev) => ({
      rows: prev.rows.map((r, i) => (i === index ? { ...r, ...patch } : r)),
    }));
  }

  function removeRow(index: number) {
    onChange((prev) => ({ rows: prev.rows.filter((_, i) => i !== index) }));
  }

  function addRow() {
    onChange((prev) => ({ rows: [...prev.rows, { ...EMPTY_ROW }] }));
  }

  return (
    <div className="space-y-3">
      <Table>
        <TableHeader>
          <TableRow>
            <TableHead className="min-w-[160px]">Acteur (PP)</TableHead>
            <TableHead className="min-w-[180px]">Rôles / Responsabilités</TableHead>
            <TableHead className="min-w-[200px]">Attentes / Intérêt / Priorités</TableHead>
            <TableHead className="min-w-[200px]">Stratégie d&apos;adaptation</TableHead>
            <TableHead className="min-w-[110px]">Importance</TableHead>
            <TableHead className="min-w-[110px]">Influence</TableHead>
            <TableHead className="min-w-[160px]">Actions</TableHead>
            {!readOnly && <TableHead className="w-10" />}
          </TableRow>
        </TableHeader>
        <TableBody>
          {content.rows.map((row, index) => (
            <TableRow key={index}>
              <TableCell>
                <EditableCell value={row.actor} onChange={(v) => updateRow(index, { actor: v })} readOnly={readOnly} />
              </TableCell>
              <TableCell>
                <EditableCell value={row.roles} onChange={(v) => updateRow(index, { roles: v })} readOnly={readOnly} multiline />
              </TableCell>
              <TableCell>
                <EditableCell value={row.expectations} onChange={(v) => updateRow(index, { expectations: v })} readOnly={readOnly} multiline />
              </TableCell>
              <TableCell>
                <EditableCell value={row.adaptationStrategy} onChange={(v) => updateRow(index, { adaptationStrategy: v })} readOnly={readOnly} multiline />
              </TableCell>
              <TableCell>
                <LevelSelect value={row.importance} onChange={(v) => updateRow(index, { importance: v })} readOnly={readOnly} />
              </TableCell>
              <TableCell>
                <LevelSelect value={row.influence} onChange={(v) => updateRow(index, { influence: v })} readOnly={readOnly} />
              </TableCell>
              <TableCell>
                <EditableCell value={row.actions} onChange={(v) => updateRow(index, { actions: v })} readOnly={readOnly} multiline />
              </TableCell>
              {!readOnly && (
                <TableCell>
                  <RemoveRowButton onConfirm={() => removeRow(index)} />
                </TableCell>
              )}
            </TableRow>
          ))}
          {content.rows.length === 0 && (
            <TableRow>
              <TableCell colSpan={8} className="text-center text-muted-foreground py-8">
                Aucune partie prenante renseignée
              </TableCell>
            </TableRow>
          )}
        </TableBody>
      </Table>
      {!readOnly && <AddRowButton onAdd={addRow} label="Ajouter une partie prenante" />}
    </div>
  );
}
