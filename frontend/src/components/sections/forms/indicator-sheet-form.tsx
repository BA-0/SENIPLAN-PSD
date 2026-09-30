"use client";

import { Fragment } from "react";

import { Table, TableBody, TableCell, TableHead, TableHeader, TableRow } from "@/components/ui/table";
import { EditableCell } from "@/components/data-table/editable-cell";
import { RemoveRowButton, TableAddRow } from "@/components/data-table/row-actions";
import { NativeSelect } from "@/components/ui/native-select";
import { CALCULATION_TYPES, calculationTypeOption, computeIndicator } from "@/lib/indicator-calculation";
import { AXIS_CODES } from "@/types/sections";
import type { IndicatorCalculationType, IndicatorRow, IndicatorSheetContent } from "@/types/sections";
import type { SectionFormProps } from "./types";

function emptyRow(axisCode: string): IndicatorRow {
  return {
    axisCode,
    indicatorTitle: "",
    calculationMethod: "",
    calculationType: "",
    valueA: "",
    valueB: "",
    calculationValues: "",
    periodicity: "",
    collectionSource: "",
    verificationSource: "",
    responsibleStructure: "",
  };
}

/** « AXE 1 : intitulé », comme les bandeaux de la fiche des indicateurs du modèle client. */
function axisBand(axisCode: string, title: string | undefined): string {
  const number = axisCode.match(/\d+/)?.[0] ?? "";
  const trimmed = title?.trim();
  return trimmed ? `AXE ${number} : ${trimmed}` : `AXE ${number}`;
}

/**
 * S13 — fiche des indicateurs quantitatifs et qualitatifs objectivement vérifiables, aux six colonnes du
 * canevas. Comme dans le modèle client, les indicateurs sont rangés sous un bandeau par axe (intitulés
 * repris des axes stratégiques de la direction). Un indicateur saisi avant ce rangement, sans axe, reste
 * visible sous « Sans axe » jusqu'à ce qu'on le range.
 */
export function IndicatorSheetForm({ content, onChange, readOnly }: SectionFormProps<IndicatorSheetContent>) {
  const rows = content.rows ?? [];
  const indexed = rows.map((row, index) => ({ row, index }));
  const unassigned = indexed.filter(({ row }) => !AXIS_CODES.includes((row.axisCode ?? "") as (typeof AXIS_CODES)[number]));
  const columnCount = 6 + (readOnly ? 0 : 1);

  function updateRow(index: number, patch: Partial<IndicatorRow>) {
    onChange((prev) => ({ ...prev, rows: prev.rows.map((r, i) => (i === index ? { ...r, ...patch } : r)) }));
  }
  function addRow(axisCode: string) {
    onChange((prev) => ({ ...prev, rows: [...prev.rows, emptyRow(axisCode)] }));
  }
  function removeRow(index: number) {
    onChange((prev) => ({ ...prev, rows: prev.rows.filter((_, i) => i !== index) }));
  }

  function renderRow({ row, index }: { row: IndicatorRow; index: number }) {
    return (
      <TableRow key={index}>
        <TableCell><EditableCell value={row.indicatorTitle} onChange={(v) => updateRow(index, { indicatorTitle: v })} readOnly={readOnly} multiline /></TableCell>
        <TableCell className="align-top">
          <CalculationCell row={row} onChange={(patch) => updateRow(index, patch)} readOnly={readOnly} />
        </TableCell>
        <TableCell><EditableCell value={row.periodicity} onChange={(v) => updateRow(index, { periodicity: v })} readOnly={readOnly} /></TableCell>
        <TableCell><EditableCell value={row.collectionSource} onChange={(v) => updateRow(index, { collectionSource: v })} readOnly={readOnly} multiline /></TableCell>
        <TableCell><EditableCell value={row.verificationSource} onChange={(v) => updateRow(index, { verificationSource: v })} readOnly={readOnly} multiline /></TableCell>
        <TableCell><EditableCell value={row.responsibleStructure} onChange={(v) => updateRow(index, { responsibleStructure: v })} readOnly={readOnly} /></TableCell>
        {!readOnly && (
          <TableCell>
            <RemoveRowButton onConfirm={() => removeRow(index)} />
          </TableCell>
        )}
      </TableRow>
    );
  }

  return (
    <div className="space-y-3">
      <Table>
        <TableHeader>
          <TableRow>
            <TableHead className="min-w-[180px] whitespace-normal">Intitulés indicateurs</TableHead>
            <TableHead className="min-w-[260px]">Modes de calcul</TableHead>
            <TableHead className="min-w-[120px]">Périodicités</TableHead>
            <TableHead className="min-w-[180px] whitespace-normal">Sources et moyens de collecte</TableHead>
            <TableHead className="min-w-[180px] whitespace-normal">Sources de vérification</TableHead>
            <TableHead className="min-w-[160px] whitespace-normal">Structures responsables</TableHead>
            {!readOnly && <TableHead className="w-10" />}
          </TableRow>
        </TableHeader>
        <TableBody>
          {AXIS_CODES.map((axisCode) => {
            const axisRows = indexed.filter(({ row }) => row.axisCode === axisCode);
            return (
              <Fragment key={axisCode}>
                <TableRow band>
                  <TableCell colSpan={columnCount}>{axisBand(axisCode, content.axisTitles?.[axisCode])}</TableCell>
                </TableRow>
                {axisRows.map(renderRow)}
                {readOnly && axisRows.length === 0 && (
                  <TableRow className="hover:bg-transparent">
                    <TableCell colSpan={columnCount} className="py-3 text-center text-[13px] italic text-muted-foreground">
                      Aucun indicateur pour cet axe
                    </TableCell>
                  </TableRow>
                )}
                {!readOnly && <TableAddRow colSpan={columnCount} onAdd={() => addRow(axisCode)} label="Ajouter un indicateur" />}
              </Fragment>
            );
          })}
          {unassigned.length > 0 && (
            <>
              <TableRow band>
                <TableCell colSpan={columnCount}>Sans axe</TableCell>
              </TableRow>
              {unassigned.map(renderRow)}
            </>
          )}
        </TableBody>
      </Table>
    </div>
  );
}

