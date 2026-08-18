import type { Role, SectionStatus } from "./common";

export interface AuthUser {
  id: number;
  username: string;
  fullName: string;
  role: Role;
  groupId: number | null;
  groupName: string | null;
}

export interface AuthResponse {
  accessToken: string;
  refreshToken: string;
  expiresIn: number;
  user: AuthUser;
}

export interface WorkGroupDto {
  id: number;
  name: string;
  description: string | null;
  enabled: boolean;
  leaderUserId: number | null;
  leaderUsername: string | null;
  leaderFullName: string | null;
  createdAt: string;
  completionPercent: number;
  sectionsSubmitted: number;
  sectionsValidated: number;
  lastActivityAt: string | null;
  temporaryPassword?: string | null;
}

export interface ResetPasswordResponse {
  username: string;
  temporaryPassword: string;
}

export interface AdminDashboardDto {
  totalGroups: number;
  activeGroups: number;
  globalCompletionPercent: number;
  sectionsSubmitted: number;
  sectionsValidated: number;
  sectionsRevisionRequested: number;
  activityToday: number;
  groups: GroupProgressDto[];
  sectionAdvancement: SectionAdvancementDto[];
}

export interface GroupProgressDto {
  groupId: number;
  groupName: string;
  leaderFullName: string | null;
  enabled: boolean;
  completionPercent: number;
  submitted: number;
  validated: number;
  lastActivityAt: string | null;
}

export interface SectionAdvancementDto {
  sectionId: number;
  code: string;
  title: string;
  order: number;
  groupsSubmittedOrValidated: number;
  totalGroups: number;
}

export interface MatrixCellDto {
  groupId: number;
  groupName: string;
  sectionId: number;
  sectionCode: string;
  status: SectionStatus;
}

export interface ActivityEntryDto {
  id: number;
  groupId: number | null;
  groupName: string | null;
  userFullName: string | null;
  action: string;
  sectionCode: string | null;
  sectionTitle: string | null;
  timestamp: string;
}

export interface MyDashboardDto {
  groupId: number;
  groupName: string;
  completionPercent: number;
  sectionsNotStarted: number;
  sectionsInProgress: number;
  sectionsSubmitted: number;
  sectionsValidated: number;
  sectionsRevisionRequested: number;
  checklist: import("./common").SectionStatusSummary[];
  nextSections: import("./common").SectionStatusSummary[];
  sectionsWithAdminComment: import("./common").SectionStatusSummary[];
}

export interface SubmissionSummaryDto {
  groupId: number;
  groupName: string;
  leaderFullName: string | null;
  sectionId: number;
  sectionCode: string;
  sectionTitle: string;
  sectionOrder: number;
  status: SectionStatus;
  version: number;
  submittedAt: string | null;
  validatedAt: string | null;
  lastActivityAt: string | null;
  adminComment: string | null;
}

export interface ApiErrorBody {
  timestamp?: string;
  status: number;
  message: string;
  fieldErrors?: Record<string, string>;
}
