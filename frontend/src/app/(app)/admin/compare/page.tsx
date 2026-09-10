"use client";

import type { CSSProperties } from "react";
import { useState } from "react";
import { useQuery } from "@tanstack/react-query";

import { Card, CardContent, CardHeader, CardTitle } from "@/components/ui/card";
import { NativeSelect } from "@/components/ui/native-select";
import { SectionFormRouter } from "@/components/sections/section-form-router";
import { StatusBadge } from "@/components/status-badge";
import { listGroups } from "@/lib/api/groups";
import { compareSection } from "@/lib/api/admin";
import { formatDateTime } from "@/lib/utils";
import { SECTION_PARTS, partCodes } from "@/lib/section-groups";
import type { SectionType } from "@/types/common";

export default function ComparePage() {
  const [sectionCode, setSectionCode] = useState<string>("S04"); // SWOT par defaut
  const [selectedGroupIds, setSelectedGroupIds] = useState<number[]>([]);

  const { data: groups } = useQuery({ queryKey: ["admin", "groups"], queryFn: listGroups });

  const { data: comparisons, isFetching } = useQuery({
    queryKey: ["admin", "compare", sectionCode, selectedGroupIds],
    queryFn: () => compareSection(sectionCode, selectedGroupIds),
    enabled: selectedGroupIds.length > 0,
  });

  const groupColorById = new Map((groups ?? []).map((g) => [g.id, g.color]));

  function toggleGroup(id: number) {
    setSelectedGroupIds((prev) => (prev.includes(id) ? prev.filter((g) => g !== id) : [...prev, id]));
  }

  return (
    <div className="space-y-5">
      <div>
        <h1>Vue comparative</h1>
        <p className="text-[13px] text-muted-foreground mt-1">Comparer les réponses de plusieurs groupes sur une même section</p>
      </div>

      <Card>
        <CardContent className="pt-5 flex flex-wrap items-start gap-6">
          <div className="space-y-1.5">
            <p className="text-[13px] font-medium text-foreground/90">Section</p>
            <NativeSelect value={sectionCode} onChange={(e) => setSectionCode(e.target.value)} className="w-64">
              {SECTION_PARTS.map((part) => (
                <optgroup key={part.id} label={`Partie ${part.numeral} — ${part.title}`}>
                  {partCodes(part).map((code) => (
                    <option key={code} value={code}>
                      {code}
                    </option>
                  ))}
                </optgroup>
              ))}
            </NativeSelect>
          </div>
          <div className="space-y-1.5">
            <p className="text-[13px] font-medium text-foreground/90">Groupes à comparer</p>
            <div className="flex flex-wrap gap-2 max-w-xl">
              {groups?.map((g) => (
                <button
                  key={g.id}
                  type="button"
                  onClick={() => toggleGroup(g.id)}
                  className={
                    "text-[13px] rounded-full px-3 py-1.5 border transition-colors " +
                    (selectedGroupIds.includes(g.id)
                      ? "bg-primary-500 text-white border-primary-500"
                      : "bg-card text-muted-foreground border-border hover:border-primary-300")
                  }
                >
                  {g.name}
                </button>
              ))}
            </div>
          </div>
        </CardContent>
      </Card>

      {selectedGroupIds.length === 0 && (
        <p className="text-center text-muted-foreground py-16">Sélectionnez au moins un groupe pour afficher la comparaison</p>
      )}

      {isFetching && <div className="h-64 bg-muted rounded-xl animate-pulse" />}

      {comparisons && comparisons.length > 0 && (
        <div className="grid grid-cols-1 xl:grid-cols-2 gap-4">
          {comparisons.map((response) => (
            <Card
              key={response.groupId}
              className="border-l-[3px]"
              style={
                {
                  borderLeftColor: groupColorById.get(response.groupId) ?? undefined,
                  // Repris par les onglets d'axes du formulaire (cf. .axis-tabs).
                  "--group-accent": groupColorById.get(response.groupId) ?? undefined,
                } as CSSProperties
              }
            >
              <CardHeader className="flex flex-row items-center justify-between gap-2">
                <CardTitle className="text-[15px] flex items-center gap-2">
                  <span
                    className="h-2.5 w-2.5 shrink-0 rounded-full"
                    style={{ backgroundColor: groupColorById.get(response.groupId) ?? "transparent" }}
                  />
                  {response.groupName}
                </CardTitle>
                <div className="flex items-center gap-2 shrink-0">
                  <StatusBadge status={response.status} />
                  {response.submittedAt && (
                    <span className="text-[12px] text-muted-foreground whitespace-nowrap">
                      Soumis le {formatDateTime(response.submittedAt)}
                    </span>
                  )}
                </div>
              </CardHeader>
              <CardContent>
                <SectionFormRouter type={response.type as SectionType} content={response.content} onChange={() => {}} readOnly />
              </CardContent>
            </Card>
          ))}
        </div>
      )}
    </div>
  );
}
