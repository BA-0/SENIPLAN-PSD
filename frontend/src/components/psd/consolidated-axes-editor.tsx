"use client";

import { useEffect, useMemo, useState } from "react";
import { useQuery } from "@tanstack/react-query";
import { toast } from "sonner";
import { ArrowDown, ArrowUp, Plus, Trash2 } from "lucide-react";

import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";
import { NativeSelect } from "@/components/ui/native-select";
import { Textarea } from "@/components/ui/textarea";
import { compareSection } from "@/lib/api/admin";
import { listGroups } from "@/lib/api/groups";
import { updateNarrativeBlock } from "@/lib/api/psd-narrative";
import { extractErrorMessage } from "@/lib/api-client";
import type { StrategicAxesContent } from "@/types/sections";
import {
  AXES_CONSOLIDES_KEY,
  type ConsolidatedAxesContent,
  type ConsolidatedAxis,
} from "@/types/psd-narrative";

/** Axe proposé par une direction dans sa section S08. */
interface DirectionAxis {
  groupId: number;
  groupName: string;
  color: string | null;
  axisCode: string;
  title: string;
}

function parseAxes(content: string | null | undefined): ConsolidatedAxis[] {
  if (!content || !content.trim()) return [];
  try {
    const parsed = JSON.parse(content) as Partial<ConsolidatedAxesContent>;
    return (parsed.axes ?? []).map((axis) => ({
      title: axis.title ?? "",
      objective: axis.objective ?? "",
      links: (axis.links ?? []).filter((link) => link && typeof link.groupId === "number" && !!link.axisCode),
    }));
  } catch {
    return [];
  }
}

const linkKey = (groupId: number, axisCode: string) => `${groupId}:${axisCode}`;

/**
 * Axes stratégiques de l'entreprise, arrêtés par la Direction Générale.
 *
 * Chaque direction propose quatre axes dans son canevas : additionnés, cela faisait une vingtaine
 * d'« axes stratégiques » pour SENICO. Ici, on définit les quelques axes communs à l'entreprise,
 * puis on rattache chaque axe proposé par une direction à l'un d'eux. La note de synthèse et le
 * Plan Stratégique de SENICO regroupent ensuite objectifs, budget et actions sous ces axes.
 */
