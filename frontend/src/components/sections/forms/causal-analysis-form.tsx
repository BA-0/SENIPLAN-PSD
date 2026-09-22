"use client";

import { Card, CardContent, CardHeader, CardTitle } from "@/components/ui/card";
import { TagListEditor } from "@/components/data-table/tag-list-editor";
import { KeyedRowAdder, RemoveRowButton, insertInModelOrder, remainingOptions } from "@/components/data-table/row-actions";
import { CAUSAL_LABELS, TOWS_ACTION_LABELS } from "@/types/sections";
import type { CausalAnalysisContent, TowsActions } from "@/types/sections";
import type { SectionFormProps } from "./types";

const CAUSAL_SOURCES = Object.keys(CAUSAL_LABELS);

export function CausalAnalysisForm({ content, onChange, readOnly }: SectionFormProps<CausalAnalysisContent>) {
  function updateItems(index: number, items: string[]) {
    onChange((prev) => ({
      ...prev,
      rows: prev.rows.map((r, i) => (i === index ? { ...r, items } : r)),
    }));
  }
  function addRow(source: string) {
    onChange((prev) => ({
      ...prev,
      rows: insertInModelOrder(prev.rows, { source, items: [] }, (r) => r.source, CAUSAL_SOURCES),
    }));
  }
  function removeRow(index: number) {
    onChange((prev) => ({
      ...prev,
      rows: prev.rows.filter((_, i) => i !== index),
    }));
  }

  const towsActions = content.syncedTowsActions;
  const towsEntries = towsActions
    ? (Object.keys(TOWS_ACTION_LABELS) as (keyof TowsActions)[])
        .map((key) => ({
          label: TOWS_ACTION_LABELS[key],
          value: towsActions[key],
        }))
        .filter((e) => e.value)
    : [];

  return (
    <div className="space-y-3">
      {towsEntries.length > 0 && (
        <Card>
          <CardHeader>
            <CardTitle className="text-[15px]">Actions (SWOT / matrice de confrontation)</CardTitle>
          </CardHeader>
          <CardContent className="space-y-2">
            {towsEntries.map((e) => (
              <div key={e.label} className="text-[13px] rounded-md bg-muted/50 px-2.5 py-1.5">
                <span className="text-muted-foreground">{e.label} </span>
                <span className="text-foreground">{e.value}</span>
              </div>
            ))}
          </CardContent>
        </Card>
      )}
      {content.rows.map((row, index) => (
        <Card key={row.source}>
          <CardHeader className="flex flex-row items-center justify-between gap-2">
            <CardTitle className="text-[15px]">{CAUSAL_LABELS[row.source] ?? row.source}</CardTitle>
            {!readOnly && <RemoveRowButton onConfirm={() => removeRow(index)} />}
          </CardHeader>
          <CardContent>
            <TagListEditor items={row.items} onChange={(items) => updateItems(index, items)} readOnly={readOnly} />
          </CardContent>
        </Card>
      ))}
      {content.rows.length === 0 && <p className="py-8 text-center text-muted-foreground">Aucun niveau d&apos;analyse renseigné</p>}
      {!readOnly && (
        <KeyedRowAdder
          options={remainingOptions(
            CAUSAL_SOURCES,
            CAUSAL_LABELS,
            content.rows.map((r) => r.source)
          )}
          onAdd={addRow}
          label="Ajouter"
          placeholder="Choisir un niveau (manifestation, causes, solutions)…"
        />
      )}
    </div>
  );
}
