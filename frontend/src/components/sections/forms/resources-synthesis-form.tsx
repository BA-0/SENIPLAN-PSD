"use client";

import { Card, CardContent, CardHeader, CardTitle } from "@/components/ui/card";
import { Label } from "@/components/ui/label";
import { Textarea } from "@/components/ui/textarea";
import { Table, TableBody, TableCell, TableHead, TableHeader, TableRow } from "@/components/ui/table";
import { TagListEditor } from "@/components/data-table/tag-list-editor";
import { RESOURCE_LABELS } from "@/types/sections";
import type { ResourcesSynthesisContent } from "@/types/sections";
import type { SectionFormProps } from "./types";

/**
 * S03B — synthese de l'analyse des ressources, entre la matrice S02 et le SWOT.
 * Les lignes de la matrice sont rappelees en lecture seule (synchronisees depuis S02
 * par DerivedFieldsService) : la saisie porte uniquement sur la synthese elle-meme.
 */
export function ResourcesSynthesisForm({ content, onChange, readOnly }: SectionFormProps<ResourcesSynthesisContent>) {
  const resources = content.resources ?? [];

  return (
    <div className="space-y-4">
      <div className="space-y-1.5">
        <Label htmlFor="resources-synthesis-note">Synthèse générale</Label>
        <Textarea
          id="resources-synthesis-note"
          value={content.synthesisNote ?? ""}
          onChange={(e) => onChange((prev) => ({ ...prev, synthesisNote: e.target.value }))}
          readOnly={readOnly}
          rows={5}
          placeholder="Ce que l'analyse des ressources et compétences révèle, en quelques lignes…"
        />
      </div>

      <div className="grid grid-cols-1 gap-4 lg:grid-cols-3">
        <Card>
          <CardHeader>
            <CardTitle className="text-[14px]">Forces majeures</CardTitle>
          </CardHeader>
          <CardContent>
            <TagListEditor
              items={content.majorStrengths ?? []}
              onChange={(items) => onChange((prev) => ({ ...prev, majorStrengths: items }))}
              readOnly={readOnly}
              placeholder="Une force majeure…"
            />
          </CardContent>
        </Card>
        <Card>
          <CardHeader>
            <CardTitle className="text-[14px]">Faiblesses majeures</CardTitle>
          </CardHeader>
          <CardContent>
            <TagListEditor
              items={content.majorWeaknesses ?? []}
              onChange={(items) => onChange((prev) => ({ ...prev, majorWeaknesses: items }))}
              readOnly={readOnly}
              placeholder="Une faiblesse majeure…"
            />
          </CardContent>
        </Card>
        <Card>
          <CardHeader>
            <CardTitle className="text-[14px]">Défis prioritaires</CardTitle>
          </CardHeader>
          <CardContent>
            <TagListEditor
              items={content.priorityChallenges ?? []}
              onChange={(items) => onChange((prev) => ({ ...prev, priorityChallenges: items }))}
              readOnly={readOnly}
              placeholder="Un défi prioritaire…"
            />
          </CardContent>
        </Card>
      </div>

      <div className="space-y-2">
        <p className="text-[13px] font-medium text-foreground/90">
          Rappel de la matrice des ressources (S02)
          <span className="ml-2 font-normal text-[12px] text-muted-foreground">lecture seule</span>
        </p>
        <Table>
          <TableHeader>
            <TableRow>
              <TableHead className="min-w-[220px]">Domaine</TableHead>
              <TableHead className="min-w-[180px]">Forces / Acquis</TableHead>
              <TableHead className="min-w-[180px]">Faiblesses</TableHead>
              <TableHead className="min-w-[180px]">Défis à relever</TableHead>
            </TableRow>
          </TableHeader>
          <TableBody>
            {resources.map((row, index) => (
              <TableRow key={index}>
                <TableCell className="text-[13px] font-medium text-foreground/90">
                  {RESOURCE_LABELS[row.resourceKey] ?? row.resourceKey}
                </TableCell>
                <TableCell className="whitespace-pre-wrap text-[13px] text-foreground/90">{row.strengths || "—"}</TableCell>
                <TableCell className="whitespace-pre-wrap text-[13px] text-foreground/90">{row.weaknesses || "—"}</TableCell>
                <TableCell className="whitespace-pre-wrap text-[13px] text-foreground/90">{row.challenges || "—"}</TableCell>
              </TableRow>
            ))}
            {resources.length === 0 && (
              <TableRow>
                <TableCell colSpan={4} className="py-8 text-center text-muted-foreground">
                  La matrice des ressources (S02) n&apos;est pas encore renseignée.
                </TableCell>
              </TableRow>
            )}
          </TableBody>
        </Table>
      </div>
    </div>
  );
}