export function ConsolidatedAxesEditor({
  content,
  onSaved,
}: {
  content: string;
  onSaved: () => Promise<unknown> | void;
}) {
  const [axes, setAxes] = useState<ConsolidatedAxis[]>(() => parseAxes(content));
  const [dirty, setDirty] = useState(false);
  const [saving, setSaving] = useState(false);

  // Resynchronise avec le serveur tant que l'utilisateur n'a rien modifié.
  useEffect(() => {
    if (!dirty) setAxes(parseAxes(content));
  }, [content, dirty]);

  const { data: groups } = useQuery({ queryKey: ["admin", "groups"], queryFn: listGroups });
  const groupIds = useMemo(() => (groups ?? []).map((group) => group.id), [groups]);
  const { data: sections, isLoading } = useQuery({
    queryKey: ["admin", "compare", "S08", groupIds],
    queryFn: () => compareSection<StrategicAxesContent>("S08", groupIds),
    enabled: groupIds.length > 0,
  });

  const directionAxes = useMemo<DirectionAxis[]>(() => {
    const colorById = new Map((groups ?? []).map((group) => [group.id, group.color ?? null]));
    return (sections ?? []).flatMap((section) =>
      (section.content?.axes ?? [])
        .filter((axis) => axis.title?.trim())
        .map((axis) => ({
          groupId: section.groupId,
          groupName: section.groupName,
          color: colorById.get(section.groupId) ?? null,
          axisCode: axis.axisCode,
          title: axis.title.trim(),
        }))
    );
  }, [sections, groups]);

  const assignment = useMemo(() => {
    const map = new Map<string, number>();
    axes.forEach((axis, index) =>
      axis.links.forEach((link) => map.set(linkKey(link.groupId, link.axisCode), index))
    );
    return map;
  }, [axes]);

  const unlinked = directionAxes.filter((axis) => !assignment.has(linkKey(axis.groupId, axis.axisCode)));

  function update(next: ConsolidatedAxis[]) {
    setAxes(next);
    setDirty(true);
  }

  function patchAxis(index: number, patch: Partial<ConsolidatedAxis>) {
    update(axes.map((axis, i) => (i === index ? { ...axis, ...patch } : axis)));
  }

  function moveAxis(index: number, delta: number) {
    const target = index + delta;
    if (target < 0 || target >= axes.length) return;
    const next = [...axes];
    [next[index], next[target]] = [next[target], next[index]];
    update(next);
  }

  function assign(axis: DirectionAxis, targetIndex: number) {
    const key = linkKey(axis.groupId, axis.axisCode);
    update(
      axes.map((item, i) => {
        const links = item.links.filter((link) => linkKey(link.groupId, link.axisCode) !== key);
        return {
          ...item,
          links: i === targetIndex ? [...links, { groupId: axis.groupId, axisCode: axis.axisCode }] : links,
        };
      })
    );
  }

  async function save() {
    if (axes.some((axis) => !axis.title.trim())) {
      toast.error("Chaque axe stratégique doit avoir un intitulé.");
      return;
    }
    setSaving(true);
    try {
      const payload: ConsolidatedAxesContent = {
        axes: axes.map((axis) => ({ ...axis, title: axis.title.trim(), objective: axis.objective.trim() })),
      };
      await updateNarrativeBlock(AXES_CONSOLIDES_KEY, axes.length === 0 ? "" : JSON.stringify(payload));
      toast.success("Axes stratégiques enregistrés");
      setDirty(false);
      await onSaved();
    } catch (error) {
      toast.error(extractErrorMessage(error, "Échec de l'enregistrement des axes"));
    } finally {
      setSaving(false);
    }
  }

  return (
    <div className="space-y-6">
      <p className="text-[13px] text-muted-foreground">
        Définissez les axes communs à toute l&apos;entreprise — quatre ou cinq, comme dans un plan stratégique publié —
        puis rattachez-y chaque axe proposé par les directions. Les documents regroupent alors objectifs spécifiques,
        budget et actions sous ces axes. Sans axe défini, ils présentent les axes des directions sans regroupement, en
        le signalant.
      </p>

      <div className="space-y-4">
        {axes.map((axis, index) => (
          <div key={index} className="space-y-3 rounded-lg border border-border p-4">
            <div className="flex flex-wrap items-center gap-3">
              <span className="text-[12px] font-semibold uppercase tracking-wide text-primary-600">Axe {index + 1}</span>
              <span className="text-[12px] text-muted-foreground">
                {axis.links.length} axe{axis.links.length > 1 ? "s" : ""} de direction rattaché
                {axis.links.length > 1 ? "s" : ""}
              </span>
              <div className="ml-auto flex gap-1">
                <Button
                  size="sm"
                  variant="secondary"
                  onClick={() => moveAxis(index, -1)}
                  disabled={index === 0}
                  aria-label="Monter l'axe"
                >
                  <ArrowUp className="h-4 w-4" />
                </Button>
                <Button
                  size="sm"
                  variant="secondary"
                  onClick={() => moveAxis(index, 1)}
                  disabled={index === axes.length - 1}
                  aria-label="Descendre l'axe"
                >
                  <ArrowDown className="h-4 w-4" />
                </Button>
                <Button
                  size="sm"
                  variant="secondary"
                  onClick={() => update(axes.filter((_, i) => i !== index))}
                  aria-label="Supprimer l'axe"
                >
                  <Trash2 className="h-4 w-4" />
                </Button>
              </div>
            </div>
            <div className="space-y-1.5">
              <Label htmlFor={`axis-title-${index}`}>Intitulé</Label>
              <Input
                id={`axis-title-${index}`}
                value={axis.title}
                placeholder="Ex. Modernisation de l'outil de production et performance opérationnelle"
                onChange={(event) => patchAxis(index, { title: event.target.value })}
              />
            </div>
            <div className="space-y-1.5">
              <Label htmlFor={`axis-objective-${index}`}>Objectif général</Label>
              <Textarea
                id={`axis-objective-${index}`}
                rows={2}
                value={axis.objective}
                placeholder="Ce que l'axe vise à l'horizon 2031, en une ou deux phrases."
                onChange={(event) => patchAxis(index, { objective: event.target.value })}
              />
            </div>
          </div>
        ))}
        <Button
          variant="secondary"
          size="sm"
          onClick={() => update([...axes, { title: "", objective: "", links: [] }])}
        >
          <Plus className="h-4 w-4" /> Ajouter un axe
        </Button>
      </div>

      <div className="space-y-3">
        <h3 className="text-[14px] font-semibold text-foreground">Rattachement des axes proposés par les directions</h3>
        {unlinked.length > 0 && axes.length > 0 && (
          <p className="text-[13px] text-amber-700 dark:text-amber-400">
            {unlinked.length} axe{unlinked.length > 1 ? "s" : ""} de direction non rattaché
            {unlinked.length > 1 ? "s" : ""} : leur budget et leurs actions apparaîtront à part dans les documents.
          </p>
        )}
        {isLoading ? (
          <p className="text-[13px] text-muted-foreground">Chargement des axes des directions…</p>
        ) : directionAxes.length === 0 ? (
          <p className="text-[13px] text-muted-foreground">Aucune direction n&apos;a encore renseigné ses axes (section S08).</p>
        ) : (
          <div className="overflow-x-auto rounded-lg border border-border">
            <table className="w-full text-[13px]">
              <thead className="bg-muted/50 text-left text-muted-foreground">
                <tr>
                  <th className="px-3 py-2 font-medium">Direction</th>
                  <th className="px-3 py-2 font-medium">Axe proposé</th>
                  <th className="px-3 py-2 font-medium">Axe de l&apos;entreprise</th>
                </tr>
              </thead>
              <tbody>
                {directionAxes.map((axis) => {
                  const key = linkKey(axis.groupId, axis.axisCode);
                  const current = assignment.get(key) ?? -1;
                  return (
                    <tr key={key} className="border-t border-border">
                      <td className="px-3 py-2 align-middle">
                        <span className="flex items-center gap-2">
                          <span
                            className="h-2.5 w-2.5 shrink-0 rounded-full border border-border/60"
                            style={{ backgroundColor: axis.color ?? "transparent" }}
                          />
                          {axis.groupName}
                        </span>
                      </td>
                      <td className="px-3 py-2 align-middle">
                        <span className="text-muted-foreground">{axis.axisCode.replace("AXE", "Axe ")} — </span>
                        {axis.title}
                      </td>
                      <td className="min-w-[280px] px-3 py-2 align-middle">
                        <NativeSelect
                          value={String(current)}
                          onChange={(event) => assign(axis, Number(event.target.value))}
                          aria-label={`Axe de l'entreprise pour « ${axis.title} »`}
                        >
                          <option value="-1">— Non rattaché —</option>
                          {axes.map((item, i) => (
                            <option key={i} value={String(i)}>
                              {`Axe ${i + 1}${item.title.trim() ? ` : ${item.title.trim()}` : ""}`}
                            </option>
                          ))}
                        </NativeSelect>
                      </td>
                    </tr>
                  );
                })}
              </tbody>
            </table>
          </div>
        )}
      </div>

      <div className="flex justify-end">
        <Button variant="primary" onClick={save} loading={saving} disabled={!dirty}>
          Enregistrer les axes
        </Button>
      </div>
    </div>
  );
}
