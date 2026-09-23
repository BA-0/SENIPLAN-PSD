"use client";

import { Fragment } from "react";
import { Minus, TrendingDown, TrendingUp } from "lucide-react";

import { Table, TableBody, TableCell, TableEmptyRow, TableHead, TableHeader, TableRow } from "@/components/ui/table";
import { NativeSelect } from "@/components/ui/native-select";
import { EditableCell } from "@/components/data-table/editable-cell";
import { RemoveRowButton, TableAddRow } from "@/components/data-table/row-actions";
import { cn } from "@/lib/utils";
import { PAST_PERFORMANCE_YEARS, PERFORMANCE_REVIEW_YEAR, PERFORMANCE_TREND_LABELS, PERFORMANCE_TRENDS } from "@/types/sections";
import type { PerformanceReview2026Content, PerformanceReviewRow, PerformanceTrend } from "@/types/sections";
import type { SectionFormProps } from "./types";

/** Les sept colonnes du modele client pour l'exercice en cours. */
const COLUMNS = [
  "Objectif",
  "Indicateur",
  "Résultat attendu",
  "Écart",
  "Cause sous-jacente",
  "Cause profonde",
  "Action à entreprendre",
] as const;
/** Exercices ecoules : les memes colonnes, le resultat y est celui obtenu et l'action celle deja entreprise. */
const PAST_COLUMNS = [
  "Objectif",
  "Indicateur",
  "Résultat obtenu",
  "Écart",
  "Cause sous-jacente",
  "Cause profonde",
  "Action entreprise",
] as const;

const TREND_STYLES: Record<PerformanceTrend, { text: string; cell: string; Icon: typeof TrendingUp }> = {
  FAVORABLE: { text: "text-primary-700 dark:text-primary-300", cell: "bg-primary-50/70 dark:bg-primary-500/10", Icon: TrendingUp },
  STABLE: { text: "text-blue-700 dark:text-blue-300", cell: "bg-blue-50/70 dark:bg-blue-500/10", Icon: Minus },
  DEFAVORABLE: { text: "text-orange-700 dark:text-orange-300", cell: "bg-orange-50/70 dark:bg-orange-500/10", Icon: TrendingDown },
};

function emptyRow(year: number): PerformanceReviewRow {
  return { year, objective: "", indicator: "", expectedResult: "", gap: "", cause: "", rootCause: "", action: "", trend: "" };
}

/** Exercice d'une ligne : une ligne de l'ancien tableau, sans exercice, est celle de l'annee en cours. */
function yearOf(row: PerformanceReviewRow): number {
  const year = Number(row.year);
  return Number.isFinite(year) && year > 0 ? year : PERFORMANCE_REVIEW_YEAR;
}

function trendOf(row: PerformanceReviewRow): PerformanceTrend | "" {
  return PERFORMANCE_TRENDS.includes(row.trend as PerformanceTrend) ? (row.trend as PerformanceTrend) : "";
}

/**
 * S01B — bilan des performances, en deux tableaux : les cinq exercices ecoules regroupes dans le premier
 * (un bandeau par exercice, le resultat y est celui obtenu), l'exercice en cours seul dans le second,
 * aux sept colonnes du modele client, ou chaque indicateur porte sa tendance vers le resultat attendu en decembre. Les lignes sont
 * stockees dans une seule liste, distinguees par leur exercice (`year`).
 */
