"use client";

import { Plus } from "lucide-react";
import { Tabs, TabsContent, TabsList, TabsTrigger } from "@/components/ui/tabs";
import { Table, TableBody, TableCell, TableHead, TableHeader, TableRow } from "@/components/ui/table";
import { EditableCell, EditableNumberCell } from "@/components/data-table/editable-cell";
import { AddRowButton, RemoveRowButton } from "@/components/data-table/row-actions";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { Card, CardContent, CardHeader } from "@/components/ui/card";
import { formatFcfa } from "@/lib/utils";
import { PLAN_YEARS } from "@/types/sections";
import type { BudgetContent, BudgetEffect, BudgetRow } from "@/types/sections";
import type { SectionFormProps } from "./types";

const EMPTY_ROW: BudgetRow = {
  extrant: "",
  activities: "",
  years: Object.fromEntries(PLAN_YEARS.map((y) => [String(y), 0])),
  responsible: "",
};

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
    updateAxis(axisIndex, (effects) => [
      ...effects,
      { effectCode: `EFFET${effects.length + 1}`, osCode: `OS${effects.length + 1}`, effectLabel: "", rows: [] },
    ]);
  }

  function removeEffect(axisIndex: number, effectIndex: number) {
    updateAxis(axisIndex, (effects) => effects.filter((_, i) => i !== effectIndex));
  }

  function updateRow(axisIndex: number, effectIndex: number, rowIndex: number, patch: Partial<BudgetRow>) {
    updateEffect(axisIndex, effectIndex, {
      rows: content.axes[axisIndex].effects[effectIndex].rows.map((r, i) => (i === rowIndex ? { ...r, ...patch } : r)),
    });
  }

  function addRow(axisIndex: number, effectIndex: number) {
    const effect = content.axes[axisIndex].effects[effectIndex];
    updateEffect(axisIndex, effectIndex, { rows: [...effect.rows, { ...EMPTY_ROW, years: { ...EMPTY_ROW.years } }] });
  }

  function removeRow(axisIndex: number, effectIndex: number, rowIndex: number) {
    const effect = content.axes[axisIndex].effects[effectIndex];
    updateEffect(axisIndex, effectIndex, { rows: effect.rows.filter((_, i) => i !== rowIndex) });
  }

  return (
    <Tabs defaultValue={content.axes[0]?.axisCode}>
      <TabsList>
        {content.axes.map((axis) => (
          <TabsTrigger key={axis.axisCode} value={axis.axisCode}>
            {axis.axisCode.replace("AXE", "Axe ")}
            {axis.axisTitle ? ` — ${axis.axisTitle}` : ""}
          </TabsTrigger>
        ))}
      </TabsList>

      {content.axes.map((axis, axisIndex) => (
        <TabsContent key={axis.axisCode} value={axis.axisCode} className="space-y-4">
          {axis.effects.map((effect, effectIndex) => (
            <Card key={effectIndex}>
              <CardHeader className="flex-col items-stretch gap-2">
                <div className="flex items-center justify-between">
                  <span className="text-[13px] font-semibold text-primary-700">
                    {effect.effectCode} — {effect.osCode}
                  </span>
                  {!readOnly && <RemoveRowButton onConfirm={() => removeEffect(axisIndex, effectIndex)} />}
                </div>
                <Input
                  value={effect.effectLabel}
                  onChange={(e) => updateEffect(axisIndex, effectIndex, { effectLabel: e.target.value })}
                  disabled={readOnly}
                  placeholder="Intitulé de l'effet…"
                />
              </CardHeader>
              <CardContent className="space-y-3">
                <Table>
                  <TableHeader>
                    <TableRow>
                      <TableHead className="min-w-[160px]">Extrants</TableHead>
                      <TableHead className="min-w-[180px]">Activités</TableHead>
                      {PLAN_YEARS.map((y) => (
                        <TableHead key={y} className="text-right">{y}</TableHead>
                      ))}
                      <TableHead className="text-right">Total</TableHead>
                      <TableHead className="min-w-[120px]">Responsable</TableHead>
                      {!readOnly && <TableHead className="w-10" />}
                    </TableRow>
                  </TableHeader>
                  <TableBody>
                    {effect.rows.map((row, rowIndex) => (
                      <TableRow key={rowIndex}>
                        <TableCell>
                          <EditableCell value={row.extrant} onChange={(v) => updateRow(axisIndex, effectIndex, rowIndex, { extrant: v })} readOnly={readOnly} />
                        </TableCell>
                        <TableCell>
                          <EditableCell value={row.activities} onChange={(v) => updateRow(axisIndex, effectIndex, rowIndex, { activities: v })} readOnly={readOnly} multiline />
                        </TableCell>
                        {PLAN_YEARS.map((y) => (
                          <TableCell key={y}>
                            <EditableNumberCell
                              value={row.years[String(y)] ?? 0}
                              onChange={(v) => updateRow(axisIndex, effectIndex, rowIndex, { years: { ...row.years, [String(y)]: v } })}
                              readOnly={readOnly}
                            />
                          </TableCell>
                        ))}
                        <TableCell className="text-right tabular-nums font-medium text-slate-700">
                          {formatFcfa(row.rowTotal)}
                        </TableCell>
                        <TableCell>
                          <EditableCell value={row.responsible} onChange={(v) => updateRow(axisIndex, effectIndex, rowIndex, { responsible: v })} readOnly={readOnly} />
                        </TableCell>
                        {!readOnly && (
                          <TableCell>
                            <RemoveRowButton onConfirm={() => removeRow(axisIndex, effectIndex, rowIndex)} />
                          </TableCell>
                        )}
                      </TableRow>
                    ))}
                    {effect.rows.length > 0 && (
                      <TableRow total>
                        <TableCell colSpan={2}>Total effet</TableCell>
                        {PLAN_YEARS.map((y) => (
                          <TableCell key={y} className="text-right tabular-nums">
                            {formatFcfa(effect.yearTotals?.[String(y)])}
                          </TableCell>
                        ))}
                        <TableCell className="text-right tabular-nums">{formatFcfa(effect.effectTotal)}</TableCell>
                        <TableCell colSpan={readOnly ? 1 : 2} />
                      </TableRow>
                    )}
                  </TableBody>
                </Table>
                {!readOnly && <AddRowButton onAdd={() => addRow(axisIndex, effectIndex)} label="Ajouter un extrant" />}
              </CardContent>
            </Card>
          ))}
          {!readOnly && (
            <Button type="button" variant="secondary" size="sm" onClick={() => addEffect(axisIndex)}>
              <Plus className="h-4 w-4" /> Ajouter un effet (OS)
            </Button>
          )}
          <div className="flex justify-end">
            <div className="rounded-lg bg-primary-50 px-4 py-2 text-[13px] font-semibold text-primary-700">
              Total axe : {formatFcfa(axis.axisTotal)}
            </div>
          </div>
        </TabsContent>
      ))}

      <div className="flex justify-end mt-4 pt-4 border-t border-slate-200">
        <div className="rounded-lg bg-primary-500 px-5 py-2.5 text-[15px] font-bold text-white">
          Total général : {formatFcfa(content.grandTotal)}
        </div>
      </div>
    </Tabs>
  );
}
