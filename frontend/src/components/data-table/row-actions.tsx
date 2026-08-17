"use client";

import { Plus, Trash2 } from "lucide-react";
import { Button } from "@/components/ui/button";
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
