"use client";

import { Table, TableBody, TableCell, TableHead, TableHeader, TableRow } from "@/components/ui/table";
import { EditableCell } from "@/components/data-table/editable-cell";
import { KeyedRowAdder, RemoveRowButton, insertInModelOrder, remainingOptions } from "@/components/data-table/row-actions";
import { RESOURCE_KEYS, RESOURCE_LABELS } from "@/types/sections";
import type { ResourceRow, ResourcesMatrixContent } from "@/types/sections";
import type { SectionFormProps } from "./types";

const OTHER_RESOURCE = "AUTRES_ACHATS_EXPLOITATION_TECHNIQUE_RH";

export function ResourcesMatrixForm({ content, onChange, readOnly }: SectionFormProps<ResourcesMatrixContent>) {
  function updateRow(index: number, patch: Partial<ResourceRow>) {
    onChange((prev) => ({
      rows: prev.rows.map((r, i) => (i === index ? { ...r, ...patch } : r)),
    }));
  }
  function addRow(resourceKey: string) {
    const labelOf = (k: string) => (RESOURCE_LABELS[k] ?? k).toLowerCase();
    if (content.rows.some((r) => labelOf(r.resourceKey) === labelOf(resourceKey))) return;
    onChange((prev) => ({
      rows: insertInModelOrder(
        prev.rows,
        { resourceKey, strengths: "", weaknesses: "", challenges: "" },
        (r) => r.resourceKey,
        RESOURCE_KEYS
      ),
    }));
  }
  function removeRow(index: number) {
    onChange((prev) => ({ rows: prev.rows.filter((_, i) => i !== index) }));
  }

  return (
    <div className="space-y-3">
      <Table>
        <TableHeader>
          <TableRow>
            <TableHead className="min-w-[280px]">Ressources</TableHead>
            <TableHead className="min-w-[220px]">Forces / Acquis</TableHead>
            <TableHead className="min-w-[220px]">Faiblesses</TableHead>
            <TableHead className="min-w-[220px]">Défis à relever</TableHead>
            {!readOnly && <TableHead className="w-10" />}
          </TableRow>
        </TableHeader>
        <TableBody>
          {content.rows.map((row, index) => (
            <TableRow key={row.resourceKey}>
              <TableCell className="border-l-2 border-l-primary-300 py-2.5 align-top font-medium text-foreground/90 text-[13px] leading-snug">
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
              {!readOnly && (
                <TableCell className="py-2.5 align-top">
                  <RemoveRowButton onConfirm={() => removeRow(index)} />
                </TableCell>
              )}
            </TableRow>
          ))}
          {content.rows.length === 0 && (
            <TableRow>
              <TableCell colSpan={readOnly ? 4 : 5} className="py-8 text-center text-muted-foreground">
                Aucune ressource renseignée
              </TableCell>
            </TableRow>
          )}
        </TableBody>
      </Table>
      {!readOnly && (
        <KeyedRowAdder
          options={remainingOptions(
            RESOURCE_KEYS,
            RESOURCE_LABELS,
            content.rows.map((r) => r.resourceKey)
          )}
          onAdd={addRow}
          label="Ajouter la ressource"
          placeholder="Choisir une ressource…"
          other={{ value: OTHER_RESOURCE, label: RESOURCE_LABELS[OTHER_RESOURCE], placeholder: "Préciser la ressource…" }}
        />
      )}
    </div>
  );
}
