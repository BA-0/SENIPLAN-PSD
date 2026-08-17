import type { SectionType } from "@/types/common";

/**
 * Mirrors the backend's strict-mode checks in SectionContentValidator that require
 * non-empty user input (row-based sections with an empty default, and SWOT). Other
 * section types always pass strict validation because their default content is
 * pre-filled, so they're intentionally not checked here.
 */
export function getSubmitBlockedReason(type: SectionType, content: unknown): string | null {
  if (!content || typeof content !== "object") return null;
  const c = content as Record<string, unknown>;

  switch (type) {
    case "STAKEHOLDERS":
      return Array.isArray(c.rows) && c.rows.length === 0
        ? "Ajoutez au moins une partie prenante avant de soumettre."
        : null;
    case "INDICATOR_SHEET":
      return Array.isArray(c.rows) && c.rows.length === 0
        ? "Ajoutez au moins un indicateur avant de soumettre."
        : null;
    case "RISK_MATRIX":
      return Array.isArray(c.rows) && c.rows.length === 0
        ? "Ajoutez au moins un risque avant de soumettre."
        : null;
    case "SWOT": {
      const quadrants = ["strengths", "weaknesses", "opportunities", "threats"] as const;
      const anyFilled = quadrants.some((k) => Array.isArray(c[k]) && (c[k] as unknown[]).length > 0);
      return anyFilled ? null : "Ajoutez au moins un élément dans une des quatre catégories avant de soumettre.";
    }
    default:
      return null;
  }
}
