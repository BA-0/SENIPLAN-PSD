"use client";

import { Fragment } from "react";
import { Table, TableBody, TableCell, TableHead, TableHeader, TableRow } from "@/components/ui/table";
import { EditableCell, EditableNumberCell } from "@/components/data-table/editable-cell";
import { KeyedRowAdder, RemoveRowButton } from "@/components/data-table/row-actions";
import { PLAN_YEARS, STAFF_CATEGORY_LABELS, STAFF_LABELS } from "@/types/sections";
import type { StaffCategory, StaffCell, StaffEvolutionContent, StaffEvolutionRow } from "@/types/sections";
import type { SectionFormProps } from "./types";

/** Lignes du modele client, bloc par bloc (DefaultSectionContentFactory.STAFF_ROWS). */
const STAFF_MODEL_ROWS: { category: StaffCategory; keys: string[] }[] = [
  {
    category: "HIERARCHIE",
    keys: ["CADRE", "AGENTS_MAITRISE", "EMPLOYE", "JOURNALIER"],
  },
  {
    category: "STATUT",
    keys: ["CDI", "EXPATRIE", "CDD", "STAGIAIRE", "JOURNALIER"],
  },
];
const CATEGORY_ORDER: string[] = STAFF_MODEL_ROWS.map((b) => b.category);

/**
 * S14B — plan d'evolution des effectifs (statut, hierarchie, genre), place juste avant
 * le plan de financement. Trois colonnes par annee : hommes, femmes, total (calcule).
 * Les lignes suivent le modele client : "Journalier" ferme le bloc hierarchie ; le bloc statut
 * aligne CDI, Expatrie, CDD, Stagiaire et Journalier ("Fonctionnaire" retire a la revue du
 * 15/09/2026). Le tableau demarre vide : la direction ajoute les lignes du modele qu'elle renseigne,
 * ou une ligne libre, et le TOTAUX suit la hierarchie : les deux blocs ventilent les memes agents.
 */
export function StaffEvolutionForm({ content, onChange, readOnly }: SectionFormProps<StaffEvolutionContent>) {
  const rows = content.rows ?? [];

  function updateCell(rowIndex: number, year: string, patch: Partial<StaffCell>) {
    onChange((prev) => ({
      ...prev,
      rows: prev.rows.map((r, i) =>
        i === rowIndex
          ? {
              ...r,
              years: {
                ...r.years,
                [year]: {
                  ...(r.years?.[year] ?? { male: 0, female: 0 }),
                  ...patch,
                },
              },
            }
          : r
      ),
    }));
  }

  function updateRow(rowIndex: number, patch: Partial<StaffEvolutionRow>) {
    onChange((prev) => ({
      ...prev,
      rows: prev.rows.map((r, i) => (i === rowIndex ? { ...r, ...patch } : r)),
    }));
  }

  function addRow(choice: string) {
    const [category, staffKey] = choice.split(":");
    // Les blocs restent dans l'ordre hierarchie puis statut, et chaque ligne du modele a sa place
    // dans son bloc ; une ligne libre ferme son bloc.
    onChange((prev) => {
      const block = STAFF_MODEL_ROWS.find((b) => b.category === category)?.keys ?? [];
      const rank = (r: StaffEvolutionRow) => {
        const c = CATEGORY_ORDER.indexOf(String(r.category));
        const k = r.staffKey ? block.indexOf(r.staffKey) : -1;
        return (c < 0 ? CATEGORY_ORDER.length : c) * 100 + (r.category === category && k >= 0 ? k : 99);
      };
      const newRow: StaffEvolutionRow = {
        category,
        staffKey,
        label: "",
        years: Object.fromEntries(PLAN_YEARS.map((y) => [String(y), { male: 0, female: 0 }])),
      };
      const at = prev.rows.findIndex((r) => rank(r) > rank(newRow));
      const next = at < 0 ? [...prev.rows, newRow] : [...prev.rows.slice(0, at), newRow, ...prev.rows.slice(at)];
      return { ...prev, rows: next };
    });
  }

  const addOptions = STAFF_MODEL_ROWS.flatMap(({ category, keys }) => [
    ...keys
      .filter((k) => !rows.some((r) => r.category === category && r.staffKey === k))
      .map((k) => ({
        value: `${category}:${k}`,
        label: `${STAFF_CATEGORY_LABELS[category]} — ${STAFF_LABELS[k] ?? k}`,
      })),
    {
      value: `${category}:`,
      label: `${STAFF_CATEGORY_LABELS[category]} — autre ligne (intitulé libre)`,
    },
  ]);

  function removeRow(rowIndex: number) {
    onChange((prev) => ({
      ...prev,
      rows: prev.rows.filter((_, i) => i !== rowIndex),
    }));
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
                  <TableRow band>
                    <TableCell colSpan={columnCount}>{STAFF_CATEGORY_LABELS[row.category as StaffCategory] ?? row.category}</TableCell>
                  </TableRow>
                )}
                <TableRow>
                  <TableCell className="text-[13px] font-medium text-foreground/90">
                    {row.staffKey ? (
                      (STAFF_LABELS[row.staffKey] ?? row.staffKey)
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
                    const cell = row.years?.[String(y)] ?? {
                      male: 0,
                      female: 0,
                    };
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

          {rows.length === 0 && (
            <TableRow>
              <TableCell colSpan={columnCount} className="py-8 text-center text-muted-foreground">
                Aucune ligne d&apos;effectifs renseignée
              </TableCell>
            </TableRow>
          )}

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
        <KeyedRowAdder options={addOptions} onAdd={addRow} label="Ajouter la ligne" placeholder="Choisir une ligne d'effectifs…" />
      )}
    </div>
  );
}
