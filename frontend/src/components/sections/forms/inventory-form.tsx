"use client";

import { Card, CardContent, CardHeader, CardTitle } from "@/components/ui/card";
import { Textarea } from "@/components/ui/textarea";
import { Label } from "@/components/ui/label";
import { Badge } from "@/components/ui/badge";
import { PESTEL_LABELS, CAUSAL_LABELS } from "@/types/sections";
import type { InventoryContent } from "@/types/sections";
import type { SectionFormProps } from "./types";

export function InventoryForm({ content, onChange, readOnly }: SectionFormProps<InventoryContent>) {
  return (
    <div className="space-y-4">
      <Card>
        <CardHeader>
          <CardTitle>Note de synthèse</CardTitle>
        </CardHeader>
        <CardContent>
          <Textarea
            value={content.synthesisNote}
            onChange={(e) => onChange((prev) => ({ ...prev, synthesisNote: e.target.value }))}
            disabled={readOnly}
            rows={4}
            placeholder="Synthèse consolidée du diagnostic (parties prenantes, PESTEL, SWOT, analyse causale)…"
          />
        </CardContent>
      </Card>

      <Card>
        <CardHeader>
          <CardTitle>Parties prenantes (Section 1)</CardTitle>
        </CardHeader>
        <CardContent className="space-y-2">
          {content.stakeholders.length === 0 && <p className="text-[13px] text-muted-foreground italic">Aucune donnée</p>}
          {content.stakeholders.map((s, i) => (
            <div key={i} className="text-[13px] rounded-lg border border-border/60 p-2.5">
              <span className="font-medium text-foreground">{s.actor || "—"}</span>
              <span className="text-muted-foreground"> · {s.roles}</span>
            </div>
          ))}
        </CardContent>
      </Card>

      <Card>
        <CardHeader>
          <CardTitle>PESTEL (Section 3)</CardTitle>
        </CardHeader>
        <CardContent className="grid grid-cols-1 sm:grid-cols-2 gap-2">
          {content.pestel.map((p, i) => (
            <div key={i} className="text-[13px] rounded-lg border border-border/60 p-2.5">
              <p className="font-medium text-foreground mb-1">{PESTEL_LABELS[p.axis] ?? p.axis}</p>
              <p className="text-muted-foreground">Menaces : {p.threats || "—"}</p>
              <p className="text-muted-foreground">Opportunités : {p.opportunities || "—"}</p>
            </div>
          ))}
        </CardContent>
      </Card>

      <Card>
        <CardHeader>
          <CardTitle>SWOT (Section 4)</CardTitle>
        </CardHeader>
        <CardContent className="grid grid-cols-2 sm:grid-cols-4 gap-3">
          <SwotMini label="Forces" items={content.swot.strengths} variant="submitted" />
          <SwotMini label="Faiblesses" items={content.swot.weaknesses} variant="criticalHigh" />
          <SwotMini label="Opportunités" items={content.swot.opportunities} variant="inProgress" />
          <SwotMini label="Menaces" items={content.swot.threats} variant="criticalMedium" />
        </CardContent>
      </Card>

      <Card>
        <CardHeader>
          <CardTitle>Analyse causale (Section 6)</CardTitle>
        </CardHeader>
        <CardContent className="space-y-2">
          {content.causalAnalysis.map((c, i) => (
            <div key={i} className="text-[13px]">
              <span className="font-medium text-foreground">{CAUSAL_LABELS[c.source] ?? c.source} : </span>
              <span className="text-muted-foreground">{c.items.filter(Boolean).join(", ") || "—"}</span>
            </div>
          ))}
        </CardContent>
      </Card>
    </div>
  );
}

function SwotMini({ label, items, variant }: { label: string; items: string[]; variant: string }) {
  return (
    <div>
      <Label className="mb-1.5 block">{label}</Label>
      <div className="flex flex-wrap gap-1">
        {items.length === 0 && <span className="text-[13px] text-muted-foreground italic">—</span>}
        {items.map((item, i) => (
          <Badge key={i} variant={variant as never} className="font-normal">
            {item}
          </Badge>
        ))}
      </div>
    </div>
  );
}
