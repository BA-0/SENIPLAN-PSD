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
            <TableHead className="min-w-[110px]">Présence (Oui/Non)</TableHead>
            <TableHead className="min-w-[200px]">Quels risques (nature détaillée)</TableHead>
            <TableHead className="min-w-[140px]">Niveau de risque (Fréquence)</TableHead>
            <TableHead className="min-w-[180px]">Impact sur les domaines d&apos;activités</TableHead>
            <TableHead className="min-w-[140px]">Quotation (Gravité)</TableHead>
            <TableHead className="min-w-[140px]">Criticité (N × Q)</TableHead>
            <TableHead className="min-w-[200px]">Actions de mitigation ou de contingence</TableHead>
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
              <TableCell colSpan={9} className="text-center text-muted-foreground py-8">Aucun risque renseigné</TableCell>
            </TableRow>
          )}
        </TableBody>
      </Table>
      {!readOnly && <AddRowButton onAdd={addRow} label="Ajouter un risque" />}
      <RiskMethodology />
    </div>
  );
}

/** « Méthodologie d'évaluation », sous la matrice comme dans le canevas. */
function RiskMethodology() {
  const columns = [
    { title: "Niveau de risque (Fréquence)", items: ["Élevé = 3", "Moyen = 2", "Faible = 1"] },
    { title: "Quotation (Gravité) / impact (Q)", items: ["Élevé = 3 (impact majeur)", "Moyen = 2 (impact modéré)", "Faible = 1 (impact mineur)"] },
    {
      title: "Criticité = N × Q",
      items: [
        "6 à 9 : Criticité ÉLEVÉE — action prioritaire",
        "3 à 4 : Criticité MOYENNE — à surveiller",
        "1 à 2 : Criticité FAIBLE — sous contrôle",
      ],
    },
  ];
  return (
    <section className="space-y-2 pt-2">
      <h3>Méthodologie d&apos;évaluation</h3>
      <Table>
        <TableHeader>
          <TableRow>
            {columns.map((c) => (
              <TableHead key={c.title} className="w-1/3">
                {c.title}
              </TableHead>
            ))}
          </TableRow>
        </TableHeader>
        <TableBody>
          <TableRow className="hover:bg-transparent">
            {columns.map((c) => (
              <TableCell key={c.title} className="align-top text-[13px] leading-relaxed">
                {c.items.map((item) => (
                  <div key={item}>▪ {item}</div>
                ))}
              </TableCell>
            ))}
          </TableRow>
        </TableBody>
      </Table>
    </section>
  );
}
