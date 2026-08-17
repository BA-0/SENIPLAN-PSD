"use client";

import { useState } from "react";
import { useQuery } from "@tanstack/react-query";

import { Card, CardContent, CardHeader, CardTitle } from "@/components/ui/card";
import { NativeSelect } from "@/components/ui/native-select";
import { SectionFormRouter } from "@/components/sections/section-form-router";
import { listGroups } from "@/lib/api/groups";
import { compareSection } from "@/lib/api/admin";
import { SECTION_CODES } from "@/types/common";
import type { SectionType } from "@/types/common";

export default function ComparePage() {
  const [sectionCode, setSectionCode] = useState<string>(SECTION_CODES[3]); // S04 SWOT par defaut
  const [selectedGroupIds, setSelectedGroupIds] = useState<number[]>([]);

  const { data: groups } = useQuery({ queryKey: ["admin", "groups"], queryFn: listGroups });

  const { data: comparisons, isFetching } = useQuery({
    queryKey: ["admin", "compare", sectionCode, selectedGroupIds],
    queryFn: () => compareSection(sectionCode, selectedGroupIds),
    enabled: selectedGroupIds.length > 0,
  });

  function toggleGroup(id: number) {
    setSelectedGroupIds((prev) => (prev.includes(id) ? prev.filter((g) => g !== id) : [...prev, id]));
  }

  return (
    <div className="space-y-5">
      <div>
        <h1>Vue comparative</h1>
        <p className="text-[13px] text-slate-500 mt-1">Comparer les réponses de plusieurs groupes sur une même section</p>
      </div>

      <Card>
        <CardContent className="pt-5 flex flex-wrap items-start gap-6">
          <div className="space-y-1.5">
            <p className="text-[13px] font-medium text-slate-700">Section</p>
            <NativeSelect value={sectionCode} onChange={(e) => setSectionCode(e.target.value)} className="w-64">
              {SECTION_CODES.map((code) => (
                <option key={code} value={code}>
                  {code}
                </option>
              ))}
            </NativeSelect>
          </div>
          <div className="space-y-1.5">
            <p className="text-[13px] font-medium text-slate-700">Groupes à comparer</p>
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
                      : "bg-white text-slate-600 border-slate-200 hover:border-primary-300")
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
        <p className="text-center text-slate-400 py-16">Sélectionnez au moins un groupe pour afficher la comparaison</p>
      )}

      {isFetching && <div className="h-64 bg-slate-200 rounded-xl animate-pulse" />}

      {comparisons && comparisons.length > 0 && (
        <div className="grid grid-cols-1 xl:grid-cols-2 gap-4">
          {comparisons.map((response) => (
            <Card key={response.groupId}>
              <CardHeader>
                <CardTitle className="text-[15px]">{response.groupName}</CardTitle>
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
