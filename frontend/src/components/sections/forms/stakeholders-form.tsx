"use client";

import { Table, TableBody, TableCell, TableHead, TableHeader, TableRow } from "@/components/ui/table";
import { EditableCell } from "@/components/data-table/editable-cell";
import { AddRowButton, RemoveRowButton } from "@/components/data-table/row-actions";
import { NativeSelect } from "@/components/ui/native-select";
import { LevelSelect } from "./level-select";
import type { SectionFormProps } from "./types";
import { STAKEHOLDER_CATEGORY_LABELS, STAKEHOLDER_SCOPES, STAKEHOLDER_SCOPE_LABELS } from "@/types/sections";
import type { StakeholderCategory, StakeholderRow, StakeholdersContent } from "@/types/sections";

/**
 * L'acteur se saisit librement (demande client du 23/09/2026). Une ancienne saisie faite avec la liste
 * (« BANQUE », « ETAT »…) s'affiche sous son libellé et devient du texte libre dès qu'on la modifie.
 */
function actorText(category: string): string {
  return STAKEHOLDER_CATEGORY_LABELS[category as StakeholderCategory] ?? category;
}

const EMPTY_ROW: StakeholderRow = {
  category: "",
  scope: "",
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
            <TableHead className="min-w-[150px]">Acteur (PP)</TableHead>
            <TableHead className="min-w-[110px]">Portée</TableHead>
            <TableHead className="min-w-[180px]">Rôles / Responsabilités</TableHead>
            <TableHead className="min-w-[200px]">Attentes / Intérêt / Priorités</TableHead>
            <TableHead className="min-w-[200px]">Stratégie d&apos;adaptation</TableHead>
            <TableHead className="min-w-[110px]">Niveau importance</TableHead>
            <TableHead className="min-w-[110px]">Niveau influence</TableHead>
            <TableHead className="min-w-[160px]">Actions</TableHead>
            {!readOnly && <TableHead className="w-10" />}
          </TableRow>
        </TableHeader>
        <TableBody>
          {content.rows.map((row, index) => (
            <TableRow key={index}>
              <TableCell>
                <EditableCell
                  value={actorText(row.category)}
                  onChange={(v) => updateRow(index, { category: v })}
                  readOnly={readOnly}
                  placeholder="Nom de la partie prenante…"
                  multiline
                />
              </TableCell>
              <TableCell>
                <NativeSelect
                  cellStyle
                  value={row.scope}
                  disabled={readOnly}
                  onChange={(e) => updateRow(index, { scope: e.target.value as StakeholderRow["scope"] })}
                >
                  <option value="">—</option>
                  {STAKEHOLDER_SCOPES.map((s) => (
                    <option key={s} value={s}>
                      {STAKEHOLDER_SCOPE_LABELS[s]}
                    </option>
                  ))}
                </NativeSelect>
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
              <TableCell colSpan={9} className="text-center text-muted-foreground py-8">
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
