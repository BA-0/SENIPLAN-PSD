"use client";

import { Fragment } from "react";
import { Tabs, TabsContent, TabsList, TabsTrigger } from "@/components/ui/tabs";
import { Table, TableBody, TableCell, TableEmptyRow, TableHead, TableHeader, TableRow } from "@/components/ui/table";
import { EditableCell, EditableNumberCell } from "@/components/data-table/editable-cell";
import { RemoveRowButton, TableAddRow } from "@/components/data-table/row-actions";
import { directionAxisLabel, formatFcfa } from "@/lib/utils";
import { PLAN_YEARS } from "@/types/sections";
import type { BudgetContent, BudgetEffect, BudgetRow } from "@/types/sections";
import type { SectionFormProps } from "./types";

const EMPTY_ROW: BudgetRow = {
  extrant: "",
  activities: "",
  years: Object.fromEntries(PLAN_YEARS.map((y) => [String(y), 0])),
  responsible: "",
};

/**
 * S11 — budget 2027-2031, sur le modele du canevas : un tableau par axe, un bandeau par effet
 * (EFFET n — OSn, avec son intitule), sous chacun ses extrants et leurs montants annuels, puis les
 * totaux de l'effet et de l'axe. Les totaux sont calcules par le serveur a chaque enregistrement.
 */
