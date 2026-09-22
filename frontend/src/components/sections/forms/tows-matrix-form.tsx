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
          swot={{
            strengths: content.strengths ?? [],
            weaknesses: content.weaknesses ?? [],
            opportunities: content.opportunities ?? [],
            threats: content.threats ?? [],
          }}
          readOnly
        />
      </section>

      <section className="space-y-2">
        <h3>Matrice de confrontation</h3>
        <Table>
          <TableHeader>
            <TableRow>
              <TableHead className="min-w-[160px] whitespace-normal">Approche externe</TableHead>
              <TableHead className="min-w-[220px] whitespace-normal">
                Comment maximiser les opportunités / minimiser les menaces ?
              </TableHead>
              <TableHead className="min-w-[220px] whitespace-normal">Forces : comment les maximiser et s&apos;en servir ?</TableHead>
              <TableHead className="min-w-[220px] whitespace-normal">Faiblesses : comment les minimiser et les corriger ?</TableHead>
              <TableHead className="min-w-[220px] whitespace-normal">En quoi les forces permettent de maîtriser les faiblesses</TableHead>
            </TableRow>
          </TableHeader>
          <TableBody>
            <TableRow className="hover:bg-transparent">
              <TableCell label>Approche interne</TableCell>
              {none}
              {answer("maximizeStrengths")}
              {answer("minimizeWeaknesses")}
              {answer("strengthsControlWeaknesses")}
            </TableRow>
            <TableRow className="hover:bg-transparent">
              <TableCell label>Opportunités</TableCell>
              {answer("maximizeOpportunities")}
              {answer("strengthsForOpportunities")}
              {answer("correctWeaknessesViaOpportunities")}
              {none}
            </TableRow>
            <TableRow className="hover:bg-transparent">
              <TableCell label>Menaces</TableCell>
              {answer("minimizeThreats")}
              {answer("strengthsReduceThreats")}
              {answer("minimizeWeaknessesAndThreats")}
              {none}
            </TableRow>
            <TableRow className="hover:bg-transparent">
              <TableCell label>En quoi les opportunités permettent de minimiser les menaces</TableCell>
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
