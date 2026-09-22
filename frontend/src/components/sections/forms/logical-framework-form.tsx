"use client";

import { Tabs, TabsContent, TabsList, TabsTrigger } from "@/components/ui/tabs";
import { Table, TableBody, TableCell, TableEmptyRow, TableHead, TableHeader, TableRow } from "@/components/ui/table";
import { EditableCell } from "@/components/data-table/editable-cell";
import { KeyedRowAdder, RemoveRowButton, insertInModelOrder, remainingOptions } from "@/components/data-table/row-actions";
import { directionAxisLabel } from "@/lib/utils";
import { LOGFRAME_LABELS } from "@/types/sections";
import type { LogicalFrameworkContent } from "@/types/sections";
import type { SectionFormProps } from "./types";

const LOGFRAME_LEVELS = Object.keys(LOGFRAME_LABELS);

type LogframeRow = LogicalFrameworkContent["axes"][number]["rows"][number];

/**
 * S09 — cadre logique, sur le modele du canevas : un tableau par axe, l'objectif de l'axe en
 * premiere ligne, puis un niveau par ligne (impact, effet, effets immediats, extrants, ressources)
 * avec sa logique d'intervention, ses IOV, ses moyens de verification et ses hypotheses. La direction
 * ajoute les niveaux qu'elle renseigne.
 */
export function LogicalFrameworkForm({ content, onChange, readOnly }: SectionFormProps<LogicalFrameworkContent>) {
  function updateAxis(axisIndex: number, patch: Partial<LogicalFrameworkContent["axes"][number]>) {
    onChange((prev) => ({ axes: prev.axes.map((a, i) => (i === axisIndex ? { ...a, ...patch } : a)) }));
  }
  function updateRows(axisIndex: number, updater: (rows: LogframeRow[]) => LogframeRow[]) {
    onChange((prev) => ({ axes: prev.axes.map((a, i) => (i === axisIndex ? { ...a, rows: updater(a.rows) } : a)) }));
  }
  function updateRow(axisIndex: number, rowIndex: number, patch: Partial<LogframeRow>) {
    updateRows(axisIndex, (rows) => rows.map((r, ri) => (ri === rowIndex ? { ...r, ...patch } : r)));
  }
  function addRow(axisIndex: number, level: string) {
    const row: LogframeRow = { level, interventionLogic: "", iov: "", verificationMeans: "", assumptions: "" };
    updateRows(axisIndex, (rows) => insertInModelOrder(rows, row, (r) => r.level, LOGFRAME_LEVELS));
  }
  function removeRow(axisIndex: number, rowIndex: number) {
    updateRows(axisIndex, (rows) => rows.filter((_, ri) => ri !== rowIndex));
  }

  const colCount = readOnly ? 5 : 6;

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
                <TableHead className="min-w-[180px] whitespace-normal">Niveau</TableHead>
                <TableHead className="min-w-[220px] whitespace-normal">Logique d&apos;intervention</TableHead>
                <TableHead className="min-w-[200px]">IOV</TableHead>
                <TableHead className="min-w-[200px] whitespace-normal">Moyens et sources de vérification</TableHead>
                <TableHead className="min-w-[200px] whitespace-normal">Conditions critiques / Hypothèses</TableHead>
                {!readOnly && <TableHead className="w-10" />}
              </TableRow>
            </TableHeader>
            <TableBody>
              <TableRow className="hover:bg-transparent">
                <TableCell label>
                  Objectif de l&apos;axe
                  <span className="ml-0.5 text-accent-500" aria-hidden>
                    *
                  </span>
                </TableCell>
                <TableCell colSpan={colCount - 1} className="py-3">
                  <EditableCell
                    value={axis.objective}
                    onChange={(v) => updateAxis(axisIndex, { objective: v })}
                    readOnly={readOnly}
                    placeholder="Objectif de l'axe…"
                    multiline
                  />
                </TableCell>
              </TableRow>

              {axis.rows.map((row, rowIndex) => (
                <TableRow key={row.level} className="hover:bg-transparent">
                  <TableCell label>{LOGFRAME_LABELS[row.level] ?? row.level}</TableCell>
                  <TableCell className="align-top">
                    <EditableCell
                      value={row.interventionLogic}
                      onChange={(v) => updateRow(axisIndex, rowIndex, { interventionLogic: v })}
                      readOnly={readOnly}
                      multiline
                    />
                  </TableCell>
                  <TableCell className="align-top">
                    <EditableCell
                      value={row.iov}
                      onChange={(v) => updateRow(axisIndex, rowIndex, { iov: v })}
                      readOnly={readOnly}
                      multiline
                    />
                  </TableCell>
                  <TableCell className="align-top">
                    <EditableCell
                      value={row.verificationMeans}
                      onChange={(v) => updateRow(axisIndex, rowIndex, { verificationMeans: v })}
                      readOnly={readOnly}
                      multiline
                    />
                  </TableCell>
                  <TableCell className="align-top">
                    <EditableCell
                      value={row.assumptions}
                      onChange={(v) => updateRow(axisIndex, rowIndex, { assumptions: v })}
                      readOnly={readOnly}
                      multiline
                    />
                  </TableCell>
                  {!readOnly && (
                    <TableCell className="align-top">
                      <RemoveRowButton onConfirm={() => removeRow(axisIndex, rowIndex)} />
                    </TableCell>
                  )}
                </TableRow>
              ))}
              {axis.rows.length === 0 && <TableEmptyRow colSpan={colCount}>Aucun niveau renseigné pour cet axe</TableEmptyRow>}
            </TableBody>
          </Table>
          {!readOnly && (
            <KeyedRowAdder
              options={remainingOptions(
                LOGFRAME_LEVELS,
                LOGFRAME_LABELS,
                axis.rows.map((r) => r.level)
              )}
              onAdd={(level) => addRow(axisIndex, level)}
              label="Ajouter le niveau"
              placeholder="Choisir un niveau (impact, effet, extrants…)…"
            />
          )}
        </TabsContent>
      ))}
    </Tabs>
  );
}
