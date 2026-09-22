"use client";

import type { SwotContent } from "@/types/sections";
import type { SectionFormProps } from "./types";
import { SwotTable } from "./swot-table";

export function SwotForm({ content, onChange, readOnly }: SectionFormProps<SwotContent>) {
  return <SwotTable swot={content} readOnly={readOnly} onChange={(key, items) => onChange((prev) => ({ ...prev, [key]: items }))} />;
}
