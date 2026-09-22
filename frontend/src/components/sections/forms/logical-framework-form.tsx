"use client";

import { Tabs, TabsContent, TabsList, TabsTrigger } from "@/components/ui/tabs";
import { Table, TableBody, TableCell, TableHead, TableHeader, TableRow } from "@/components/ui/table";
import { EditableCell } from "@/components/data-table/editable-cell";
import { KeyedRowAdder, RemoveRowButton, insertInModelOrder, remainingOptions } from "@/components/data-table/row-actions";
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";
import { directionAxisLabel } from "@/lib/utils";
import { LOGFRAME_LABELS } from "@/types/sections";
import type { LogicalFrameworkContent } from "@/types/sections";
import type { SectionFormProps } from "./types";

const LOGFRAME_LEVELS = Object.keys(LOGFRAME_LABELS);

export function LogicalFrameworkForm({ content, onChange, readOnly }: SectionFormProps<LogicalFrameworkContent>) {
  function updateAxis(axisIndex: number, patch: Partial<LogicalFrameworkContent["axes"][number]>) {
    onChange((prev) => ({
      axes: prev.axes.map((a, i) => (i === axisIndex ? { ...a, ...patch } : a)),
    }));
  }

  function updateRow(axisIndex: number, rowIndex: number, patch: Partial<LogicalFrameworkContent["axes"][number]["rows"][number]>) {
    onChange((prev) => ({
      axes: prev.axes.map((a, i) =>
        i === axisIndex
          ? {
              ...a,
              rows: a.rows.map((r, ri) => (ri === rowIndex ? { ...r, ...patch } : r)),
            }
          : a
      ),
    }));
  }

  function addRow(axisIndex: number, level: string) {
    const row = {
      level,
      interventionLogic: "",
      iov: "",
      verificationMeans: "",
      assumptions: "",
    };
    onChange((prev) => ({
      axes: prev.axes.map((a, i) =>
        i === axisIndex
          ? {
              ...a,
              rows: insertInModelOrder(a.rows, row, (r) => r.level, LOGFRAME_LEVELS),
            }
          : a
      ),
    }));
  }

  function removeRow(axisIndex: number, rowIndex: number) {
    onChange((prev) => ({
      axes: prev.axes.map((a, i) => (i === axisIndex ? { ...a, rows: a.rows.filter((_, ri) => ri !== rowIndex) } : a)),
    }));
  }

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
        <TabsContent key={axis.axisCode} value={axis.axisCode} className="space-y-4">
          <div className="space-y-1.5 max-w-xl">
            <Label required>Objectif</Label>
            <Input value={axis.objective} onChange={(e) => updateAxis(axisIndex, { objective: e.target.value })} readOnly={readOnly} />
          </div>

          <Table>
            <TableHeader>
              <TableRow>
                <TableHead className="min-w-[200px]">Logique d&apos;intervention</TableHead>
                <TableHead className="min-w-[200px]">IOV</TableHead>
                <TableHead className="min-w-[200px]">Moyens et sources de vérification</TableHead>
                <TableHead className="min-w-[200px]">Conditions critiques / Hypothèses</TableHead>
                {!readOnly && <TableHead className="w-10" />}
              </TableRow>
            </TableHeader>
            <TableBody>
              {axis.rows.map((row, rowIndex) => (
                <TableRow key={row.level}>
                  <TableCell className="align-top">
                    <p className="text-[12px] font-semibold text-muted-foreground uppercase mb-1">
                      {LOGFRAME_LABELS[row.level] ?? row.level}
                    </p>
                    <EditableCell
                      value={row.interventionLogic}
                      onChange={(v) => updateRow(axisIndex, rowIndex, { interventionLogic: v })}
                      readOnly={readOnly}
                      multiline
                    />
                  </TableCell>
                  <TableCell>
                    <EditableCell
                      value={row.iov}
                      onChange={(v) => updateRow(axisIndex, rowIndex, { iov: v })}
                      readOnly={readOnly}
                      multiline
                    />
                  </TableCell>
                  <TableCell>
                    <EditableCell
                      value={row.verificationMeans}
                      onChange={(v) => updateRow(axisIndex, rowIndex, { verificationMeans: v })}
                      readOnly={readOnly}
                      multiline
                    />
                  </TableCell>
                  <TableCell>
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
              {axis.rows.length === 0 && (
                <TableRow>
                  <TableCell colSpan={readOnly ? 4 : 5} className="py-8 text-center text-muted-foreground">
                    Aucun niveau renseigné pour cet axe
                  </TableCell>
                </TableRow>
              )}
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
