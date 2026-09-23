"use client";

import { Fragment } from "react";
import { Plus } from "lucide-react";
import { Table, TableBody, TableCell, TableEmptyRow, TableHead, TableHeader, TableRow } from "@/components/ui/table";
import { Button } from "@/components/ui/button";
import { EditableCell } from "@/components/data-table/editable-cell";
import { RemoveRowButton, TableAddRow } from "@/components/data-table/row-actions";
import { directionAxisLabel } from "@/lib/utils";
import type { StrategicSummaryContent, SummaryAction, SummaryOrientation } from "@/types/sections";
import type { SectionFormProps } from "./types";

/**
 * S17 — tableau de synthese du cadre strategique, sur le modele transmis par le client : la vision
 * en tete, puis pour chaque axe son bandeau et ses orientations strategiques (OS), chacune fusionnee
 * sur les lignes de ses actions, avec les contraintes a lever ou opportunites a saisir.
 */
export function StrategicSummaryForm({ content, onChange, readOnly }: SectionFormProps<StrategicSummaryContent>) {
  function updateAxis(axisIndex: number, updater: (orientations: SummaryOrientation[]) => SummaryOrientation[]) {
    onChange((prev) => ({
      ...prev,
      axes: prev.axes.map((a, i) => (i === axisIndex ? { ...a, orientations: updater(a.orientations) } : a)),
    }));
  }
  function addOrientation(axisIndex: number) {
    updateAxis(axisIndex, (orientations) => [...orientations, { label: "", actions: [] }]);
  }
  function updateOrientation(axisIndex: number, orientationIndex: number, patch: Partial<SummaryOrientation>) {
    updateAxis(axisIndex, (orientations) => orientations.map((o, i) => (i === orientationIndex ? { ...o, ...patch } : o)));
  }
  function removeOrientation(axisIndex: number, orientationIndex: number) {
    updateAxis(axisIndex, (orientations) => orientations.filter((_, i) => i !== orientationIndex));
  }
  function updateActions(axisIndex: number, orientationIndex: number, updater: (actions: SummaryAction[]) => SummaryAction[]) {
    updateAxis(axisIndex, (orientations) =>
      orientations.map((o, i) => (i === orientationIndex ? { ...o, actions: updater(o.actions) } : o))
    );
  }
  function addAction(axisIndex: number, orientationIndex: number) {
    updateActions(axisIndex, orientationIndex, (actions) => [...actions, { label: "", constraintsOrOpportunities: "" }]);
  }
  function updateAction(axisIndex: number, orientationIndex: number, actionIndex: number, patch: Partial<SummaryAction>) {
    updateActions(axisIndex, orientationIndex, (actions) => actions.map((a, i) => (i === actionIndex ? { ...a, ...patch } : a)));
  }
  function removeAction(axisIndex: number, orientationIndex: number, actionIndex: number) {
    updateActions(axisIndex, orientationIndex, (actions) => actions.filter((_, i) => i !== actionIndex));
  }

  const colCount = readOnly ? 3 : 4;

  return (
    <Table>
      <TableHeader>
        <TableRow>
          <TableHead className="min-w-[260px] whitespace-normal">Orientation stratégique (OS)</TableHead>
          <TableHead className="min-w-[260px]">Actions</TableHead>
          <TableHead className="min-w-[260px] whitespace-normal">Contraintes à lever ou opportunités à saisir</TableHead>
          {!readOnly && <TableHead className="w-10" />}
        </TableRow>
      </TableHeader>
      <TableBody>
        <TableRow className="hover:bg-transparent">
          <TableCell label>
            Vision
            <span className="ml-0.5 text-accent-500" aria-hidden>
              *
            </span>
          </TableCell>
          <TableCell colSpan={colCount - 1} className="py-3">
            <EditableCell
              value={content.vision}
              onChange={(v) => onChange((prev) => ({ ...prev, vision: v }))}
              readOnly={readOnly}
              placeholder="Vision globale du Plan Stratégique 2027-2031…"
              multiline
            />
          </TableCell>
        </TableRow>

        {content.axes.map((axis, axisIndex) => (
          <Fragment key={axis.axisCode}>
            <TableRow band>
              <TableCell colSpan={colCount}>{directionAxisLabel(axis.axisCode, axis.axisTitle)}</TableCell>
            </TableRow>

            {axis.orientations.map((orientation, orientationIndex) => {
              const osCell = (
                <TableCell rowSpan={Math.max(orientation.actions.length, 1)} label>
                  <div className="space-y-2">
                    <div className="flex items-center justify-between gap-2">
                      <span className="text-[12px] font-semibold uppercase tracking-wide text-primary-700 dark:text-primary-300">
                        OS{orientationIndex + 1}
                      </span>
                      {!readOnly && <RemoveRowButton onConfirm={() => removeOrientation(axisIndex, orientationIndex)} />}
                    </div>
                    <EditableCell
                      value={orientation.label}
                      onChange={(v) => updateOrientation(axisIndex, orientationIndex, { label: v })}
                      readOnly={readOnly}
                      placeholder="Intitulé de l'orientation stratégique…"
                      multiline
                    />
                    {!readOnly && (
                      <Button
                        type="button"
                        variant="link"
                        size="sm"
                        onClick={() => addAction(axisIndex, orientationIndex)}
                        className="gap-1"
                      >
                        <Plus className="h-3.5 w-3.5" /> Ajouter une action
                      </Button>
                    )}
                  </div>
                </TableCell>
              );

              if (orientation.actions.length === 0) {
                return (
                  <TableRow key={orientationIndex} className="hover:bg-transparent">
                    {osCell}
                    <TableCell colSpan={colCount - 1} className="text-[13px] italic text-muted-foreground">
                      Aucune action pour cette orientation
                    </TableCell>
                  </TableRow>
                );
              }

              return orientation.actions.map((action, actionIndex) => (
                <TableRow key={`${orientationIndex}-${actionIndex}`} className="hover:bg-transparent">
                  {actionIndex === 0 && osCell}
                  <TableCell className="align-top">
                    <div className="flex items-start gap-2">
                      <span className="mt-2 shrink-0 text-[12px] font-medium tabular-nums text-muted-foreground">
                        Action {orientationIndex + 1}.{actionIndex + 1} :
                      </span>
                      <EditableCell
                        value={action.label}
                        onChange={(v) => updateAction(axisIndex, orientationIndex, actionIndex, { label: v })}
                        readOnly={readOnly}
                        placeholder="Intitulé de l'action…"
                        multiline
                      />
                    </div>
                  </TableCell>
                  <TableCell className="align-top">
                    <EditableCell
                      value={action.constraintsOrOpportunities}
                      onChange={(v) => updateAction(axisIndex, orientationIndex, actionIndex, { constraintsOrOpportunities: v })}
                      readOnly={readOnly}
                      placeholder="Contraintes à lever ou opportunités à saisir…"
                      multiline
                    />
                  </TableCell>
                  {!readOnly && (
                    <TableCell className="align-top">
                      <RemoveRowButton onConfirm={() => removeAction(axisIndex, orientationIndex, actionIndex)} />
                    </TableCell>
                  )}
                </TableRow>
              ));
            })}

            {axis.orientations.length === 0 && (
              <TableEmptyRow colSpan={colCount}>Aucune orientation stratégique pour cet axe</TableEmptyRow>
            )}
            {!readOnly && (
              <TableAddRow
                colSpan={colCount}
                onAdd={() => addOrientation(axisIndex)}
                label="Ajouter une orientation stratégique"
                emphasis
              />
            )}
          </Fragment>
        ))}
      </TableBody>
    </Table>
  );
}
