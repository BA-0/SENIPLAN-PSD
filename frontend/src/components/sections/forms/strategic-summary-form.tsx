"use client";

import { Plus } from "lucide-react";
import { Tabs, TabsContent, TabsList, TabsTrigger } from "@/components/ui/tabs";
import { Card, CardContent, CardHeader } from "@/components/ui/card";
import { Textarea } from "@/components/ui/textarea";
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";
import { Button } from "@/components/ui/button";
import { RemoveRowButton } from "@/components/data-table/row-actions";
import type { StrategicSummaryContent, SummaryAxis, SummaryOrientation } from "@/types/sections";
import type { SectionFormProps } from "./types";

export function StrategicSummaryForm({ content, onChange, readOnly }: SectionFormProps<StrategicSummaryContent>) {
  function updateAxis(axisIndex: number, updater: (orientations: SummaryOrientation[]) => SummaryOrientation[]) {
    onChange((prev) => ({
      ...prev,
      axes: prev.axes.map((a, i) => (i === axisIndex ? { ...a, orientations: updater(a.orientations) } : a)),
    }));
  }

  function addOrientation(axisIndex: number) {
    updateAxis(axisIndex, (orientations) => [...orientations, { label: "", actions: [] }]);
  }

  function updateOrientation(axisIndex: number, orientationIndex: number, patch: Partial<SummaryOrientation>) {
    updateAxis(axisIndex, (orientations) =>
      orientations.map((o, i) => (i === orientationIndex ? { ...o, ...patch } : o))
    );
  }

  function removeOrientation(axisIndex: number, orientationIndex: number) {
    updateAxis(axisIndex, (orientations) => orientations.filter((_, i) => i !== orientationIndex));
  }

  function addAction(axisIndex: number, orientationIndex: number) {
    const orientation = content.axes[axisIndex].orientations[orientationIndex];
    updateOrientation(axisIndex, orientationIndex, {
      actions: [...orientation.actions, { label: "", constraintsOrOpportunities: "" }],
    });
  }

  function updateAction(axisIndex: number, orientationIndex: number, actionIndex: number, patch: Partial<SummaryAxis["orientations"][number]["actions"][number]>) {
    const orientation = content.axes[axisIndex].orientations[orientationIndex];
    updateOrientation(axisIndex, orientationIndex, {
      actions: orientation.actions.map((a, i) => (i === actionIndex ? { ...a, ...patch } : a)),
    });
  }

  function removeAction(axisIndex: number, orientationIndex: number, actionIndex: number) {
    const orientation = content.axes[axisIndex].orientations[orientationIndex];
    updateOrientation(axisIndex, orientationIndex, {
      actions: orientation.actions.filter((_, i) => i !== actionIndex),
    });
  }

  return (
    <div className="space-y-5">
      <Card>
        <CardHeader>
          <Label required>Vision</Label>
        </CardHeader>
        <CardContent>
          <Textarea
            value={content.vision}
            onChange={(e) => onChange((prev) => ({ ...prev, vision: e.target.value }))}
            disabled={readOnly}
            rows={3}
            placeholder="Vision globale du PSD 2027-2031…"
          />
        </CardContent>
      </Card>

      <Tabs defaultValue={content.axes[0]?.axisCode}>
        <TabsList>
          {content.axes.map((axis) => (
            <TabsTrigger key={axis.axisCode} value={axis.axisCode}>
              {axis.axisCode.replace("AXE", "Axe ")}
              {axis.axisTitle ? ` — ${axis.axisTitle}` : ""}
            </TabsTrigger>
          ))}
        </TabsList>

        {content.axes.map((axis, axisIndex) => (
          <TabsContent key={axis.axisCode} value={axis.axisCode} className="space-y-4">
            {axis.orientations.map((orientation, orientationIndex) => (
              <Card key={orientationIndex}>
                <CardHeader className="flex-col items-stretch gap-2">
                  <div className="flex items-center justify-between">
                    <span className="text-[13px] font-semibold text-primary-700">OS{orientationIndex + 1}</span>
                    {!readOnly && <RemoveRowButton onConfirm={() => removeOrientation(axisIndex, orientationIndex)} />}
                  </div>
                  <Input
                    value={orientation.label}
                    onChange={(e) => updateOrientation(axisIndex, orientationIndex, { label: e.target.value })}
                    disabled={readOnly}
                    placeholder="Intitulé de l'orientation stratégique…"
                  />
                </CardHeader>
                <CardContent className="space-y-2">
                  {orientation.actions.map((action, actionIndex) => (
                    <div key={actionIndex} className="grid grid-cols-1 sm:grid-cols-[auto_1fr_1fr_auto] gap-2 items-start rounded-lg bg-slate-50 p-2.5">
                      <span className="text-[12px] font-medium text-slate-500 pt-2.5 sm:pt-0 sm:self-center">
                        Action {orientationIndex + 1}.{actionIndex + 1}
                      </span>
                      <Input
                        value={action.label}
                        onChange={(e) => updateAction(axisIndex, orientationIndex, actionIndex, { label: e.target.value })}
                        disabled={readOnly}
                        placeholder="Intitulé de l'action…"
                        className="bg-white"
                      />
                      <Input
                        value={action.constraintsOrOpportunities}
                        onChange={(e) =>
                          updateAction(axisIndex, orientationIndex, actionIndex, { constraintsOrOpportunities: e.target.value })
                        }
                        disabled={readOnly}
                        placeholder="Contraintes à lever / opportunités à saisir…"
                        className="bg-white"
                      />
                      {!readOnly && (
                        <RemoveRowButton onConfirm={() => removeAction(axisIndex, orientationIndex, actionIndex)} />
                      )}
                    </div>
                  ))}
                  {!readOnly && (
                    <Button type="button" variant="link" size="sm" onClick={() => addAction(axisIndex, orientationIndex)} className="gap-1">
                      <Plus className="h-3.5 w-3.5" /> Ajouter une action
                    </Button>
                  )}
                </CardContent>
              </Card>
            ))}
            {!readOnly && (
              <Button type="button" variant="secondary" size="sm" onClick={() => addOrientation(axisIndex)}>
                <Plus className="h-4 w-4" /> Ajouter une orientation stratégique
              </Button>
            )}
          </TabsContent>
        ))}
      </Tabs>
    </div>
  );
}
