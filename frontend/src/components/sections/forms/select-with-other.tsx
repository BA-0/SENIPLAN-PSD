"use client";

import * as React from "react";
import { Check } from "lucide-react";
import { cn } from "@/lib/utils";
import { NativeSelect } from "@/components/ui/native-select";

interface SelectWithOtherProps {
  value: string;
  onChange: (value: string) => void;
  /** Valeurs predefinies du modele, dans l'ordre d'affichage. */
  options: readonly string[];
  labels: Record<string, string>;
  /** Valeur de l'option « Autre » : la choisir ouvre la saisie libre. */
  otherValue: string;
  /** Valeurs libres deja saisies ailleurs (autres lignes), proposees a leur tour dans la liste. */
  customValues?: string[];
  readOnly?: boolean;
  placeholder?: string;
}

/**
 * Liste deroulante dont l'option « Autre » laisse l'utilisateur saisir sa propre valeur. Le texte saisi
 * devient la valeur de la cellule et s'ajoute a la liste (pour cette ligne comme pour les suivantes).
 * Une saisie qui reprend le libelle d'une option predefinie retombe sur cette option.
 */
export function SelectWithOther({
  value,
  onChange,
  options,
  labels,
  otherValue,
  customValues = [],
  readOnly,
  placeholder = "Préciser…",
}: SelectWithOtherProps) {
  const [draft, setDraft] = React.useState("");
  const isCustom = value !== "" && !options.includes(value);
  const editing = !readOnly && value === otherValue;

  const extras = Array.from(new Set([...customValues, ...(isCustom ? [value] : [])].filter((v) => v && !options.includes(v)))).sort(
    (a, b) => a.localeCompare(b, "fr")
  );

  function commit() {
    const text = draft.trim().replace(/\s+/g, " ");
    if (!text) return;
    const known = options.find((o) => o !== otherValue && (labels[o] ?? o).toLowerCase() === text.toLowerCase());
    const existing = extras.find((e) => e.toLowerCase() === text.toLowerCase());
    onChange(known ?? existing ?? text);
    setDraft("");
  }

  return (
    <div className="space-y-1.5">
      <NativeSelect
        cellStyle
        value={value}
        disabled={readOnly}
        onChange={(e) => {
          setDraft("");
          onChange(e.target.value);
        }}
      >
        <option value="">—</option>
        {options
          .filter((o) => o !== otherValue)
          .map((o) => (
            <option key={o} value={o}>
              {labels[o] ?? o}
            </option>
          ))}
        {extras.map((e) => (
          <option key={`custom-${e}`} value={e}>
            {e}
          </option>
        ))}
        <option value={otherValue}>{readOnly ? labels[otherValue] ?? otherValue : `${labels[otherValue] ?? otherValue} (préciser)…`}</option>
      </NativeSelect>
      {editing && (
        <div className="flex items-center gap-1">
          <input
            type="text"
            autoFocus
            value={draft}
            onChange={(e) => setDraft(e.target.value)}
            onBlur={commit}
            onKeyDown={(e) => {
              if (e.key === "Enter") {
                e.preventDefault();
                commit();
              }
            }}
            placeholder={placeholder}
            className={cn(
              "w-full min-w-0 rounded-md border border-border bg-muted/40 px-2.5 py-1.5 text-sm text-foreground shadow-sm",
              "placeholder:text-muted-foreground/70 focus:bg-card focus:border-primary-500 focus:ring-2 focus:ring-primary-500/20 focus:outline-none"
            )}
          />
          <button
            type="button"
            onMouseDown={(e) => e.preventDefault()}
            onClick={commit}
            disabled={!draft.trim()}
            aria-label="Ajouter à la liste"
            title="Ajouter à la liste"
            className="inline-flex h-8 w-8 shrink-0 items-center justify-center rounded-md text-primary-600 hover:bg-primary-50 disabled:opacity-40 dark:hover:bg-primary-900/30"
          >
            <Check className="h-4 w-4" />
          </button>
        </div>
      )}
    </div>
  );
}
