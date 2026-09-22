"use client";

import { useState } from "react";
import { Plus, Trash2 } from "lucide-react";
import { Button } from "@/components/ui/button";
import { NativeSelect } from "@/components/ui/native-select";
import { TableCell, TableRow } from "@/components/ui/table";
import {
  AlertDialog,
  AlertDialogAction,
  AlertDialogCancel,
  AlertDialogContent,
  AlertDialogDescription,
  AlertDialogFooter,
  AlertDialogHeader,
  AlertDialogTitle,
  AlertDialogTrigger,
} from "@/components/ui/alert-dialog";

export function AddRowButton({ onAdd, label = "Ajouter une ligne", disabled }: { onAdd: () => void; label?: string; disabled?: boolean }) {
  return (
    <Button type="button" variant="secondary" size="sm" onClick={onAdd} disabled={disabled}>
      <Plus className="h-4 w-4" />
      {label}
    </Button>
  );
}

export function RemoveRowButton({ onConfirm, disabled }: { onConfirm: () => void; disabled?: boolean }) {
  return (
    <AlertDialog>
      <AlertDialogTrigger asChild>
        <Button type="button" variant="destructiveGhost" size="icon" disabled={disabled} title="Supprimer la ligne">
          <Trash2 className="h-4 w-4" />
        </Button>
      </AlertDialogTrigger>
      <AlertDialogContent>
        <AlertDialogHeader>
          <AlertDialogTitle>Supprimer cette ligne ?</AlertDialogTitle>
          <AlertDialogDescription>Cette action est irréversible.</AlertDialogDescription>
        </AlertDialogHeader>
        <AlertDialogFooter>
          <AlertDialogCancel>Annuler</AlertDialogCancel>
          <AlertDialogAction variant="destructive" onClick={onConfirm}>
            Supprimer
          </AlertDialogAction>
        </AlertDialogFooter>
      </AlertDialogContent>
    </AlertDialog>
  );
}

/**
 * Ajout d'une ligne choisie dans la liste du modele (axe PESTEL, ressource, source de financement…) :
 * les tableaux demarrent vides et la direction n'ajoute que les lignes qu'elle renseigne. Seules les
 * lignes pas encore presentes sont proposees ; le controle disparait quand il n'en reste aucune.
 */
export function KeyedRowAdder({
  options,
  onAdd,
  label = "Ajouter",
  placeholder = "Choisir une ligne…",
}: {
  options: { value: string; label: string }[];
  onAdd: (value: string) => void;
  label?: string;
  placeholder?: string;
}) {
  const [selected, setSelected] = useState("");
  if (options.length === 0) return null;
  const current = options.some((o) => o.value === selected) ? selected : "";

  return (
    <div className="flex flex-wrap items-center gap-2">
      <div className="w-full max-w-md">
        <NativeSelect value={current} onChange={(e) => setSelected(e.target.value)}>
          <option value="">{placeholder}</option>
          {options.map((o) => (
            <option key={o.value} value={o.value}>
              {o.label}
            </option>
          ))}
        </NativeSelect>
      </div>
      <AddRowButton
        label={label}
        disabled={!current}
        onAdd={() => {
          onAdd(current);
          setSelected("");
        }}
      />
    </div>
  );
}

/** Insere une ligne a sa place dans l'ordre du modele ; les lignes hors modele restent en fin de tableau. */
export function insertInModelOrder<R>(rows: R[], row: R, keyOf: (r: R) => string, order: readonly string[]): R[] {
  const rank = (r: R) => {
    const i = order.indexOf(keyOf(r));
    return i < 0 ? order.length : i;
  };
  const at = rows.findIndex((r) => rank(r) > rank(row));
  return at < 0 ? [...rows, row] : [...rows.slice(0, at), row, ...rows.slice(at)];
}

/** Options du modele pas encore utilisees par une ligne du tableau. */
export function remainingOptions(order: readonly string[], labels: Record<string, string>, used: string[]) {
  return order.filter((k) => !used.includes(k)).map((k) => ({ value: k, label: labels[k] ?? k }));
}

/** Ligne d'ajout en pied de tableau (ou de bloc) : un lien « + Ajouter … » sur toute la largeur. */
export function TableAddRow({
  colSpan,
  onAdd,
  label,
  emphasis,
}: {
  colSpan: number;
  onAdd: () => void;
  label: string;
  emphasis?: boolean;
}) {
  return (
    <TableRow className="hover:bg-transparent">
      <TableCell colSpan={colSpan} className="py-1.5">
        <Button type="button" variant="link" size="sm" onClick={onAdd} className={emphasis ? "gap-1 font-semibold" : "gap-1"}>
          <Plus className="h-3.5 w-3.5" /> {label}
        </Button>
      </TableCell>
    </TableRow>
  );
}
