"use client";

import { Card, CardContent, CardHeader, CardTitle } from "@/components/ui/card";
import { Input } from "@/components/ui/input";
import { Textarea } from "@/components/ui/textarea";
import { Label } from "@/components/ui/label";
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
            <div className="space-y-1.5">
              <Label required>Intitulé</Label>
              <Input value={axis.title} onChange={(e) => updateAxis(index, { title: e.target.value })} disabled={readOnly} />
            </div>
            <div className="space-y-1.5">
              <Label>Description</Label>
              <Textarea value={axis.description} onChange={(e) => updateAxis(index, { description: e.target.value })} disabled={readOnly} rows={3} />
            </div>
          </CardContent>
        </Card>
      ))}
    </div>
  );
}
