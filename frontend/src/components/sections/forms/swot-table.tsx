"use client";

import { cn } from "@/lib/utils";
import { Table, TableBody, TableCell, TableHead, TableHeader, TableRow } from "@/components/ui/table";
import { TagListEditor } from "@/components/data-table/tag-list-editor";
import type { SwotContent } from "@/types/sections";

type Quadrant = { key: keyof SwotContent; label: string; dot: string };

const INTERNAL: Quadrant[] = [
  { key: "strengths", label: "Forces", dot: "bg-primary-500" },
  { key: "weaknesses", label: "Faiblesses", dot: "bg-accent-500" },
];
const EXTERNAL: Quadrant[] = [
  { key: "opportunities", label: "Opportunités", dot: "bg-blue-500" },
  { key: "threats", label: "Menaces", dot: "bg-amber-500" },
];

interface SwotTableProps {
  swot: Partial<SwotContent> | null | undefined;
  onChange?: (key: keyof SwotContent, items: string[]) => void;
  readOnly?: boolean;
}

/**
 * Le tableau SWOT (FFOM) du canevas : une colonne « Environnement » ; la ligne INTERNE porte les forces et
 * les faiblesses, la ligne EXTERNE, sous l'intitulé « Opportunités | Menaces », les opportunités et les
 * menaces. Sert a la saisie (S04) comme au rappel en lecture seule (S05, S07).
 */
export function SwotTable({ swot, onChange, readOnly }: SwotTableProps) {
  const editable = !readOnly && !!onChange;

  const label = (q: Quadrant) => (
    <>
      <span className={cn("mr-2 inline-block h-2 w-2 rounded-full align-middle", q.dot)} aria-hidden />
      {q.label}
    </>
  );
  const cell = (q: Quadrant) => (
    <TableCell key={q.key} className="w-[44%] py-3 align-top">
      <TagListEditor
        items={swot?.[q.key] ?? []}
        onChange={(items) => onChange?.(q.key, items)}
        readOnly={!editable}
        placeholder={`Ajouter ${q.label.toLowerCase()}…`}
      />
    </TableCell>
  );
  const environment = "w-[12%] text-center align-middle text-[12px] font-semibold uppercase tracking-wide";

  return (
    <Table>
      <TableHeader>
        <TableRow>
          <TableHead className="text-center">Environnement</TableHead>
          {INTERNAL.map((q) => (
            <TableHead key={q.key} className="text-center">
              {label(q)}
            </TableHead>
          ))}
        </TableRow>
      </TableHeader>
      <TableBody>
        <TableRow className="hover:bg-transparent">
          <TableCell label rowSpan={2} className={environment}>
            Interne
          </TableCell>
          {INTERNAL.map(cell)}
        </TableRow>
        <TableRow className="bg-muted/60 hover:bg-muted/60">
          {EXTERNAL.map((q) => (
            <TableCell key={q.key} className="h-11 text-center text-[12px] font-semibold uppercase tracking-wide text-muted-foreground">
              {label(q)}
            </TableCell>
          ))}
        </TableRow>
        <TableRow className="hover:bg-transparent">
          <TableCell label className={environment}>
            Externe
          </TableCell>
          {EXTERNAL.map(cell)}
        </TableRow>
      </TableBody>
    </Table>
  );
}