export function PerformanceReview2026Form({ content, onChange, readOnly }: SectionFormProps<PerformanceReview2026Content>) {
  const rows = content.rows ?? [];
  const indexed = rows.map((row, index) => ({ row, index }));
  const pastYears = Array.from(
    new Set<number>([...PAST_PERFORMANCE_YEARS, ...indexed.map(({ row }) => yearOf(row)).filter((y) => y < PERFORMANCE_REVIEW_YEAR)])
  ).sort((a, b) => a - b);
  const current = indexed.filter(({ row }) => yearOf(row) >= PERFORMANCE_REVIEW_YEAR);
  const columnCount = COLUMNS.length + (readOnly ? 0 : 1);

  function updateRow(index: number, patch: Partial<PerformanceReviewRow>) {
    onChange((prev) => ({ rows: prev.rows.map((r, i) => (i === index ? { ...r, ...patch } : r)) }));
  }
  function addRow(year: number) {
    onChange((prev) => ({ rows: [...prev.rows, emptyRow(year)] }));
  }
  function removeRow(index: number) {
    onChange((prev) => ({ rows: prev.rows.filter((_, i) => i !== index) }));
  }

  return (
    <div className="space-y-8">
      <section className="space-y-3">
        <div>
          <h3>Performances des années passées</h3>
          <p className="mt-1 text-[12.5px] text-muted-foreground">
            Les cinq exercices écoulés ({pastYears[0]}-{pastYears[pastYears.length - 1]}) dans un seul tableau, un bandeau par exercice.
          </p>
        </div>
        <Table>
          <ReviewHeader columns={PAST_COLUMNS} readOnly={readOnly} />
          <TableBody>
            {pastYears.map((year) => {
              const yearRows = indexed.filter(({ row }) => yearOf(row) === year);
              return (
                <Fragment key={year}>
                  <TableRow band>
                    <TableCell colSpan={columnCount}>Exercice {year}</TableCell>
                  </TableRow>
                  {yearRows.map(({ row, index }) => (
                    <ReviewRow
                      key={index}
                      row={row}
                      readOnly={readOnly}
                      onChange={(patch) => updateRow(index, patch)}
                      onRemove={() => removeRow(index)}
                    />
                  ))}
                  {readOnly && yearRows.length === 0 && (
                    <TableRow className="hover:bg-transparent">
                      <TableCell colSpan={columnCount} className="py-3 text-center text-[13px] italic text-muted-foreground">
                        Aucune performance renseignée pour {year}
                      </TableCell>
                    </TableRow>
                  )}
                  {!readOnly && <TableAddRow colSpan={columnCount} onAdd={() => addRow(year)} label={`Ajouter une ligne ${year}`} />}
                </Fragment>
              );
            })}
          </TableBody>
        </Table>
      </section>

      <section className="space-y-3">
        <div>
          <h3>Performances de l&apos;année {PERFORMANCE_REVIEW_YEAR} et tendances</h3>
          <p className="mt-1 text-[12.5px] text-muted-foreground">
            L&apos;exercice {PERFORMANCE_REVIEW_YEAR} est en cours : le résultat attendu en décembre est une projection à date, et chaque
            indicateur porte sa tendance (favorable, stable ou défavorable) dans la colonne Écart.
          </p>
        </div>
        <Table>
          <ReviewHeader columns={COLUMNS} readOnly={readOnly} />
          <TableBody>
            {current.map(({ row, index }) => (
              <ReviewRow
                key={index}
                row={row}
                readOnly={readOnly}
                withTrend
                onChange={(patch) => updateRow(index, patch)}
                onRemove={() => removeRow(index)}
              />
            ))}
            {current.length === 0 && (
              <TableEmptyRow colSpan={columnCount}>Aucune performance {PERFORMANCE_REVIEW_YEAR} renseignée</TableEmptyRow>
            )}
            {!readOnly && (
              <TableAddRow
                colSpan={columnCount}
                onAdd={() => addRow(PERFORMANCE_REVIEW_YEAR)}
                label={`Ajouter une ligne ${PERFORMANCE_REVIEW_YEAR}`}
              />
            )}
          </TableBody>
        </Table>
        {current.length > 0 && <TrendSummary rows={current.map(({ row }) => row)} />}
      </section>
    </div>
  );
}

function ReviewHeader({ columns, readOnly }: { columns: readonly string[]; readOnly: boolean }) {
  return (
    <TableHeader>
      <TableRow>
        {columns.map((column) => (
          <TableHead key={column} className={cn("min-w-[170px]", column === "Écart" && "min-w-[200px]")}>
            {column}
          </TableHead>
        ))}
        {!readOnly && <TableHead className="w-10" />}
      </TableRow>
    </TableHeader>
  );
}

