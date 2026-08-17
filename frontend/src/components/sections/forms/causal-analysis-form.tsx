"use client";

import { Card, CardContent, CardHeader, CardTitle } from "@/components/ui/card";
import { TagListEditor } from "@/components/data-table/tag-list-editor";
import { CAUSAL_LABELS } from "@/types/sections";
import type { CausalAnalysisContent } from "@/types/sections";
import type { SectionFormProps } from "./types";

export function CausalAnalysisForm({ content, onChange, readOnly }: SectionFormProps<CausalAnalysisContent>) {
  function updateItems(index: number, items: string[]) {
    onChange((prev) => ({
      rows: prev.rows.map((r, i) => (i === index ? { ...r, items } : r)),
    }));
  }

  return (
    <div className="space-y-3">
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