/**
 * « Modes de calcul » : la formule en toutes lettres, comme dans le canevas, puis le calcul automatique
 * posé sur les valeurs saisies. Le résultat s'affiche pendant la saisie ; le serveur le recalcule à la
 * lecture et pour l'export.
 */
function CalculationCell({
  row,
  onChange,
  readOnly,
}: {
  row: IndicatorRow;
  onChange: (patch: Partial<IndicatorRow>) => void;
  readOnly?: boolean;
}) {
  const option = calculationTypeOption(row.calculationType);
  const outcome = computeIndicator(row);
  const isList = option.value === "SUM" || option.value === "AVERAGE";

  return (
    <div className="space-y-2">
      <EditableCell
        value={row.calculationMethod}
        onChange={(v) => onChange({ calculationMethod: v })}
        readOnly={readOnly}
        placeholder="Formule en toutes lettres"
        multiline
      />
      {(!readOnly || option.value) && (
        <NativeSelect
          cellStyle
          value={option.value}
          disabled={readOnly}
          onChange={(e) => onChange({ calculationType: e.target.value as IndicatorCalculationType })}
        >
          {CALCULATION_TYPES.map((t) => (
            <option key={t.value} value={t.value}>
              {t.label}
            </option>
          ))}
        </NativeSelect>
      )}
      {!readOnly && option.value && !isList && (
        <div className="grid grid-cols-2 gap-2">
          <label className="space-y-1 text-[12px] text-muted-foreground">
            <span>{option.a}</span>
            <EditableCell value={row.valueA ?? ""} onChange={(v) => onChange({ valueA: v })} align="right" className="min-w-0" />
          </label>
          <label className="space-y-1 text-[12px] text-muted-foreground">
            <span>{option.b}</span>
            <EditableCell value={row.valueB ?? ""} onChange={(v) => onChange({ valueB: v })} align="right" className="min-w-0" />
          </label>
        </div>
      )}
      {!readOnly && isList && (
        <label className="block space-y-1 text-[12px] text-muted-foreground">
          <span>Valeurs, séparées par « ; »</span>
          <EditableCell
            value={row.calculationValues ?? ""}
            onChange={(v) => onChange({ calculationValues: v })}
            placeholder="12 ; 15 ; 18"
          />
        </label>
      )}
      {outcome.status === "ok" && (
        <div className="rounded-md bg-primary-50 px-2.5 py-1.5 text-[13px] dark:bg-primary-500/10">
          <div className="font-semibold tabular-nums text-foreground">Résultat : {outcome.result}</div>
          <div className="tabular-nums text-muted-foreground">{outcome.detail}</div>
        </div>
      )}
      {outcome.status === "error" && !readOnly && <p className="text-[12px] text-red-500 dark:text-red-400">{outcome.message}</p>}
      {outcome.status === "incomplete" && !readOnly && (
        <p className="text-[12px] italic text-muted-foreground">Saisissez les valeurs pour obtenir le résultat</p>
      )}
    </div>
  );
}
