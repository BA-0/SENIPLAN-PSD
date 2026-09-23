"use client";

import { Fragment } from "react";
import { Tabs, TabsContent, TabsList, TabsTrigger } from "@/components/ui/tabs";
import { Table, TableBody, TableCell, TableEmptyRow, TableHead, TableHeader, TableRow } from "@/components/ui/table";
import { EditableCell } from "@/components/data-table/editable-cell";
import { KeyedRowAdder, RemoveRowButton, TableAddRow, insertInModelOrder, remainingOptions } from "@/components/data-table/row-actions";
import { directionAxisLabel } from "@/lib/utils";
import { PERFORMANCE_LEVEL_LABELS, PLAN_YEARS } from "@/types/sections";
import type { PerformanceFrameworkContent, PerformanceGroup, PerformanceRow } from "@/types/sections";
import type { SectionFormProps } from "./types";

const LOGFRAME_LEVELS = Object.keys(PERFORMANCE_LEVEL_LABELS);

const EMPTY_ROW: PerformanceRow = {
  resultOrExtrant: "",
  indicator: "",
  ref2026: "",
  years: Object.fromEntries(PLAN_YEARS.map((y) => [String(y), ""])),
  responsible: "",
};

/**
 * S12 — cadre de mesure du rendement, sur le modele du canevas : un tableau par axe, un bandeau par
 * niveau (impact, effet, effets immediats, extrants, ressources) et sous chacun ses lignes de
 * resultats, indicateurs et cibles annuelles. La direction ajoute les niveaux qu'elle renseigne ;
 * les effets (OS) du plan d'actions sont rappeles sous le bandeau des effets immediats.
 */
