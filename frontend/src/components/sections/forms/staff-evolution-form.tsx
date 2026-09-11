"use client";

import { Fragment } from "react";
import { Table, TableBody, TableCell, TableHead, TableHeader, TableRow } from "@/components/ui/table";
import { EditableCell, EditableNumberCell } from "@/components/data-table/editable-cell";
import { AddRowButton, RemoveRowButton } from "@/components/data-table/row-actions";
import { PLAN_YEARS, STAFF_CATEGORY_LABELS, STAFF_LABELS } from "@/types/sections";
import type { StaffCategory, StaffCell, StaffEvolutionContent, StaffEvolutionRow } from "@/types/sections";
import type { SectionFormProps } from "./types";

/**
 * S14B — plan d'evolution des effectifs (statut, hierarchie, genre), place juste avant
 * le plan de financement. Trois colonnes par annee : hommes, femmes, total (calcule).
 * Les lignes suivent le modele client : "Journalier" ferme le bloc hierarchie, "Fonctionnaire"
 * ouvre le bloc statut et "Expatrie" le ferme. Le serveur rajoute a sa place une ligne fixe
 * absente d'un plan saisi avant son ajout, et le TOTAUX suit la hierarchie : les deux blocs
 * ventilent les memes agents.
 */
export function StaffEvolutionForm({ content, onChange, readOnly }: SectionFormProps<StaffEvolutionContent>) {
  const rows = content.rows ?? [];

  function updateCell(rowIndex: number, year: string, patch: Partial<StaffCell>) {
    onChange((prev) => ({
      ...prev,
      rows: prev.rows.map((r, i) =>
        i === rowIndex
          ? { ...r, years: { ...r.years, [year]: { ...(r.years?.[year] ?? { male: 0, female: 0 }), ...patch } } }
          : r
      ),
    }));
  }

  function updateRow(rowIndex: number, patch: Partial<StaffEvolutionRow>) {
    onChange((prev) => ({ ...prev, rows: prev.rows.map((r, i) => (i === rowIndex ? { ...r, ...patch } : r)) }));
  }

  function addRow(category: StaffCategory) {
    // La ligne est inseree a la fin de son propre bloc, pour ne pas casser le
    // regroupement hierarchie / statut affiche par les intertitres.
    onChange((prev) => {
      const next = [...prev.rows];
      const lastOfCategory = next.map((r) => r.category).lastIndexOf(category);
      const insertAt = lastOfCategory >= 0 ? lastOfCategory + 1 : next.length;
      const newRow: StaffEvolutionRow = {
        category,
        staffKey: "",
        label: "",
        years: Object.fromEntries(PLAN_YEARS.map((y) => [String(y), { male: 0, female: 0 }])),
      };
      next.splice(insertAt, 0, newRow);
      return { ...prev, rows: next };
    });
  }

  function removeRow(rowIndex: number) {
    onChange((prev) => ({ ...prev, rows: prev.rows.filter((_, i) => i !== rowIndex) }));
  }

  const columnCount = 1 + PLAN_YEARS.length * 3 + (readOnly ? 0 : 1);

  return (
    <div className="space-y-3">
      <Table>
        <TableHeader>
          <TableRow>
            <TableHead rowSpan={2} className="min-w-[200px] align-bottom">
              Effectifs
            </TableHead>
            {PLAN_YEARS.map((y) => (
              <TableHead key={y} colSpan={3} className="text-center border-l border-border">
                {y}
              </TableHead>
            ))}
            {!readOnly && <TableHead rowSpan={2} className="w-10" />}
          </TableRow>
          <TableRow>
            {PLAN_YEARS.map((y) => (
              <Fragment key={y}>
                <TableHead className="text-right border-l border-border">M</TableHead>
                <TableHead className="text-right">F</TableHead>
                <TableHead className="text-right">Total</TableHead>
              </Fragment>
            ))}
          </TableRow>
        </TableHeader>
        <TableBody>
          {rows.map((row, index) => {
            const isFirstOfCategory = index === 0 || rows[index - 1].category !== row.category;
            return (
              <Fragment key={index}>
                {isFirstOfCategory && (
                  <TableRow>
                    <TableCell
                      colSpan={columnCount}
                      className="bg-muted/60 text-[12px] font-semibold uppercase tracking-wide text-foreground/80"
                    >
                      {STAFF_CATEGORY_LABELS[row.category as StaffCategory] ?? row.category}
                    </TableCell>
                  </TableRow>
                )}
                <TableRow>
                  <TableCell className="text-[13px] font-medium text-foreground/90">
                    {row.staffKey ? (
                      STAFF_LABELS[row.staffKey] ?? row.staffKey
                    ) : (
                      <EditableCell
                        value={row.label ?? ""}
                        onChange={(v) => updateRow(index, { label: v })}
                        readOnly={readOnly}
                        placeholder="Intitulé de la ligne…"
                      />
                    )}
                  </TableCell>
                  {PLAN_YEARS.map((y) => {
                    const cell = row.years?.[String(y)] ?? { male: 0, female: 0 };
                    return (
                      <Fragment key={y}>
                        <TableCell className="border-l border-border">
                          <EditableNumberCell
                            value={cell.male}
                            onChange={(v) => updateCell(index, String(y), { male: v })}
                            readOnly={readOnly}
                          />
                        </TableCell>
                        <TableCell>
                          <EditableNumberCell
                            value={cell.female}
                            onChange={(v) => updateCell(index, String(y), { female: v })}
                            readOnly={readOnly}
                          />
                        </TableCell>
                        <TableCell className="text-right tabular-nums text-muted-foreground">
                          {cell.total ?? (cell.male ?? 0) + (cell.female ?? 0)}
                        </TableCell>
                      </Fragment>
                    );
                  })}
                  {!readOnly && (
                    <TableCell>
                      <RemoveRowButton onConfirm={() => removeRow(index)} />
                    </TableCell>
                  )}
                </TableRow>
              </Fragment>
            );
          })}

          <TableRow total>
            <TableCell>TOTAUX</TableCell>
            {PLAN_YEARS.map((y) => {
              const cell = content.totals?.[String(y)];
              return (
                <Fragment key={y}>
                  <TableCell className="text-right tabular-nums border-l border-border">{cell?.male ?? 0}</TableCell>
                  <TableCell className="text-right tabular-nums">{cell?.female ?? 0}</TableCell>
                  <TableCell className="text-right tabular-nums">{cell?.total ?? 0}</TableCell>
                </Fragment>
              );
            })}
            {!readOnly && <TableCell />}
          </TableRow>
        </TableBody>
      </Table>

      {!readOnly && (
        <div className="flex flex-wrap gap-2">
          <AddRowButton onAdd={() => addRow("HIERARCHIE")} label="Ajouter une ligne — hiérarchie" />
          <AddRowButton onAdd={() => addRow("STATUT")} label="Ajouter une ligne — statut" />
        </div>
      )}
    </div>
  );
}
