import type { SectionType } from "@/types/common";

/** Message affiche quand un tableau a lignes est encore vide, par type de section. */
const EMPTY_ROWS_MESSAGES: Partial<Record<SectionType, string>> = {
  STAKEHOLDERS: "Ajoutez au moins une partie prenante avant de soumettre.",
  INDICATOR_SHEET: "Ajoutez au moins un indicateur avant de soumettre.",
  RISK_MATRIX: "Ajoutez au moins un risque avant de soumettre.",
  PERFORMANCE_REVIEW_2026: "Ajoutez au moins un indicateur au bilan avant de soumettre.",
  RESOURCES_MATRIX: "Ajoutez au moins une ressource avant de soumettre.",
  PESTEL: "Ajoutez au moins un facteur PESTEL avant de soumettre.",
  CAUSAL_ANALYSIS: "Ajoutez au moins une ligne à l'analyse causale avant de soumettre.",
  CONSTRAINTS_SYNTHESIS: "Ajoutez au moins un domaine d'activité avant de soumettre.",
  STAFF_EVOLUTION: "Ajoutez au moins une ligne d'effectifs avant de soumettre.",
  FINANCING_PLAN: "Ajoutez au moins une source de financement avant de soumettre.",
};

/**
 * Reprend les controles stricts de SectionContentValidator qui exigent une saisie : les
 * tableaux a lignes demarrent vides (DefaultSectionContentFactory), et le serveur refuse
 * d'en soumettre un sans aucune ligne. Les autres types ont un contenu par defaut qui
 * passe toujours la validation stricte.
 */
export function getSubmitBlockedReason(type: SectionType, content: unknown): string | null {
  if (!content || typeof content !== "object") return null;
  const c = content as Record<string, unknown>;

  const emptyRowsMessage = EMPTY_ROWS_MESSAGES[type];
  if (emptyRowsMessage && Array.isArray(c.rows) && c.rows.length === 0) {
    return emptyRowsMessage;
  }

  switch (type) {
    case "STAKEHOLDERS": {
      const rows = Array.isArray(c.rows) ? (c.rows as Record<string, unknown>[]) : [];
      const incomplete = rows.findIndex((row) => isBlank(row?.category) || isBlank(row?.scope));
      return incomplete >= 0
        ? `Renseignez la catégorie et la portée de la partie prenante (ligne ${incomplete + 1}) avant de soumettre.`
        : null;
    }
    case "SWOT": {
      const quadrants = ["strengths", "weaknesses", "opportunities", "threats"] as const;
      const anyFilled = quadrants.some((k) => Array.isArray(c[k]) && (c[k] as unknown[]).length > 0);
      return anyFilled ? null : "Ajoutez au moins un élément dans une des quatre catégories avant de soumettre.";
    }
    default:
      return null;
  }
}

function isBlank(value: unknown): boolean {
  return typeof value !== "string" || value.trim() === "";
}
