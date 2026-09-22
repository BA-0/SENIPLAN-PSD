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
 * Le tableau SWOT du canevas : l'analyse interne (forces, faiblesses) puis l'analyse externe
 * (opportunites, menaces), deux colonnes par bloc. Sert a la saisie (S04) comme au rappel en
 * lecture seule (S05, S07).
 */
export function SwotTable({ swot, onChange, readOnly }: SwotTableProps) {
  const editable = !readOnly && !!onChange;

  const head = (q: Quadrant) => (
    <TableHead key={q.key} className="w-1/2">
      <span className={cn("mr-2 inline-block h-2 w-2 rounded-full align-middle", q.dot)} aria-hidden />
      {q.label}
    </TableHead>
  );
  const cell = (q: Quadrant) => (
    <TableCell key={q.key} className="w-1/2 py-3 align-top">
      <TagListEditor
        items={swot?.[q.key] ?? []}
        onChange={(items) => onChange?.(q.key, items)}
        readOnly={!editable}
        placeholder={`Ajouter ${q.label.toLowerCase()}…`}
      />
    </TableCell>
  );

  return (
    <Table>
      <TableHeader>
        <TableRow band>
          <TableCell colSpan={2}>Analyse interne</TableCell>
        </TableRow>
        <TableRow>{INTERNAL.map(head)}</TableRow>
      </TableHeader>
      <TableBody>
        <TableRow className="hover:bg-transparent">{INTERNAL.map(cell)}</TableRow>
        <TableRow band>
          <TableCell colSpan={2}>Analyse externe</TableCell>
        </TableRow>
        <TableRow className="bg-muted/60 hover:bg-muted/60">{EXTERNAL.map(head)}</TableRow>
        <TableRow className="hover:bg-transparent">{EXTERNAL.map(cell)}</TableRow>
      </TableBody>
    </Table>
  );
}
