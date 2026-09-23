"use client";

import { Table, TableBody, TableCell, TableHead, TableHeader, TableRow } from "@/components/ui/table";
import { EditableCell } from "@/components/data-table/editable-cell";
import { TOWS_ACTION_LABELS } from "@/types/sections";
import type { TowsActions, TowsMatrixContent } from "@/types/sections";
import type { SectionFormProps } from "./types";
import { SwotTable } from "./swot-table";

/**
 * S05 — mise en relation du diagnostic, sur le modele du canevas : les facteurs internes en
 * colonnes (forces, faiblesses), les facteurs externes en lignes (opportunites, menaces) et, a
 * chaque croisement, la strategie qu'en tire la direction. Les cases grises ne se remplissent pas.
 * Le SWOT est rappele au-dessus, en lecture seule (il se saisit dans sa propre section).
 */
export function TowsMatrixForm({ content, onChange, readOnly }: SectionFormProps<TowsMatrixContent>) {
  function updateField(key: keyof TowsActions, value: string) {
    onChange((prev) => ({ ...prev, [key]: value }));
  }

  const answer = (key: keyof TowsActions) => (
    <TableCell className="min-w-[220px] align-top">
      <EditableCell
        value={content[key] ?? ""}
        onChange={(v) => updateField(key, v)}
        readOnly={readOnly}
        placeholder={TOWS_ACTION_LABELS[key]}
        multiline
        rows={3}
      />
    </TableCell>
  );
  const none = <TableCell className="bg-muted/40" aria-hidden />;

  return (
    <div className="space-y-5">
      <section className="space-y-2">
        <h3>
          Rappel de l&apos;analyse SWOT
          <span className="ml-2 text-[12px] font-normal text-muted-foreground">lecture seule — se saisit dans la section SWOT</span>
        </h3>
        <SwotTable
          swot={content}
          readOnly
        />
      </section>

      <section className="space-y-2">
        <h3>Mise en relation du diagnostic stratégique</h3>
        {/* Grille du canevas : l'approche interne coiffe les colonnes, l'approche externe les lignes. */}
        <Table>
          <TableHeader>
            <TableRow>
              <TableHead colSpan={3} rowSpan={2} className="bg-muted" aria-hidden />
              <TableHead colSpan={3} className="text-center">
                Approche interne
              </TableHead>
            </TableRow>
            <TableRow>
              <TableHead className="min-w-[220px] whitespace-normal">Liste des forces</TableHead>
              <TableHead className="min-w-[220px] whitespace-normal">Liste des faiblesses</TableHead>
              <TableHead className="min-w-[220px] whitespace-normal">
                {TOWS_ACTION_LABELS.strengthsControlWeaknesses}
              </TableHead>
            </TableRow>
          </TableHeader>
          <TableBody>
            <TableRow className="hover:bg-transparent">
              <TableCell colSpan={3} className="bg-muted/40" aria-hidden />
              {answer("maximizeStrengths")}
              {answer("minimizeWeaknesses")}
              {answer("strengthsControlWeaknesses")}
            </TableRow>
            <TableRow className="hover:bg-transparent">
              <TableCell
                label
                rowSpan={3}
                className="w-10 text-center align-middle [writing-mode:vertical-rl] rotate-180 text-[12px] font-semibold uppercase tracking-wide"
              >
                Approche externe
              </TableCell>
              <TableCell label>Liste des opportunités</TableCell>
              {answer("maximizeOpportunities")}
              {answer("strengthsForOpportunities")}
              {answer("correctWeaknessesViaOpportunities")}
              {none}
            </TableRow>
            <TableRow className="hover:bg-transparent">
              <TableCell label>Liste des menaces</TableCell>
              {answer("minimizeThreats")}
              {answer("strengthsReduceThreats")}
              {answer("minimizeWeaknessesAndThreats")}
              {none}
            </TableRow>
            <TableRow className="hover:bg-transparent">
              <TableCell label>{TOWS_ACTION_LABELS.opportunitiesMinimizeThreats}</TableCell>
              {answer("opportunitiesMinimizeThreats")}
              {none}
              {none}
              {none}
            </TableRow>
          </TableBody>
        </Table>
      </section>
    </div>
  );
}
