"use client";

import { cn } from "@/lib/utils";
import { Table, TableBody, TableCell, TableHead, TableHeader, TableRow } from "@/components/ui/table";
import { TagListEditor } from "@/components/data-table/tag-list-editor";
import type { SwotContent } from "@/types/sections";

type SwotKey = keyof SwotContent;
/** `key` recoit la saisie ; `legacy` garde les elements d'anciennes saisies ventilees en interne/externe. */
type Category = { label: string; dot: string; key: SwotKey; legacy: SwotKey };

const INTERNAL: Category[] = [
  { label: "Forces", dot: "bg-primary-500", key: "strengths", legacy: "strengthsExternal" },
  { label: "Faiblesses", dot: "bg-accent-500", key: "weaknesses", legacy: "weaknessesExternal" },
];
const EXTERNAL: Category[] = [
  { label: "Opportunités", dot: "bg-blue-500", key: "opportunities", legacy: "opportunitiesInternal" },
  { label: "Menaces", dot: "bg-amber-500", key: "threats", legacy: "threatsInternal" },
];

interface SwotTableProps {
  swot: Partial<SwotContent> | null | undefined;
  onChange?: (key: SwotKey, items: string[]) => void;
  readOnly?: boolean;
}

/**
 * Le tableau SWOT (FFOM) du canevas : l'environnement INTERNE coiffe l'intitule « Forces | Faiblesses »
 * puis leurs elements, l'environnement EXTERNE l'intitule « Opportunités | Menaces » puis leurs elements. Sert a la saisie
 * (S04) comme au rappel en lecture seule (S05, S07).
 */
export function SwotTable({ swot, onChange, readOnly }: SwotTableProps) {
  const editable = !readOnly && !!onChange;

  const label = (c: Category) => (
    <>
      <span className={cn("mr-2 inline-block h-2 w-2 rounded-full align-middle", c.dot)} aria-hidden />
      {c.label}
    </>
  );
  const items = (c: Category) => [...new Set([...(swot?.[c.key] ?? []), ...(swot?.[c.legacy] ?? [])])];
  const change = (c: Category, next: string[]) => {
    onChange?.(c.key, next);
    if (swot?.[c.legacy]?.length) onChange?.(c.legacy, []);
  };
  const cell = (c: Category) => (
    <TableCell key={c.key} className="w-[44%] py-3 align-top">
      <TagListEditor
        items={items(c)}
        onChange={(next) => change(c, next)}
        readOnly={!editable}
        placeholder={`Ajouter ${c.label.toLowerCase()}…`}
      />
    </TableCell>
  );
  const environment = "w-[12%] bg-background text-center align-middle text-[12px] font-semibold uppercase tracking-wide";
  const block = (name: string, categories: Category[]) => (
    <>
      <TableRow className="bg-muted/60 hover:bg-muted/60">
        <TableCell label rowSpan={2} className={environment}>
          {name}
        </TableCell>
        {categories.map((c) => (
          <TableCell key={c.label} className="h-11 text-center text-[12px] font-semibold uppercase tracking-wide text-muted-foreground">
            {label(c)}
          </TableCell>
        ))}
      </TableRow>
      <TableRow className="hover:bg-transparent">{categories.map(cell)}</TableRow>
    </>
  );

  return (
    <Table>
      <TableHeader>
        <TableRow>
          <TableHead className="text-center">Environnement</TableHead>
          <TableHead aria-hidden />
          <TableHead aria-hidden />
        </TableRow>
      </TableHeader>
      <TableBody>
        {block("Interne", INTERNAL)}
        {block("Externe", EXTERNAL)}
      </TableBody>
    </Table>
  );
}
