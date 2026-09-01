"use client";

import { Card, CardContent, CardHeader, CardTitle } from "@/components/ui/card";
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";
import { TagListEditor } from "@/components/data-table/tag-list-editor";
import type { StrategicAxesContent } from "@/types/sections";
import type { SectionFormProps } from "./types";

export function StrategicAxesForm({ content, onChange, readOnly }: SectionFormProps<StrategicAxesContent>) {
  function updateAxis(index: number, patch: Partial<StrategicAxesContent["axes"][number]>) {
    onChange((prev) => ({
      axes: prev.axes.map((a, i) => (i === index ? { ...a, ...patch } : a)),
    }));
  }

  return (
    <div className="grid grid-cols-1 lg:grid-cols-2 gap-4">
      {content.axes.map((axis, index) => (
        <Card key={axis.axisCode}>
          <CardHeader>
            <CardTitle className="text-[15px]">{axis.axisCode.replace("AXE", "Axe ")}</CardTitle>
          </CardHeader>
          <CardContent className="space-y-3">
            <div className="grid grid-cols-1 sm:grid-cols-2 gap-3">
              <div className="space-y-1.5">
                <Label required>Orientation stratégique</Label>
                <Input value={axis.title} onChange={(e) => updateAxis(index, { title: e.target.value })} readOnly={readOnly} />
              </div>
              <div className="space-y-1.5">
                <Label>Objectif de l&apos;axe</Label>
                <Input value={axis.objective ?? ""} onChange={(e) => updateAxis(index, { objective: e.target.value })} readOnly={readOnly} />
              </div>
            </div>
            <div className="space-y-1.5">
              <Label>Objectif spécifique</Label>
              <TagListEditor
                items={axis.specificObjectives}
                onChange={(items) => updateAxis(index, { specificObjectives: items })}
                readOnly={readOnly}
                placeholder="Saisir un objectif spécifique…"
              />
            </div>
          </CardContent>
        </Card>
      ))}
    </div>
  );
}
