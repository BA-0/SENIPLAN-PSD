export type SectionStatus = "NOT_STARTED" | "IN_PROGRESS" | "SUBMITTED" | "VALIDATED" | "REVISION_REQUESTED";

export type SectionType =
  | "STAKEHOLDERS"
  | "PERFORMANCE_REVIEW_2026"
  | "RESOURCES_MATRIX"
  | "PESTEL"
  | "RESOURCES_SYNTHESIS"
  | "SWOT"
  | "TOWS_MATRIX"
  | "CAUSAL_ANALYSIS"
  | "CONSTRAINTS_SYNTHESIS"
  | "INVENTORY"
  | "STRATEGIC_FRAMEWORK"
  | "STRATEGIC_AXES"
  | "LOGICAL_FRAMEWORK"
  | "LOGFRAME_SYNTHESIS"
  | "ACTION_PLAN"
  | "BUDGET"
  | "PERFORMANCE_FRAMEWORK"
  | "INDICATOR_SHEET"
  | "RISK_MATRIX"
  | "STAFF_EVOLUTION"
  | "FINANCING_PLAN"
  | "BUSINESS_PLAN"
  | "STRATEGIC_SUMMARY";

export type Role = "ADMIN" | "GROUP_LEADER";

export interface SectionStatusSummary {
  sectionId: number;
  code: string;
  title: string;
  order: number;
  status: SectionStatus;
  submittedAt: string | null;
  validatedAt: string | null;
  lastActivityAt: string | null;
  adminComment: string | null;
}

export interface SectionContentResponse<T = unknown> {
  sectionId: number;
  code: string;
  title: string;
  order: number;
  type: SectionType;
  groupId: number;
  groupName: string;
  status: SectionStatus;
  locked: boolean;
  content: T;
  version: number;
  updatedAt: string | null;
  submittedAt: string | null;
  validatedAt: string | null;
  adminComment: string | null;
  lastActivityAt: string | null;
}

export interface SectionRevisionSummaryDto {
  version: number;
  createdAt: string;
  createdByName: string | null;
  current: boolean;
}

export interface SectionRevisionContentResponse<T = unknown> {
  code: string;
  title: string;
  type: SectionType;
  version: number;
  createdAt: string;
  createdByName: string | null;
  content: T;
}

// Dans l'ordre d'affichage du canevas (sections.display_order, cf. migration V10).
export const SECTION_CODES = [
  "S01", "S01B", "S02", "S03", "S03B", "S04", "S05", "S06", "S06B", "S07",
  "S07B", "S08", "S09", "S09B", "S11", "S10", "S13", "S12", "S14", "S14B",
  "S15", "S16", "S17",
] as const;