export function BudgetForm({ content, onChange, readOnly }: SectionFormProps<BudgetContent>) {
  function updateAxis(axisIndex: number, updater: (effects: BudgetEffect[]) => BudgetEffect[]) {
    onChange((prev) => ({
      ...prev,
      axes: prev.axes.map((a, i) => (i === axisIndex ? { ...a, effects: updater(a.effects) } : a)),
    }));
  }
  function updateEffect(axisIndex: number, effectIndex: number, patch: Partial<BudgetEffect>) {
    updateAxis(axisIndex, (effects) => effects.map((e, i) => (i === effectIndex ? { ...e, ...patch } : e)));
  }
  function addEffect(axisIndex: number) {
    // Numero suivant le plus grand deja pris : supprimer l'effet 1 de deux ne fait pas renaitre un second « EFFET2 ».
    updateAxis(axisIndex, (effects) => {
      const next = effects.reduce((max, e) => Math.max(max, Number(e.effectCode.match(/\d+/)?.[0] ?? 0)), 0) + 1;
      return [...effects, { effectCode: `EFFET${next}`, osCode: `OS${next}`, effectLabel: "", rows: [] }];
    });
  }
  function removeEffect(axisIndex: number, effectIndex: number) {
    updateAxis(axisIndex, (effects) => effects.filter((_, i) => i !== effectIndex));
  }
  function updateRows(axisIndex: number, effectIndex: number, updater: (rows: BudgetRow[]) => BudgetRow[]) {
    updateAxis(axisIndex, (effects) => effects.map((e, i) => (i === effectIndex ? { ...e, rows: updater(e.rows) } : e)));
  }
  function updateRow(axisIndex: number, effectIndex: number, rowIndex: number, patch: Partial<BudgetRow>) {
    updateRows(axisIndex, effectIndex, (rows) => rows.map((r, i) => (i === rowIndex ? { ...r, ...patch } : r)));
  }
  function addRow(axisIndex: number, effectIndex: number) {
    updateRows(axisIndex, effectIndex, (rows) => [...rows, { ...EMPTY_ROW, years: { ...EMPTY_ROW.years } }]);
  }
  function removeRow(axisIndex: number, effectIndex: number, rowIndex: number) {
    updateRows(axisIndex, effectIndex, (rows) => rows.filter((_, i) => i !== rowIndex));
  }

  const trailing = readOnly ? 1 : 2; // responsable (+ colonne de suppression)
  const colCount = 2 + PLAN_YEARS.length + 1 + trailing;

  return (
    <Tabs defaultValue={content.axes[0]?.axisCode}>
      <TabsList className="axis-tabs">
        {content.axes.map((axis) => (
          <TabsTrigger key={axis.axisCode} value={axis.axisCode}>
            {directionAxisLabel(axis.axisCode, axis.axisTitle)}
          </TabsTrigger>
        ))}
      </TabsList>

      {content.axes.map((axis, axisIndex) => (
        <TabsContent key={axis.axisCode} value={axis.axisCode}>
          <Table>
            <TableHeader>
              <TableRow>
                <TableHead className="min-w-[160px]">Extrants</TableHead>
                <TableHead className="min-w-[180px] whitespace-normal">Activités pour atteindre les résultats</TableHead>
                {PLAN_YEARS.map((y) => (
                  <TableHead key={y} className="text-right">
                    {y}
                  </TableHead>
                ))}
                <TableHead className="text-right">Totaux</TableHead>
                <TableHead className="min-w-[120px]">Responsable</TableHead>
                {!readOnly && <TableHead className="w-10" />}
              </TableRow>
            </TableHeader>
            <TableBody>
              {axis.effects.map((effect, effectIndex) => (
                <Fragment key={effectIndex}>
                  <TableRow band>
                    <TableCell colSpan={colCount} className="py-2">
                      <div className="flex flex-wrap items-center gap-3">
                        <span className="shrink-0 text-primary-700 dark:text-primary-300">
                          {effect.effectCode.replace(/^EFFET\s*/,"EFFET ")} — {effect.osCode} :
                        </span>
                        <div className="min-w-[260px] flex-1 text-sm font-normal normal-case tracking-normal">
                          <EditableCell
                            value={effect.effectLabel}
                            onChange={(v) => updateEffect(axisIndex, effectIndex, { effectLabel: v })}
                            readOnly={readOnly}
                            placeholder="Intitulé de l'effet…"
                          />
                        </div>
                        {!readOnly && <RemoveRowButton onConfirm={() => removeEffect(axisIndex, effectIndex)} />}
                      </div>
                    </TableCell>
                  </TableRow>
                  {effect.rows.map((row, rowIndex) => (
                    <TableRow key={rowIndex}>
                      <TableCell className="align-top">
                        <EditableCell
                          value={row.extrant}
                          onChange={(v) => updateRow(axisIndex, effectIndex, rowIndex, { extrant: v })}
                          readOnly={readOnly}
                          multiline
                        />
                      </TableCell>
                      <TableCell className="align-top">
                        <EditableCell
                          value={row.activities}
                          onChange={(v) => updateRow(axisIndex, effectIndex, rowIndex, { activities: v })}
                          readOnly={readOnly}
                          multiline
                        />
                      </TableCell>
                      {PLAN_YEARS.map((y) => (
                        <TableCell key={y} className="align-top">
                          <EditableNumberCell
                            value={row.years[String(y)] ?? 0}
                            onChange={(v) => updateRow(axisIndex, effectIndex, rowIndex, { years: { ...row.years, [String(y)]: v } })}
                            readOnly={readOnly}
                          />
                        </TableCell>
                      ))}
                      <TableCell className="text-right align-top pt-3.5 tabular-nums font-medium text-foreground/90">
                        {formatFcfa(row.rowTotal)}
                      </TableCell>
                      <TableCell className="align-top">
                        <EditableCell
                          value={row.responsible}
                          onChange={(v) => updateRow(axisIndex, effectIndex, rowIndex, { responsible: v })}
                          readOnly={readOnly}
                        />
                      </TableCell>
                      {!readOnly && (
                        <TableCell className="align-top">
                          <RemoveRowButton onConfirm={() => removeRow(axisIndex, effectIndex, rowIndex)} />
                        </TableCell>
                      )}
                    </TableRow>
                  ))}
                  {readOnly && effect.rows.length === 0 && <TableEmptyRow colSpan={colCount}>Aucun extrant pour cet effet</TableEmptyRow>}
                  {effect.rows.length > 0 && (
                    <TableRow total>
                      <TableCell colSpan={2}>Total de l&apos;effet</TableCell>
                      {PLAN_YEARS.map((y) => (
                        <TableCell key={y} className="text-right tabular-nums">
                          {formatFcfa(effect.yearTotals?.[String(y)])}
                        </TableCell>
                      ))}
                      <TableCell className="text-right tabular-nums">{formatFcfa(effect.effectTotal)}</TableCell>
                      <TableCell colSpan={trailing} />
                    </TableRow>
                  )}
                  {!readOnly && <TableAddRow colSpan={colCount} onAdd={() => addRow(axisIndex, effectIndex)} label="Ajouter un extrant" />}
                </Fragment>
              ))}
              {axis.effects.length === 0 && <TableEmptyRow colSpan={colCount}>Aucun effet (OS) renseigné pour cet axe</TableEmptyRow>}
              {!readOnly && <TableAddRow colSpan={colCount} onAdd={() => addEffect(axisIndex)} label="Ajouter un effet (OS)" emphasis />}
              <TableRow total className="border-t-2 border-t-primary-200 dark:border-t-primary-500/40">
                <TableCell colSpan={2 + PLAN_YEARS.length}>Total de l&apos;axe</TableCell>
                <TableCell className="text-right tabular-nums">{formatFcfa(axis.axisTotal)}</TableCell>
                <TableCell colSpan={trailing} />
              </TableRow>
            </TableBody>
          </Table>
        </TabsContent>
      ))}

      <div className="mt-4 flex justify-end border-t border-border pt-4">
        <div className="rounded-lg bg-primary-500 px-5 py-2.5 text-[15px] font-bold text-white">
          Total général : {formatFcfa(content.grandTotal)}
        </div>
      </div>
    </Tabs>
  );
}
