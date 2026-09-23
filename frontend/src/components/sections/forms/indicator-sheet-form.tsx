"use client";

import { Fragment } from "react";

import { Table, TableBody, TableCell, TableHead, TableHeader, TableRow } from "@/components/ui/table";
import { EditableCell } from "@/components/data-table/editable-cell";
import { RemoveRowButton, TableAddRow } from "@/components/data-table/row-actions";
import { AXIS_CODES } from "@/types/sections";
import type { IndicatorRow, IndicatorSheetContent } from "@/types/sections";
import type { SectionFormProps } from "./types";

function emptyRow(axisCode: string): IndicatorRow {
  return {
    axisCode,
    indicatorTitle: "",
    calculationMethod: "",
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
        <TableCell><EditableCell value={row.calculationMethod} onChange={(v) => updateRow(index, { calculationMethod: v })} readOnly={readOnly} multiline /></TableCell>
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
            <TableHead className="min-w-[180px]">Modes de calcul</TableHead>
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