export function PerformanceFrameworkForm({ content, onChange, readOnly }: SectionFormProps<PerformanceFrameworkContent>) {
  function updateAxis(axisIndex: number, updater: (groups: PerformanceGroup[]) => PerformanceGroup[]) {
    onChange((prev) => ({ axes: prev.axes.map((a, i) => (i === axisIndex ? { ...a, groups: updater(a.groups) } : a)) }));
  }
  function updateGroup(axisIndex: number, groupIndex: number, updater: (rows: PerformanceRow[]) => PerformanceRow[]) {
    updateAxis(axisIndex, (groups) => groups.map((g, i) => (i === groupIndex ? { ...g, rows: updater(g.rows) } : g)));
  }
  function updateRow(axisIndex: number, groupIndex: number, rowIndex: number, patch: Partial<PerformanceRow>) {
    updateGroup(axisIndex, groupIndex, (rows) => rows.map((r, ri) => (ri === rowIndex ? { ...r, ...patch } : r)));
  }
  function addRow(axisIndex: number, groupIndex: number) {
    updateGroup(axisIndex, groupIndex, (rows) => [...rows, { ...EMPTY_ROW, years: { ...EMPTY_ROW.years } }]);
  }
  function removeRow(axisIndex: number, groupIndex: number, rowIndex: number) {
    updateGroup(axisIndex, groupIndex, (rows) => rows.filter((_, ri) => ri !== rowIndex));
  }
  function addGroup(axisIndex: number, level: string) {
    updateAxis(axisIndex, (groups) => insertInModelOrder(groups, { level, rows: [] }, (g) => g.level, LOGFRAME_LEVELS));
  }
  function removeGroup(axisIndex: number, groupIndex: number) {
    updateAxis(axisIndex, (groups) => groups.filter((_, i) => i !== groupIndex));
  }

  const colCount = 3 + PLAN_YEARS.length + 1 + (readOnly ? 0 : 1);

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
        <TabsContent key={axis.axisCode} value={axis.axisCode} className="space-y-3">
          <Table>
            <TableHeader>
              <TableRow>
                <TableHead className="min-w-[180px] whitespace-normal">Résultat / Extrant</TableHead>
                <TableHead className="min-w-[180px] whitespace-normal">Indicateur (IOV)</TableHead>
                <TableHead className="min-w-[120px]">Réf. 2026</TableHead>
                {PLAN_YEARS.map((y) => (
                  <TableHead key={y} className="min-w-[90px]">
                    {y}
                  </TableHead>
                ))}
                <TableHead className="min-w-[130px]">Responsables</TableHead>
                {!readOnly && <TableHead className="w-10" />}
              </TableRow>
            </TableHeader>
            <TableBody>
              {axis.groups.map((group, groupIndex) => (
                <Fragment key={group.level}>
                  <TableRow band>
                    <TableCell colSpan={colCount}>
                      <div className="flex items-center justify-between gap-2">
                        <span>{PERFORMANCE_LEVEL_LABELS[group.level] ?? group.level}</span>
                        {!readOnly && <RemoveRowButton onConfirm={() => removeGroup(axisIndex, groupIndex)} />}
                      </div>
                    </TableCell>
                  </TableRow>
                  {group.syncedEffects && group.syncedEffects.length > 0 && (
                    <TableRow className="hover:bg-transparent">
                      <TableCell colSpan={colCount} className="py-2">
                        <div className="flex flex-wrap items-center gap-1.5">
                          <span className="text-[12px] text-muted-foreground">Effets (OS) définis dans le plan d&apos;actions :</span>
                          {group.syncedEffects.map((e, i) => (
                            <span
                              key={i}
                              className="rounded-full bg-primary-50 px-2 py-0.5 text-[12px] text-primary-700 dark:bg-primary-500/15 dark:text-primary-300"
                            >
                              {e.osCode}
                              {e.effectLabel ? ` — ${e.effectLabel}` : ""}
                            </span>
                          ))}
                        </div>
                      </TableCell>
                    </TableRow>
                  )}
                  {group.rows.map((row, rowIndex) => (
                    <TableRow key={rowIndex}>
                      <TableCell className="align-top">
                        <EditableCell
                          value={row.resultOrExtrant}
                          onChange={(v) => updateRow(axisIndex, groupIndex, rowIndex, { resultOrExtrant: v })}
                          readOnly={readOnly}
                          multiline
                        />
                      </TableCell>
                      <TableCell className="align-top">
                        <EditableCell
                          value={row.indicator}
                          onChange={(v) => updateRow(axisIndex, groupIndex, rowIndex, { indicator: v })}
                          readOnly={readOnly}
                          multiline
                        />
                      </TableCell>
                      <TableCell className="align-top">
                        <EditableCell
                          value={row.ref2026}
                          onChange={(v) => updateRow(axisIndex, groupIndex, rowIndex, { ref2026: v })}
                          readOnly={readOnly}
                        />
                      </TableCell>
                      {PLAN_YEARS.map((y) => (
                        <TableCell key={y} className="align-top">
                          <EditableCell
                            value={row.years[String(y)] ?? ""}
                            onChange={(v) => updateRow(axisIndex, groupIndex, rowIndex, { years: { ...row.years, [String(y)]: v } })}
                            readOnly={readOnly}
                          />
                        </TableCell>
                      ))}
                      <TableCell className="align-top">
                        <EditableCell
                          value={row.responsible}
                          onChange={(v) => updateRow(axisIndex, groupIndex, rowIndex, { responsible: v })}
                          readOnly={readOnly}
                        />
                      </TableCell>
                      {!readOnly && (
                        <TableCell className="align-top">
                          <RemoveRowButton onConfirm={() => removeRow(axisIndex, groupIndex, rowIndex)} />
                        </TableCell>
                      )}
                    </TableRow>
                  ))}
                  {readOnly && group.rows.length === 0 && <TableEmptyRow colSpan={colCount}>Aucune ligne pour ce niveau</TableEmptyRow>}
                  {!readOnly && <TableAddRow colSpan={colCount} onAdd={() => addRow(axisIndex, groupIndex)} label="Ajouter une ligne" />}
                </Fragment>
              ))}
              {axis.groups.length === 0 && <TableEmptyRow colSpan={colCount}>Aucun niveau renseigné pour cet axe</TableEmptyRow>}
            </TableBody>
          </Table>
          {!readOnly && (
            <KeyedRowAdder
              options={remainingOptions(
                LOGFRAME_LEVELS,
                PERFORMANCE_LEVEL_LABELS,
                axis.groups.map((g) => g.level)
              )}
              onAdd={(level) => addGroup(axisIndex, level)}
              label="Ajouter le niveau"
              placeholder="Choisir un niveau (impact, effet, extrants…)…"
            />
          )}
        </TabsContent>
      ))}
    </Tabs>
  );
}
