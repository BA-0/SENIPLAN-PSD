"use client";

import { Fragment } from "react";
import { Tabs, TabsContent, TabsList, TabsTrigger } from "@/components/ui/tabs";
import { Table, TableBody, TableCell, TableEmptyRow, TableHead, TableHeader, TableRow } from "@/components/ui/table";
import { EditableCell, EditableNumberCell } from "@/components/data-table/editable-cell";
import { RemoveRowButton, TableAddRow } from "@/components/data-table/row-actions";
import { directionAxisLabel } from "@/lib/utils";
import { PLAN_YEARS } from "@/types/sections";
import type { ActionPlanContent, ActionPlanEffect, ActionPlanRow } from "@/types/sections";
import type { SectionFormProps } from "./types";

const EMPTY_ROW: ActionPlanRow = {
  extrant: "",
  activities: "",
  objective: "",
  budget: 0,
  years: Object.fromEntries(PLAN_YEARS.map((y) => [String(y), false])),
  responsible: "",
};

/**
 * S10 — plan d'actions 2027-2031, sur le modele du canevas : un tableau par axe, un bandeau par
 * effet (EFFET n — OSn, avec son intitule) et sous chacun ses extrants, activites, annees de
 * realisation prevues et responsables. La direction ajoute ses effets et ses extrants.
 */
export function ActionPlanForm({ content, onChange, readOnly }: SectionFormProps<ActionPlanContent>) {
  function updateAxis(axisIndex: number, updater: (effects: ActionPlanEffect[]) => ActionPlanEffect[]) {
    onChange((prev) => ({
      ...prev,
      axes: prev.axes.map((a, i) => (i === axisIndex ? { ...a, effects: updater(a.effects) } : a)),
    }));
  }
  function updateEffect(axisIndex: number, effectIndex: number, patch: Partial<ActionPlanEffect>) {
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
  function updateRows(axisIndex: number, effectIndex: number, updater: (rows: ActionPlanRow[]) => ActionPlanRow[]) {
    updateAxis(axisIndex, (effects) => effects.map((e, i) => (i === effectIndex ? { ...e, rows: updater(e.rows) } : e)));
  }
  function updateRow(axisIndex: number, effectIndex: number, rowIndex: number, patch: Partial<ActionPlanRow>) {
    updateRows(axisIndex, effectIndex, (rows) => rows.map((r, i) => (i === rowIndex ? { ...r, ...patch } : r)));
  }
  function addRow(axisIndex: number, effectIndex: number) {
    updateRows(axisIndex, effectIndex, (rows) => [...rows, { ...EMPTY_ROW, years: { ...EMPTY_ROW.years } }]);
  }
  function removeRow(axisIndex: number, effectIndex: number, rowIndex: number) {
    updateRows(axisIndex, effectIndex, (rows) => rows.filter((_, i) => i !== rowIndex));
  }

  const colCount = 4 + PLAN_YEARS.length + 1 + (readOnly ? 0 : 1);

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
                <TableHead className="min-w-[180px]">Extrants</TableHead>
                <TableHead className="min-w-[200px]">Activités</TableHead>
                <TableHead className="min-w-[180px]">Objectif</TableHead>
                <TableHead className="min-w-[120px] text-right">Budget (FCFA)</TableHead>
                {PLAN_YEARS.map((y) => (
                  <TableHead key={y} className="text-center">
                    Prévu {y}
                  </TableHead>
                ))}
                <TableHead className="min-w-[140px]">Responsables</TableHead>
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
                          {effect.effectCode} — {effect.osCode}
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
                      <TableCell className="align-top">
                        <EditableCell
                          value={row.objective ?? ""}
                          onChange={(v) => updateRow(axisIndex, effectIndex, rowIndex, { objective: v })}
                          readOnly={readOnly}
                          multiline
                        />
                      </TableCell>
                      <TableCell className="align-top">
                        <EditableNumberCell
                          value={row.budget ?? 0}
                          onChange={(v) => updateRow(axisIndex, effectIndex, rowIndex, { budget: v })}
                          readOnly={readOnly}
                        />
                      </TableCell>
                      {PLAN_YEARS.map((y) => (
                        <TableCell key={y} className="text-center align-top">
                          <input
                            type="checkbox"
                            checked={!!row.years[String(y)]}
                            disabled={readOnly}
                            onChange={(e) =>
                              updateRow(axisIndex, effectIndex, rowIndex, { years: { ...row.years, [String(y)]: e.target.checked } })
                            }
                            className="mt-2.5 h-4 w-4 rounded border-slate-300 text-primary-500 focus:ring-primary-500/40"
                          />
                        </TableCell>
                      ))}
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
                  {!readOnly && <TableAddRow colSpan={colCount} onAdd={() => addRow(axisIndex, effectIndex)} label="Ajouter un extrant" />}
                </Fragment>
              ))}
              {axis.effects.length === 0 && <TableEmptyRow colSpan={colCount}>Aucun effet (OS) renseigné pour cet axe</TableEmptyRow>}
              {!readOnly && <TableAddRow colSpan={colCount} onAdd={() => addEffect(axisIndex)} label="Ajouter un effet (OS)" emphasis />}
            </TableBody>
          </Table>
        </TabsContent>
      ))}
    </Tabs>
  );
}
