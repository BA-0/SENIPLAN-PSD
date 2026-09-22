"use client";

import { Table, TableBody, TableCell, TableEmptyRow, TableHead, TableHeader, TableRow } from "@/components/ui/table";
import { NoteTable } from "@/components/data-table/note-table";
import { TagListEditor } from "@/components/data-table/tag-list-editor";
import { RESOURCE_LABELS } from "@/types/sections";
import type { ResourcesSynthesisContent } from "@/types/sections";
import type { SectionFormProps } from "./types";

/**
 * S03B — synthese de l'analyse des ressources, entre la matrice S02 et le SWOT : la synthese
 * generale, puis les forces majeures, faiblesses majeures et defis prioritaires cote a cote.
 * Les lignes de la matrice sont rappelees en lecture seule (synchronisees depuis S02 par
 * DerivedFieldsService).
 */
export function ResourcesSynthesisForm({ content, onChange, readOnly }: SectionFormProps<ResourcesSynthesisContent>) {
  const resources = content.resources ?? [];

  return (
    <div className="space-y-5">
      <NoteTable
        title="Synthèse générale"
        value={content.synthesisNote ?? ""}
        onChange={(v) => onChange((prev) => ({ ...prev, synthesisNote: v }))}
        readOnly={readOnly}
        rows={5}
        placeholder="Ce que l'analyse des ressources et compétences révèle, en quelques lignes…"
      />

      <Table>
        <TableHeader>
          <TableRow>
            <TableHead className="w-1/3 min-w-[220px]">Forces majeures</TableHead>
            <TableHead className="w-1/3 min-w-[220px]">Faiblesses majeures</TableHead>
            <TableHead className="w-1/3 min-w-[220px]">Défis prioritaires</TableHead>
          </TableRow>
        </TableHeader>
        <TableBody>
          <TableRow className="hover:bg-transparent">
            <TableCell className="py-3 align-top">
              <TagListEditor
                items={content.majorStrengths ?? []}
                onChange={(items) => onChange((prev) => ({ ...prev, majorStrengths: items }))}
                readOnly={readOnly}
                placeholder="Une force majeure…"
              />
            </TableCell>
            <TableCell className="py-3 align-top">
              <TagListEditor
                items={content.majorWeaknesses ?? []}
                onChange={(items) => onChange((prev) => ({ ...prev, majorWeaknesses: items }))}
                readOnly={readOnly}
                placeholder="Une faiblesse majeure…"
              />
            </TableCell>
            <TableCell className="py-3 align-top">
              <TagListEditor
                items={content.priorityChallenges ?? []}
                onChange={(items) => onChange((prev) => ({ ...prev, priorityChallenges: items }))}
                readOnly={readOnly}
                placeholder="Un défi prioritaire…"
              />
            </TableCell>
          </TableRow>
        </TableBody>
      </Table>

      <section className="space-y-2">
        <h3>
          Rappel de la matrice des ressources et compétences
          <span className="ml-2 text-[12px] font-normal text-muted-foreground">lecture seule</span>
        </h3>
        <Table>
          <TableHeader>
            <TableRow>
              <TableHead className="min-w-[220px]">Ressource</TableHead>
              <TableHead className="min-w-[180px]">Forces / Acquis</TableHead>
              <TableHead className="min-w-[180px]">Faiblesses</TableHead>
              <TableHead className="min-w-[180px]">Défis à relever</TableHead>
            </TableRow>
          </TableHeader>
          <TableBody>
            {resources.map((row, index) => (
              <TableRow key={index}>
                <TableCell label>{RESOURCE_LABELS[row.resourceKey] ?? row.resourceKey}</TableCell>
                <TableCell className="whitespace-pre-wrap align-top text-[13px] text-foreground/90">{row.strengths || "—"}</TableCell>
                <TableCell className="whitespace-pre-wrap align-top text-[13px] text-foreground/90">{row.weaknesses || "—"}</TableCell>
                <TableCell className="whitespace-pre-wrap align-top text-[13px] text-foreground/90">{row.challenges || "—"}</TableCell>
              </TableRow>
            ))}
            {resources.length === 0 && (
              <TableEmptyRow colSpan={4}>La matrice des ressources et compétences n&apos;est pas encore renseignée.</TableEmptyRow>
            )}
          </TableBody>
        </Table>
      </section>
    </div>
  );
}
