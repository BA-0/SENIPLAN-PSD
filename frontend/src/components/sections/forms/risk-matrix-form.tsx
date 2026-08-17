"use client";

import { Table, TableBody, TableCell, TableHead, TableHeader, TableRow } from "@/components/ui/table";
import { EditableCell } from "@/components/data-table/editable-cell";
import { AddRowButton, RemoveRowButton } from "@/components/data-table/row-actions";
import { NativeSelect } from "@/components/ui/native-select";
import { CriticalityBadge } from "@/components/status-badge";
import type { RiskMatrixContent, RiskRow } from "@/types/sections";
import type { SectionFormProps } from "./types";

const EMPTY_ROW: RiskRow = {
  category: "",
  present: false,
  riskDetails: "",
  levelN: 1,
  impactAreas: "",
  quotationQ: 1,
  mitigationActions: "",
};

export function RiskMatrixForm({ content, onChange, readOnly }: SectionFormProps<RiskMatrixContent>) {
  function updateRow(index: number, patch: Partial<RiskRow>) {
    onChange((prev) => ({ rows: prev.rows.map((r, i) => (i === index ? { ...r, ...patch } : r)) }));
  }
  function addRow() {
    onChange((prev) => ({ rows: [...prev.rows, { ...EMPTY_ROW }] }));
  }
  function removeRow(index: number) {
    onChange((prev) => ({ rows: prev.rows.filter((_, i) => i !== index) }));
  }

  return (
    <div className="space-y-3">
      <Table>
        <TableHeader>
          <TableRow>
            <TableHead className="min-w-[160px]">Catégorie de risque</TableHead>
            <TableHead className="min-w-[110px]">Présence</TableHead>
            <TableHead className="min-w-[200px]">Quels risques</TableHead>
            <TableHead className="min-w-[140px]">Niveau N</TableHead>
            <TableHead className="min-w-[180px]">Impact domaines</TableHead>
            <TableHead className="min-w-[140px]">Quotation Q</TableHead>
            <TableHead className="min-w-[140px]">Criticité</TableHead>
            <TableHead className="min-w-[200px]">Actions de mitigation</TableHead>
            {!readOnly && <TableHead className="w-10" />}
          </TableRow>
        </TableHeader>
        <TableBody>
          {content.rows.map((row, index) => (
            <TableRow key={index}>
              <TableCell><EditableCell value={row.category} onChange={(v) => updateRow(index, { category: v })} readOnly={readOnly} /></TableCell>
              <TableCell>
                <NativeSelect
                  cellStyle
                  value={row.present ? "OUI" : "NON"}
                  disabled={readOnly}
                  onChange={(e) => updateRow(index, { present: e.target.value === "OUI" })}
                >
                  <option value="NON">Non</option>
                  <option value="OUI">Oui</option>
                </NativeSelect>
              </TableCell>
              <TableCell><EditableCell value={row.riskDetails} onChange={(v) => updateRow(index, { riskDetails: v })} readOnly={readOnly} multiline /></TableCell>
              <TableCell>
                <NativeSelect cellStyle value={row.levelN} disabled={readOnly} onChange={(e) => updateRow(index, { levelN: Number(e.target.value) })}>
                  <option value={3}>Élevé (3)</option>
                  <option value={2}>Moyen (2)</option>
                  <option value={1}>Faible (1)</option>
                </NativeSelect>
              </TableCell>
              <TableCell><EditableCell value={row.impactAreas} onChange={(v) => updateRow(index, { impactAreas: v })} readOnly={readOnly} multiline /></TableCell>
              <TableCell>
                <NativeSelect cellStyle value={row.quotationQ} disabled={readOnly} onChange={(e) => updateRow(index, { quotationQ: Number(e.target.value) })}>
                  <option value={3}>Élevé (3)</option>
                  <option value={2}>Moyen (2)</option>
                  <option value={1}>Faible (1)</option>
                </NativeSelect>
              </TableCell>
              <TableCell>
                {row.criticalityLabel ? <CriticalityBadge label={row.criticalityLabel} /> : "—"}
              </TableCell>
              <TableCell><EditableCell value={row.mitigationActions} onChange={(v) => updateRow(index, { mitigationActions: v })} readOnly={readOnly} multiline /></TableCell>
              {!readOnly && (
                <TableCell>
                  <RemoveRowButton onConfirm={() => removeRow(index)} />
                </TableCell>
              )}
            </TableRow>
          ))}
          {content.rows.length === 0 && (
            <TableRow>
              <TableCell colSpan={9} className="text-center text-slate-400 py-8">Aucun risque renseigné</TableCell>
            </TableRow>
          )}
        </TableBody>
      </Table>
      {!readOnly && <AddRowButton onAdd={addRow} label="Ajouter un risque" />}
    </div>
  );
}
