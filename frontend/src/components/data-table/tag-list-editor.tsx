"use client";

import { Plus, X } from "lucide-react";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";

interface TagListEditorProps {
  items: string[];
  onChange: (items: string[]) => void;
  readOnly?: boolean;
  placeholder?: string;
}

export function TagListEditor({ items, onChange, readOnly, placeholder = "Saisir un élément…" }: TagListEditorProps) {
  const safeItems = items ?? [];

  function updateItem(index: number, value: string) {
    const next = [...safeItems];
    next[index] = value;
    onChange(next);
  }

  function removeItem(index: number) {
    onChange(safeItems.filter((_, i) => i !== index));
  }

  function addItem() {
    onChange([...safeItems, ""]);
  }

  if (readOnly) {
    if (safeItems.length === 0) {
      return <p className="text-[13px] italic text-muted-foreground">Aucun élément</p>;
    }
    return (
      <ul className="space-y-1.5">
        {safeItems.map((item, index) => (
          <li
            key={index}
            className="flex items-start gap-2 rounded-md bg-muted/40 px-2.5 py-1.5 text-[13px] leading-snug text-foreground/90"
          >
            <span className="mt-[7px] h-1 w-1 shrink-0 rounded-full bg-muted-foreground/60" aria-hidden />
            <span className="whitespace-pre-wrap break-words">
              {item?.trim() || <span className="italic text-muted-foreground/70">—</span>}
            </span>
          </li>
        ))}
      </ul>
    );
  }

  return (
    <div className="space-y-2">
      {safeItems.map((item, index) => (
        <div key={index} className="flex items-center gap-2">
          <Input value={item} onChange={(e) => updateItem(index, e.target.value)} placeholder={placeholder} className="bg-card" />
          <Button type="button" variant="ghost" size="icon" onClick={() => removeItem(index)} title="Supprimer">
            <X className="h-4 w-4" />
          </Button>
        </div>
      ))}
      <Button type="button" variant="link" size="sm" onClick={addItem} className="gap-1">
        <Plus className="h-3.5 w-3.5" /> Ajouter un élément
      </Button>
    </div>
  );
}
