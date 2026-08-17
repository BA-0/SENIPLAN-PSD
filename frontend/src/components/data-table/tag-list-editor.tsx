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
  function updateItem(index: number, value: string) {
    const next = [...items];
    next[index] = value;
    onChange(next);
  }

  function removeItem(index: number) {
    onChange(items.filter((_, i) => i !== index));
  }

  function addItem() {
    onChange([...items, ""]);
  }

  return (
    <div className="space-y-2">
      {items.map((item, index) => (
        <div key={index} className="flex items-center gap-2">
          <Input
            value={item}
            onChange={(e) => updateItem(index, e.target.value)}
            disabled={readOnly}
            placeholder={placeholder}
            className="bg-white"
          />
          {!readOnly && (
            <Button type="button" variant="ghost" size="icon" onClick={() => removeItem(index)} title="Supprimer">
              <X className="h-4 w-4" />
            </Button>
          )}
        </div>
      ))}
      {!readOnly && (
        <Button type="button" variant="link" size="sm" onClick={addItem} className="gap-1">
          <Plus className="h-3.5 w-3.5" /> Ajouter un élément
        </Button>
      )}
      {items.length === 0 && readOnly && <p className="text-[13px] text-slate-400 italic">Aucun élément</p>}
    </div>
  );
}
