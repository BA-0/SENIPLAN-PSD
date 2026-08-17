"use client";

import { Tabs, TabsContent, TabsList, TabsTrigger } from "@/components/ui/tabs";
import { Table, TableBody, TableCell, TableHead, TableHeader, TableRow } from "@/components/ui/table";
import { EditableCell } from "@/components/data-table/editable-cell";
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";
import { LOGFRAME_LABELS } from "@/types/sections";
import type { LogicalFrameworkContent } from "@/types/sections";
import type { SectionFormProps } from "./types";

export function LogicalFrameworkForm({ content, onChange, readOnly }: SectionFormProps<LogicalFrameworkContent>) {
  function updateAxis(axisIndex: number, patch: Partial<LogicalFrameworkContent["axes"][number]>) {
    onChange((prev) => ({
      axes: prev.axes.map((a, i) => (i === axisIndex ? { ...a, ...patch } : a)),
    }));
  }

  function updateRow(axisIndex: number, rowIndex: number, patch: Partial<LogicalFrameworkContent["axes"][number]["rows"][number]>) {
    onChange((prev) => ({
      axes: prev.axes.map((a, i) =>
        i === axisIndex ? { ...a, rows: a.rows.map((r, ri) => (ri === rowIndex ? { ...r, ...patch } : r)) } : a
      ),
    }));
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
          <div className="space-y-1.5 max-w-xl">
            <Label required>Objectif</Label>
            <Input value={axis.objective} onChange={(e) => updateAxis(axisIndex, { objective: e.target.value })} disabled={readOnly} />
          </div>

          <Table>
            <TableHeader>
              <TableRow>
                <TableHead className="min-w-[200px]">Logique d&apos;intervention</TableHead>
                <TableHead className="min-w-[200px]">IOV</TableHead>
                <TableHead className="min-w-[200px]">Moyens et sources de vérification</TableHead>
                <TableHead className="min-w-[200px]">Conditions critiques / Hypothèses</TableHead>
              </TableRow>
            </TableHeader>
            <TableBody>
              {axis.rows.map((row, rowIndex) => (
                <TableRow key={row.level}>
                  <TableCell className="align-top">
                    <p className="text-[12px] font-semibold text-slate-500 uppercase mb-1">
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
                    <EditableCell value={row.iov} onChange={(v) => updateRow(axisIndex, rowIndex, { iov: v })} readOnly={readOnly} multiline />
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
                </TableRow>
              ))}
            </TableBody>
          </Table>
        </TabsContent>
      ))}
    </Tabs>
  );
}
