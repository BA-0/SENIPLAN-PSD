"use client";

import { Input } from "@/components/ui/input";

/**
 * Intitule d'un axe, modifiable par l'admin et la direction generale depuis les sections qui le
 * reprennent de S08 (cadre logique, plan d'actions, budget...). Le serveur reporte la correction
 * dans S08, d'ou elle vaut pour toutes les sections de la direction.
 */
export function AxisTitleField({ value, onChange }: { value: string | undefined; onChange: (title: string) => void }) {
  return (
    <label className="flex flex-col gap-1.5 text-[13px] font-medium">
      Intitulé de l&apos;axe (orientation stratégique)
      <Input value={value ?? ""} onChange={(e) => onChange(e.target.value)} placeholder="Intitulé de l'axe…" />
      <span className="text-[12px] font-normal text-muted-foreground">
        Repris dans les axes stratégiques (S08) et toutes les sections de la direction à l&apos;enregistrement.
      </span>
    </label>
  );
}

/** Renomme l'axe {@code axisIndex} d'un contenu de section a axes. */
export function renameAxis<T extends { axes: { axisTitle?: string }[] }>(prev: T, axisIndex: number, title: string): T {
  return { ...prev, axes: prev.axes.map((a, i) => (i === axisIndex ? { ...a, axisTitle: title } : a)) };
}
