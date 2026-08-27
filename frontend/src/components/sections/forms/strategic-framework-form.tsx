"use client";

import { Card, CardContent, CardHeader } from "@/components/ui/card";
import { Textarea } from "@/components/ui/textarea";
import { Label } from "@/components/ui/label";
import { TagListEditor } from "@/components/data-table/tag-list-editor";
import type { StrategicFrameworkContent } from "@/types/sections";
import type { SectionFormProps } from "./types";

export function StrategicFrameworkForm({ content, onChange, readOnly }: SectionFormProps<StrategicFrameworkContent>) {
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

      <Card>
        <CardHeader>
          <Label>Mission</Label>
        </CardHeader>
        <CardContent>
          <TagListEditor
            items={content.mission}
            onChange={(items) => onChange((prev) => ({ ...prev, mission: items }))}
            readOnly={readOnly}
            placeholder="Saisir un élément de mission…"
          />
        </CardContent>
      </Card>

      <Card>
        <CardHeader>
          <Label>Valeurs</Label>
        </CardHeader>
        <CardContent>
          <TagListEditor
            items={content.values}
            onChange={(items) => onChange((prev) => ({ ...prev, values: items }))}
            readOnly={readOnly}
            placeholder="Saisir une valeur…"
          />
        </CardContent>
      </Card>
    </div>
  );
}
