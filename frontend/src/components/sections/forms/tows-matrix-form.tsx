"use client";

import { Card, CardContent, CardHeader, CardTitle } from "@/components/ui/card";
import { Textarea } from "@/components/ui/textarea";
import { Label } from "@/components/ui/label";
import { Accordion, AccordionContent, AccordionItem, AccordionTrigger } from "@/components/ui/accordion";
import type { TowsMatrixContent } from "@/types/sections";
import type { SectionFormProps } from "./types";

function RefList({ label, items, tone }: { label: string; items: string[]; tone: string }) {
  return (
    <div>
      <p className="text-[11px] uppercase tracking-wide font-semibold text-muted-foreground mb-1.5">{label}</p>
      {items.length === 0 ? (
        <p className="text-[13px] text-muted-foreground italic">Aucun élément (voir Section 4 — SWOT)</p>
      ) : (
        <ul className="space-y-1">
          {items.map((item, i) => (
            <li key={i} className={`text-[13px] rounded-md px-2 py-1 ${tone}`}>
              {item}
            </li>
          ))}
        </ul>
      )}
    </div>
  );
}

const FIELD_GROUPS: { title: string; fields: { key: keyof TowsMatrixContent; question: string }[] }[] = [
  {
    title: "Forces × Faiblesses",
    fields: [
      { key: "maximizeStrengths", question: "Comment maximiser les forces ?" },
      { key: "minimizeWeaknesses", question: "Comment minimiser les faiblesses ?" },
      { key: "strengthsControlWeaknesses", question: "En quoi les forces permettent-elles de maîtriser les faiblesses ?" },
    ],
  },
  {
    title: "Opportunités",
    fields: [
      { key: "maximizeOpportunities", question: "Comment maximiser les opportunités ?" },
      { key: "strengthsForOpportunities", question: "Comment utiliser les forces pour tirer parti des opportunités ?" },
      { key: "correctWeaknessesViaOpportunities", question: "Comment corriger les faiblesses en tirant parti des opportunités ?" },
    ],
  },
  {
    title: "Menaces",
    fields: [
      { key: "minimizeThreats", question: "Comment minimiser les menaces ?" },
      { key: "strengthsReduceThreats", question: "Comment utiliser les forces pour réduire les menaces ?" },
      { key: "minimizeWeaknessesAndThreats", question: "Comment minimiser les faiblesses et les menaces ?" },
    ],
  },
  {
    title: "Synthèse",
    fields: [
      { key: "opportunitiesMinimizeThreats", question: "En quoi les opportunités permettent-elles de minimiser les menaces ?" },
    ],
  },
];

export function TowsMatrixForm({ content, onChange, readOnly }: SectionFormProps<TowsMatrixContent>) {
  function updateField(key: keyof TowsMatrixContent, value: string) {
    onChange((prev) => ({ ...prev, [key]: value }));
  }

  return (
    <div className="space-y-4">
      <Card>
        <CardHeader>
          <CardTitle>Rappel — Analyse SWOT (Section 4)</CardTitle>
        </CardHeader>
        <CardContent className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-4 gap-4">
          <RefList label="Forces" items={content.strengths} tone="bg-primary-50 dark:bg-primary-500/10 text-primary-700 dark:text-primary-300" />
          <RefList label="Faiblesses" items={content.weaknesses} tone="bg-accent-50 dark:bg-accent-500/10 text-accent-700 dark:text-accent-300" />
          <RefList label="Opportunités" items={content.opportunities} tone="bg-blue-50 dark:bg-blue-500/10 text-blue-700 dark:text-blue-300" />
          <RefList label="Menaces" items={content.threats} tone="bg-amber-50 dark:bg-amber-500/10 text-amber-700 dark:text-amber-300" />
        </CardContent>
      </Card>

      <Card>
        <CardHeader>
          <CardTitle>Matrice de confrontation</CardTitle>
        </CardHeader>
        <CardContent className="pt-0">
          <Accordion type="multiple" defaultValue={FIELD_GROUPS.map((g) => g.title)} className="divide-y-0">
            {FIELD_GROUPS.map((group) => (
              <AccordionItem key={group.title} value={group.title}>
                <AccordionTrigger>{group.title}</AccordionTrigger>
                <AccordionContent className="space-y-4">
                  {group.fields.map((f) => (
                    <div key={String(f.key)} className="space-y-1.5">
                      <Label>{f.question}</Label>
                      <Textarea
                        value={content[f.key] as string}
                        onChange={(e) => updateField(f.key, e.target.value)}
                        disabled={readOnly}
                        rows={3}
                      />
                    </div>
                  ))}
                </AccordionContent>
              </AccordionItem>
            ))}
          </Accordion>
        </CardContent>
      </Card>
    </div>
  );
}
