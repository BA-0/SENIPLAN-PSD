"use client";

import { Tabs, TabsContent, TabsList, TabsTrigger } from "@/components/ui/tabs";
import { Table, TableBody, TableCell, TableHead, TableHeader, TableRow } from "@/components/ui/table";
import { EditableCell } from "@/components/data-table/editable-cell";
import { AddRowButton, RemoveRowButton } from "@/components/data-table/row-actions";
import { LOGFRAME_LABELS, PLAN_YEARS } from "@/types/sections";
import type { PerformanceFrameworkContent, PerformanceGroup, PerformanceRow } from "@/types/sections";
import type { SectionFormProps } from "./types";

const EMPTY_ROW: PerformanceRow = {
  resultOrExtrant: "",
  indicator: "",
  ref2026: "",
  years: Object.fromEntries(PLAN_YEARS.map((y) => [String(y), ""])),
  responsible: "",
};

export function PerformanceFrameworkForm({ content, onChange, readOnly }: SectionFormProps<PerformanceFrameworkContent>) {
  function updateAxis(axisIndex: number, updater: (groups: PerformanceGroup[]) => PerformanceGroup[]) {
    onChange((prev) => ({
      axes: prev.axes.map((a, i) => (i === axisIndex ? { ...a, groups: updater(a.groups) } : a)),
    }));
  }

  function updateRow(axisIndex: number, groupIndex: number, rowIndex: number, patch: Partial<PerformanceRow>) {
    updateAxis(axisIndex, (groups) =>
      groups.map((g, i) => (i === groupIndex ? { ...g, rows: g.rows.map((r, ri) => (ri === rowIndex ? { ...r, ...patch } : r)) } : g))
    );
  }

  function addRow(axisIndex: number, groupIndex: number) {
    updateAxis(axisIndex, (groups) =>
      groups.map((g, i) => (i === groupIndex ? { ...g, rows: [...g.rows, { ...EMPTY_ROW, years: { ...EMPTY_ROW.years } }] } : g))
    );
  }

  function removeRow(axisIndex: number, groupIndex: number, rowIndex: number) {
    updateAxis(axisIndex, (groups) =>
      groups.map((g, i) => (i === groupIndex ? { ...g, rows: g.rows.filter((_, ri) => ri !== rowIndex) } : g))
    );
  }

  return (
    <Tabs defaultValue={content.axes[0]?.axisCode}>
      <TabsList>
        {content.axes.map((axis) => (
          <TabsTrigger key={axis.axisCode} value={axis.axisCode}>
            {axis.axisCode.replace("AXE", "Axe ")}
          </TabsTrigger>
        ))}
      </TabsList>

      {content.axes.map((axis, axisIndex) => (
        <TabsContent key={axis.axisCode} value={axis.axisCode} className="space-y-5">
          {axis.groups.map((group, groupIndex) => (
            <div key={group.level}>
              <h3 className="mb-2">{LOGFRAME_LABELS[group.level] ?? group.level}</h3>
              {group.syncedEffects && group.syncedEffects.length > 0 && (
                <div className="mb-2 flex flex-wrap gap-1.5">
                  <span className="text-[12px] text-slate-500">Effets (OS) définis en Section 10 :</span>
                  {group.syncedEffects.map((e, i) => (
                    <span key={i} className="text-[12px] rounded-full bg-primary-50 text-primary-700 px-2 py-0.5">
                      {e.osCode}
                      {e.effectLabel ? ` — ${e.effectLabel}` : ""}
                    </span>
                  ))}
                </div>
              )}
              <Table>
                <TableHeader>
                  <TableRow>
                    <TableHead className="min-w-[180px]">Résultat / Extrant</TableHead>
                    <TableHead className="min-w-[180px]">Indicateur (IOV)</TableHead>
                    <TableHead className="min-w-[100px]">Réf. 2026</TableHead>
                    {PLAN_YEARS.map((y) => (
                      <TableHead key={y} className="min-w-[90px]">{y}</TableHead>
                    ))}
                    <TableHead className="min-w-[130px]">Responsables</TableHead>
                    {!readOnly && <TableHead className="w-10" />}
                  </TableRow>
                </TableHeader>
                <TableBody>
                  {group.rows.map((row, rowIndex) => (
                    <TableRow key={rowIndex}>
                      <TableCell>
                        <EditableCell value={row.resultOrExtrant} onChange={(v) => updateRow(axisIndex, groupIndex, rowIndex, { resultOrExtrant: v })} readOnly={readOnly} />
                      </TableCell>
                      <TableCell>
                        <EditableCell value={row.indicator} onChange={(v) => updateRow(axisIndex, groupIndex, rowIndex, { indicator: v })} readOnly={readOnly} />
                      </TableCell>
                      <TableCell>
                        <EditableCell value={row.ref2026} onChange={(v) => updateRow(axisIndex, groupIndex, rowIndex, { ref2026: v })} readOnly={readOnly} />
                      </TableCell>
                      {PLAN_YEARS.map((y) => (
                        <TableCell key={y}>
                          <EditableCell
                            value={row.years[String(y)] ?? ""}
                            onChange={(v) => updateRow(axisIndex, groupIndex, rowIndex, { years: { ...row.years, [String(y)]: v } })}
                            readOnly={readOnly}
                          />
                        </TableCell>
                      ))}
                      <TableCell>
                        <EditableCell value={row.responsible} onChange={(v) => updateRow(axisIndex, groupIndex, rowIndex, { responsible: v })} readOnly={readOnly} />
                      </TableCell>
                      {!readOnly && (
                        <TableCell>
                          <RemoveRowButton onConfirm={() => removeRow(axisIndex, groupIndex, rowIndex)} />
                        </TableCell>
                      )}
                    </TableRow>
                  ))}
                </TableBody>
              </Table>
              {!readOnly && (
                <div className="mt-2">
                  <AddRowButton onAdd={() => addRow(axisIndex, groupIndex)} />
                </div>
              )}
            </div>
          ))}
        </TabsContent>
      ))}
    </Tabs>
  );
}
