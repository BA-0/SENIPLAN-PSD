"use client";

import { Table, TableBody, TableCell, TableHead, TableHeader, TableRow } from "@/components/ui/table";
import { EditableCell } from "./editable-cell";

interface NoteTableProps {
  title: string;
  /** Precision affichee a cote du titre, en minuscules (ex. « lecture seule »). */
  hint?: string;
  value: string;
  onChange: (value: string) => void;
  readOnly?: boolean;
  placeholder?: string;
  rows?: number;
  required?: boolean;
}

/**
 * Une note de synthese presentee comme les autres saisies : un tableau a une colonne, son intitule
 * en en-tete et le texte dans l'unique cellule. Les sections ne melangent ainsi pas tableaux et
 * champs libres.
 */
export function NoteTable({ title, hint, value, onChange, readOnly, placeholder, rows = 4, required }: NoteTableProps) {
  return (
    <Table>
      <TableHeader>
        <TableRow>
          <TableHead className="whitespace-normal">
            {title}
            {required && (
              <span className="ml-0.5 text-accent-500" aria-hidden>
                *
              </span>
            )}
            {hint && <span className="ml-2 font-normal normal-case tracking-normal">{hint}</span>}
          </TableHead>
        </TableRow>
      </TableHeader>
      <TableBody>
        <TableRow className="hover:bg-transparent">
          <TableCell className="py-3">
            <EditableCell value={value ?? ""} onChange={onChange} readOnly={readOnly} placeholder={placeholder} multiline rows={rows} />
          </TableCell>
        </TableRow>
      </TableBody>
    </Table>
  );
}
