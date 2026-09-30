import type { IndicatorCalculationType, IndicatorRow } from "@/types/sections";

/**
 * Calcul automatique de la fiche d'indicateurs (S13). Meme regle que le serveur
 * (DerivedFieldsService.applyIndicatorCalculations), qui la reapplique a la lecture et pour l'export :
 * ici elle ne sert qu'a afficher le resultat pendant la saisie, avant l'enregistrement.
 */
export interface CalculationTypeOption {
  value: IndicatorCalculationType;
  label: string;
  /** Libelles des deux valeurs A et B ; absents pour la somme et la moyenne, qui prennent une liste. */
  a?: string;
  b?: string;
}

export const CALCULATION_TYPES: CalculationTypeOption[] = [
  { value: "", label: "Pas de calcul" },
  { value: "RATIO_PERCENT", label: "Taux : A / B × 100", a: "Numérateur (A)", b: "Dénominateur (B)" },
  { value: "RATIO", label: "Ratio : A / B", a: "Numérateur (A)", b: "Dénominateur (B)" },
  { value: "GROWTH", label: "Évolution : (A − B) / B × 100", a: "Valeur de la période (A)", b: "Valeur de référence (B)" },
  { value: "DIFFERENCE", label: "Écart : A − B", a: "Valeur A", b: "Valeur B" },
  { value: "SUM", label: "Somme des valeurs" },
  { value: "AVERAGE", label: "Moyenne des valeurs" },
];

export function calculationTypeOption(type: string | undefined): CalculationTypeOption {
  return CALCULATION_TYPES.find((t) => t.value === (type ?? "")) ?? CALCULATION_TYPES[0];
}

export type CalculationOutcome =
  | { status: "none" }
  | { status: "incomplete" }
  | { status: "error"; message: string }
  | { status: "ok"; result: string; detail: string };

/** « 1 250,5 », « 12,3 » ou « -4 » : les espaces de milliers et la virgule decimale sont acceptes. */
export function parseIndicatorNumber(raw: string | undefined): number | null {
  const text = (raw ?? "").replace(/[\s  ]/g, "").replace(",", ".");
  if (!/^[-+]?\d+(\.\d+)?$/.test(text)) {
    return null;
  }
  return Number(text);
}

function formatNumber(value: number): string {
  return new Intl.NumberFormat("fr-FR", { maximumFractionDigits: 2 }).format(value).replace(/[  ]/g, " ");
}

function formatSigned(value: number): string {
  const rounded = Math.round(value * 100) / 100;
  return (rounded > 0 ? "+" : "") + formatNumber(rounded);
}

/** Resultat de l'indicateur, avec le detail du calcul pose sur les valeurs saisies (« 170 / 200 × 100 = 85 % »). */
export function computeIndicator(row: IndicatorRow): CalculationOutcome {
  const type = row.calculationType ?? "";
  if (!type) {
    return { status: "none" };
  }

  if (type === "SUM" || type === "AVERAGE") {
    const parts = (row.calculationValues ?? "").split(/[;\n]/).map((p) => p.trim()).filter(Boolean);
    if (parts.length === 0) {
      return { status: "incomplete" };
    }
    const numbers = parts.map(parseIndicatorNumber);
    if (numbers.some((n) => n === null)) {
      return { status: "error", message: "Une des valeurs n'est pas un nombre" };
    }
    const values = numbers as number[];
    const sum = values.reduce((acc, n) => acc + n, 0);
    const listed = values.map(formatNumber).join(" + ");
    if (type === "SUM") {
      const result = formatNumber(sum);
      return { status: "ok", result, detail: `${listed} = ${result}` };
    }
    const result = formatNumber(sum / values.length);
    return { status: "ok", result, detail: `(${listed}) / ${values.length} = ${result}` };
  }

  const aText = (row.valueA ?? "").trim();
  const bText = (row.valueB ?? "").trim();
  if (!aText || !bText) {
    return { status: "incomplete" };
  }
  const a = parseIndicatorNumber(aText);
  const b = parseIndicatorNumber(bText);
  if (a === null || b === null) {
    return { status: "error", message: "A et B doivent être des nombres" };
  }
  const fa = formatNumber(a);
  const fb = formatNumber(b);

  switch (type) {
    case "RATIO_PERCENT": {
      if (b === 0) return { status: "error", message: "Le dénominateur (B) ne peut pas être 0" };
      const result = `${formatNumber((a / b) * 100)} %`;
      return { status: "ok", result, detail: `${fa} / ${fb} × 100 = ${result}` };
    }
    case "RATIO": {
      if (b === 0) return { status: "error", message: "Le dénominateur (B) ne peut pas être 0" };
      const result = formatNumber(a / b);
      return { status: "ok", result, detail: `${fa} / ${fb} = ${result}` };
    }
    case "GROWTH": {
      if (b === 0) return { status: "error", message: "La valeur de référence (B) ne peut pas être 0" };
      const result = `${formatSigned(((a - b) / b) * 100)} %`;
      return { status: "ok", result, detail: `(${fa} − ${fb}) / ${fb} × 100 = ${result}` };
    }
    case "DIFFERENCE": {
      const result = formatSigned(a - b);
      return { status: "ok", result, detail: `${fa} − ${fb} = ${result}` };
    }
    default:
      return { status: "none" };
  }
}