function ReviewRow({
  row,
  readOnly,
  withTrend = false,
  onChange,
  onRemove,
}: {
  row: PerformanceReviewRow;
  readOnly: boolean;
  /** Exercice en cours : la tendance de l'indicateur se choisit sous l'ecart. */
  withTrend?: boolean;
  onChange: (patch: Partial<PerformanceReviewRow>) => void;
  onRemove: () => void;
}) {
  const trend = trendOf(row);
  const look = trend ? TREND_STYLES[trend] : null;
  return (
    <TableRow>
      <TableCell>
        <EditableCell value={row.objective ?? ""} onChange={(v) => onChange({ objective: v })} readOnly={readOnly} multiline />
      </TableCell>
      <TableCell>
        <EditableCell value={row.indicator ?? ""} onChange={(v) => onChange({ indicator: v })} readOnly={readOnly} multiline />
      </TableCell>
      <TableCell>
        <EditableCell value={row.expectedResult ?? ""} onChange={(v) => onChange({ expectedResult: v })} readOnly={readOnly} />
      </TableCell>
      <TableCell className={cn(withTrend && look?.cell)}>
        <div className="space-y-1.5">
          <EditableCell value={row.gap ?? ""} onChange={(v) => onChange({ gap: v })} readOnly={readOnly} />
          {withTrend && (
            <div className="flex items-center gap-1.5">
              {look && <look.Icon className={cn("h-3.5 w-3.5 shrink-0", look.text)} aria-hidden />}
              <NativeSelect
                cellStyle
                aria-label={`Tendance ${PERFORMANCE_REVIEW_YEAR}`}
                value={trend}
                disabled={readOnly}
                onChange={(e) => onChange({ trend: e.target.value as PerformanceTrend | "" })}
                className={cn("text-[12.5px] font-medium", look?.text)}
              >
                <option value="">—</option>
                {PERFORMANCE_TRENDS.map((t) => (
                  <option key={t} value={t}>
                    Tendance {PERFORMANCE_TREND_LABELS[t].toLowerCase()}
                  </option>
                ))}
              </NativeSelect>
            </div>
          )}
        </div>
      </TableCell>
      <TableCell>
        <EditableCell value={row.cause ?? ""} onChange={(v) => onChange({ cause: v })} readOnly={readOnly} multiline />
      </TableCell>
      <TableCell>
        <EditableCell value={row.rootCause ?? ""} onChange={(v) => onChange({ rootCause: v })} readOnly={readOnly} multiline />
      </TableCell>
      <TableCell>
        <EditableCell value={row.action ?? ""} onChange={(v) => onChange({ action: v })} readOnly={readOnly} multiline />
      </TableCell>
      {!readOnly && (
        <TableCell>
          <RemoveRowButton onConfirm={onRemove} />
        </TableCell>
      )}
    </TableRow>
  );
}

/** Lecture des tendances de l'exercice en cours : combien d'indicateurs favorables, stables, defavorables. */
function TrendSummary({ rows }: { rows: PerformanceReviewRow[] }) {
  const counts: Record<PerformanceTrend, number> = { FAVORABLE: 0, STABLE: 0, DEFAVORABLE: 0 };
  let unset = 0;
  for (const row of rows) {
    const trend = trendOf(row);
    if (trend) counts[trend] += 1;
    else unset += 1;
  }
  return (
    <div className="flex flex-wrap items-center gap-x-4 gap-y-1.5 text-[12.5px]">
      <span className="font-medium text-foreground/80">Tendances {PERFORMANCE_REVIEW_YEAR} :</span>
      {PERFORMANCE_TRENDS.map((trend) => {
        const { Icon, text } = TREND_STYLES[trend];
        return (
          <span key={trend} className={cn("inline-flex items-center gap-1 font-medium", text)}>
            <Icon className="h-3.5 w-3.5" aria-hidden />
            {counts[trend]} {PERFORMANCE_TREND_LABELS[trend].toLowerCase()}
            {counts[trend] > 1 ? "s" : ""}
          </span>
        );
      })}
      {unset > 0 && <span className="text-muted-foreground">{unset} sans tendance</span>}
    </div>
  );
}
