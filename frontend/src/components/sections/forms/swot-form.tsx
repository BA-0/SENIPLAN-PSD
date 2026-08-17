"use client";

import { cn } from "@/lib/utils";
import { TagListEditor } from "@/components/data-table/tag-list-editor";
import type { SwotContent } from "@/types/sections";
import type { SectionFormProps } from "./types";

const QUADRANTS: {
  key: keyof SwotContent;
  label: string;
  scope: string;
  accent: string;
  bg: string;
}[] = [
  { key: "strengths", label: "Forces", scope: "Interne", accent: "border-primary-300", bg: "bg-primary-50" },
  { key: "weaknesses", label: "Faiblesses", scope: "Interne", accent: "border-accent-200", bg: "bg-accent-50" },
  { key: "opportunities", label: "Opportunités", scope: "Externe", accent: "border-blue-200", bg: "bg-blue-50" },
  { key: "threats", label: "Menaces", scope: "Externe", accent: "border-amber-200", bg: "bg-amber-50" },
];

export function SwotForm({ content, onChange, readOnly }: SectionFormProps<SwotContent>) {
  return (
    <div className="grid grid-cols-1 md:grid-cols-2 gap-4">
      {QUADRANTS.map((q) => (
        <div key={q.key} className={cn("rounded-xl border-2 p-4", q.accent, q.bg)}>
          <div className="flex items-center justify-between mb-3">
            <h3 className="text-slate-800">{q.label}</h3>
            <span className="text-[11px] uppercase tracking-wide text-slate-500 font-semibold">{q.scope}</span>
          </div>
          <TagListEditor
            items={content[q.key] as string[]}
            onChange={(items) => onChange((prev) => ({ ...prev, [q.key]: items }))}
            readOnly={readOnly}
            placeholder={`Ajouter ${q.label.toLowerCase()}…`}
          />
        </div>
      ))}
    </div>
  );
}
