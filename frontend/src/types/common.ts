export type SectionStatus = "NOT_STARTED" | "IN_PROGRESS" | "SUBMITTED" | "VALIDATED" | "REVISION_REQUESTED";

export type SectionType =
  | "STAKEHOLDERS"
  | "RESOURCES_MATRIX"
  | "PESTEL"
  | "SWOT"
  | "TOWS_MATRIX"
  | "CAUSAL_ANALYSIS"
  | "INVENTORY"
  | "STRATEGIC_AXES"
  | "LOGICAL_FRAMEWORK"
  | "ACTION_PLAN"
  | "BUDGET"
  | "PERFORMANCE_FRAMEWORK"
  | "INDICATOR_SHEET"
  | "RISK_MATRIX"
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

export const SECTION_CODES = [
  "S01", "S02", "S03", "S04", "S05", "S06", "S07", "S08", "S09",
  "S10", "S11", "S12", "S13", "S14", "S15", "S16", "S17",
] as const;
