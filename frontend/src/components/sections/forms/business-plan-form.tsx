"use client";

import { Card, CardContent, CardHeader, CardTitle } from "@/components/ui/card";
import { Table, TableBody, TableCell, TableHead, TableHeader, TableRow } from "@/components/ui/table";
import { EditableNumberCell } from "@/components/data-table/editable-cell";
import { formatFcfa } from "@/lib/utils";
import { CASH_FLOW_LABELS, COMPUTED_ROW_LABELS, OPERATING_ACCOUNT_LABELS, PLAN_YEARS } from "@/types/sections";
import type { BusinessPlanContent, YearlyBlock } from "@/types/sections";
import type { SectionFormProps } from "./types";

function YearlyBlockTable({
  block,
  labels,
  onUpdate,
  readOnly,
}: {
  block: YearlyBlock;
  labels: Record<string, string>;
  onUpdate: (rowIndex: number, year: number, value: number) => void;
  readOnly: boolean;
}) {
  return (
    <Table>
      <TableHeader>
        <TableRow>
          <TableHead className="min-w-[220px]">Rubrique</TableHead>
          {PLAN_YEARS.map((y) => (
            <TableHead key={y} className="text-right">{y}</TableHead>
          ))}
          <TableHead className="text-right">Total</TableHead>
        </TableRow>
      </TableHeader>
      <TableBody>
        {block.rows.map((row, rowIndex) => {
          const computed = COMPUTED_ROW_LABELS.has(row.label);
          return (
            <TableRow key={row.label} total={computed}>
              <TableCell className={computed ? "" : "font-medium text-foreground/90 text-[13px]"}>
                {labels[row.label] ?? row.label}
              </TableCell>
              {PLAN_YEARS.map((y) => (
                <TableCell key={y}>
                  {computed ? (
                    <span className="block text-right tabular-nums pr-2">{formatFcfa(row.years[String(y)])}</span>
                  ) : (
                    <EditableNumberCell
                      value={row.years[String(y)] ?? 0}
                      onChange={(v) => onUpdate(rowIndex, y, v)}
                      readOnly={readOnly}
                    />
                  )}
                </TableCell>
              ))}
              <TableCell className="text-right tabular-nums font-medium">{formatFcfa(row.total)}</TableCell>
            </TableRow>
          );
        })}
      </TableBody>
    </Table>
  );
}

export function BusinessPlanForm({ content, onChange, readOnly }: SectionFormProps<BusinessPlanContent>) {
  function updateOperating(rowIndex: number, year: number, value: number) {
    onChange((prev) => ({
      ...prev,
      operatingAccount: {
        rows: prev.operatingAccount.rows.map((r, i) =>
          i === rowIndex ? { ...r, years: { ...r.years, [String(year)]: value } } : r
        ),
      },
    }));
  }

  function updateCashFlow(rowIndex: number, year: number, value: number) {
    onChange((prev) => ({
      ...prev,
      cashFlow: {
        rows: prev.cashFlow.rows.map((r, i) => (i === rowIndex ? { ...r, years: { ...r.years, [String(year)]: value } } : r)),
      },
    }));
  }

  return (
    <div className="space-y-4">
      <Card>
        <CardHeader>
          <CardTitle>Compte d&apos;exploitation prévisionnel</CardTitle>
        </CardHeader>
        <CardContent>
          <YearlyBlockTable block={content.operatingAccount} labels={OPERATING_ACCOUNT_LABELS} onUpdate={updateOperating} readOnly={readOnly} />
        </CardContent>
      </Card>

      <Card>
        <CardHeader>
          <CardTitle>Flux financiers (trésorerie)</CardTitle>
        </CardHeader>
        <CardContent>
          <YearlyBlockTable block={content.cashFlow} labels={CASH_FLOW_LABELS} onUpdate={updateCashFlow} readOnly={readOnly} />
        </CardContent>
      </Card>
    </div>
  );
}
