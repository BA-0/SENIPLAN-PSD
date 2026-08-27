"use client";

import { Card, CardContent, CardHeader, CardTitle } from "@/components/ui/card";
import { TagListEditor } from "@/components/data-table/tag-list-editor";
import { CAUSAL_LABELS, TOWS_ACTION_LABELS } from "@/types/sections";
import type { CausalAnalysisContent, TowsActions } from "@/types/sections";
import type { SectionFormProps } from "./types";

export function CausalAnalysisForm({ content, onChange, readOnly }: SectionFormProps<CausalAnalysisContent>) {
  function updateItems(index: number, items: string[]) {
    onChange((prev) => ({
      ...prev,
      rows: prev.rows.map((r, i) => (i === index ? { ...r, items } : r)),
    }));
  }

  const towsActions = content.syncedTowsActions;
  const towsEntries = towsActions
    ? (Object.keys(TOWS_ACTION_LABELS) as (keyof TowsActions)[])
        .map((key) => ({ label: TOWS_ACTION_LABELS[key], value: towsActions[key] }))
        .filter((e) => e.value)
    : [];

  return (
    <div className="space-y-3">
      {towsEntries.length > 0 && (
        <Card>
          <CardHeader>
            <CardTitle className="text-[15px]">Actions (SWOT / matrice de confrontation — Section 5)</CardTitle>
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
          <CardHeader>
            <CardTitle className="text-[15px]">{CAUSAL_LABELS[row.source] ?? row.source}</CardTitle>
          </CardHeader>
          <CardContent>
            <TagListEditor items={row.items} onChange={(items) => updateItems(index, items)} readOnly={readOnly} />
          </CardContent>
        </Card>
      ))}
    </div>
  );
}
