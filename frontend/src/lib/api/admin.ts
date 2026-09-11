import { apiClient } from "@/lib/api-client";
import type {
  ActivityEntryDto,
  AdminDashboardDto,
  GroupCycleSectionContentDto,
  GroupCycleSummaryDto,
  MatrixCellDto,
  SubmissionSummaryDto,
} from "@/types/api";
import type {
  Role,
  SectionContentResponse,
  SectionRevisionContentResponse,
  SectionRevisionSummaryDto,
  SectionStatusSummary,
} from "@/types/common";

export async function getAdminDashboard(): Promise<AdminDashboardDto> {
  const { data } = await apiClient.get<AdminDashboardDto>("/admin/dashboard");
  return data;
}

export async function getAdminMatrix(): Promise<MatrixCellDto[]> {
  const { data } = await apiClient.get<MatrixCellDto[]>("/admin/matrix");
  return data;
}

export async function getAdminActivity(limit = 50): Promise<ActivityEntryDto[]> {
  const { data } = await apiClient.get<ActivityEntryDto[]>("/admin/activity", { params: { limit } });
  return data;
}

export async function getAdminSubmissions(): Promise<SubmissionSummaryDto[]> {
  const { data } = await apiClient.get<SubmissionSummaryDto[]>("/admin/submissions");
  return data;
}

export async function getSectionHistory(groupId: number, code: string): Promise<SectionRevisionSummaryDto[]> {
  const { data } = await apiClient.get<SectionRevisionSummaryDto[]>(`/admin/groups/${groupId}/sections/${code}/history`);
  return data;
}

export async function getSectionHistoryContent<T>(
  groupId: number,
  code: string,
  version: number
): Promise<SectionRevisionContentResponse<T>> {
  const { data } = await apiClient.get<SectionRevisionContentResponse<T>>(
    `/admin/groups/${groupId}/sections/${code}/history/${version}`
  );
  return data;
}

export async function listGroupSections(groupId: number): Promise<SectionStatusSummary[]> {
  const { data } = await apiClient.get<SectionStatusSummary[]>(`/admin/groups/${groupId}/sections`);
  return data;
}

export async function getGroupSectionContent<T>(groupId: number, code: string): Promise<SectionContentResponse<T>> {
  const { data } = await apiClient.get<SectionContentResponse<T>>(`/admin/groups/${groupId}/sections/${code}`);
  return data;
}

export async function reviewSection<T>(
  groupId: number,
  code: string,
  decision: "VALIDATE" | "REQUEST_REVISION" | "RETURN_TO_GROUP",
  comment?: string
): Promise<SectionContentResponse<T>> {
  const { data } = await apiClient.post<SectionContentResponse<T>>(
    `/admin/groups/${groupId}/sections/${code}/review`,
    { decision, comment }
  );
  return data;
}

/**
 * Valide d'un coup les sections soumises d'une direction. Le serveur ignore celles qui
 * ne sont pas au statut « Soumis », l'appel est donc sans effet s'il est rejoue.
 */
export async function validateAllSubmitted(groupId: number, comment?: string): Promise<{ validatedCount: number }> {
  const { data } = await apiClient.post<{ validatedCount: number }>(
    `/admin/groups/${groupId}/sections/validate-all`,
    { decision: "VALIDATE", comment }
  );
  return data;
}

/**
 * Second niveau de validation, reserve au DG : une section validee par le comite de pilotage
 * n'entre dans les documents consolides qu'une fois approuvee ici. Un refus la renvoie en
 * revision cote direction.
 */
export async function dgReviewSection<T>(
  groupId: number,
  code: string,
  decision: "APPROVE" | "REJECT",
  comment?: string
): Promise<SectionContentResponse<T>> {
  const { data } = await apiClient.post<SectionContentResponse<T>>(
    `/admin/groups/${groupId}/sections/${code}/dg-approval`,
    { decision, comment }
  );
  return data;
}

/**
 * Approuve d'un coup les sections validees d'une direction qui attendent encore le DG. Le
 * serveur ignore celles qui ne sont pas validees ou deja approuvees : l'appel est sans effet
 * s'il est rejoue.
 */
export async function dgApproveAllValidated(groupId: number, comment?: string): Promise<{ approvedCount: number }> {
  const { data } = await apiClient.post<{ approvedCount: number }>(
    `/admin/groups/${groupId}/sections/dg-approve-all`,
    { decision: "APPROVE", comment }
  );
  return data;
}

/** Une section designee pour l'arbitrage du DG, hors de tout contexte de direction. */
export interface DgSectionTarget {
  groupId: number;
  sectionCode: string;
}

/**
 * Approbation par lot : les sections cochees par le DG dans la liste des soumissions, toutes
 * directions confondues. Le serveur ignore celles qui ne sont pas validees ou deja approuvees,
 * et renvoie ce qui a reellement change.
 */
export async function dgApproveSelection(
  targets: DgSectionTarget[],
  comment?: string
): Promise<{ approvedCount: number }> {
  const { data } = await apiClient.post<{ approvedCount: number }>("/admin/dg-approvals/selection", {
    targets,
    comment,
  });
  return data;
}

/**
 * Approuve d'un coup tout ce qui attend encore l'arbitrage du DG, toutes directions confondues :
 * le geste de fin de campagne, quand le comite de pilotage a fini de valider.
 */
export async function dgApproveAllPending(comment?: string): Promise<{ approvedCount: number }> {
  const { data } = await apiClient.post<{ approvedCount: number }>("/admin/dg-approvals/all", {
    decision: "APPROVE",
    comment,
  });
  return data;
}

export interface UserAccount {
  id: number;
  username: string;
  fullName: string;
  role: Role;
  roleLabel: string;
  groupId: number | null;
  groupName: string | null;
  enabled: boolean;
  /** Le compte n'a pas encore remplacé le mot de passe qu'on lui a remis. */
  mustChangePassword: boolean;
  lastLoginAt: string | null;
  /** Renseigné seulement en réponse à une création : le mot de passe ne s'affiche qu'une fois. */
  temporaryPassword?: string;
}

export interface CreateUserAccountPayload {
  username: string;
  fullName: string;
  role: Role;
  /** Direction de rattachement : exigée pour un chef de groupe, refusée pour les autres rôles. */
  groupId?: number;
  /** Laissé vide, le serveur génère un mot de passe et le renvoie une seule fois. */
  password?: string;
}

/** Tous les comptes, chefs de groupe compris : la seule vue d'ensemble des accès. */
export async function listUserAccounts(): Promise<UserAccount[]> {
  const { data } = await apiClient.get<UserAccount[]>("/admin/users");
  return data;
}

export async function createUserAccount(payload: CreateUserAccountPayload): Promise<UserAccount> {
  const { data } = await apiClient.post<UserAccount>("/admin/users", payload);
  return data;
}

export async function resetUserPassword(userId: number): Promise<{ username: string; temporaryPassword: string }> {
  const { data } = await apiClient.post<{ username: string; temporaryPassword: string }>(
    `/admin/users/${userId}/reset-password`
  );
  return data;
}

/** Mot de passe inchangé ; les sessions ouvertes du compte se ferment, il se reconnecte avec le nouvel identifiant. */
export async function updateUserUsername(userId: number, username: string): Promise<UserAccount> {
  const { data } = await apiClient.patch<UserAccount>(`/admin/users/${userId}/username`, { username });
  return data;
}

export async function adminUpdateSectionContent<T>(
  groupId: number,
  code: string,
  content: T
): Promise<SectionContentResponse<T>> {
  const { data } = await apiClient.put<SectionContentResponse<T>>(
    `/admin/groups/${groupId}/sections/${code}/content`,
    content
  );
  return data;
}

export async function resetGroupSection<T>(groupId: number, code: string): Promise<SectionContentResponse<T>> {
  const { data } = await apiClient.delete<SectionContentResponse<T>>(`/admin/groups/${groupId}/sections/${code}`);
  return data;
}

export async function compareSection<T>(sectionCode: string, groupIds: number[]): Promise<SectionContentResponse<T>[]> {
  const { data } = await apiClient.get<SectionContentResponse<T>[]>("/admin/compare", {
    params: { sectionCode, groupIds: groupIds.join(",") },
  });
  return data;
}

export async function startNewCycle(groupId: number): Promise<GroupCycleSummaryDto> {
  const { data } = await apiClient.post<GroupCycleSummaryDto>(`/admin/groups/${groupId}/cycles/new`);
  return data;
}

export async function listGroupCycles(groupId: number): Promise<GroupCycleSummaryDto[]> {
  const { data } = await apiClient.get<GroupCycleSummaryDto[]>(`/admin/groups/${groupId}/cycles`);
  return data;
}

export async function getGroupCycleSections(groupId: number, cycleNumber: number): Promise<SectionStatusSummary[]> {
  const { data } = await apiClient.get<SectionStatusSummary[]>(
    `/admin/groups/${groupId}/cycles/${cycleNumber}/sections`
  );
  return data;
}

export async function getGroupCycleSectionContent<T>(
  groupId: number,
  cycleNumber: number,
  code: string
): Promise<GroupCycleSectionContentDto<T>> {
  const { data } = await apiClient.get<GroupCycleSectionContentDto<T>>(
    `/admin/groups/${groupId}/cycles/${cycleNumber}/sections/${code}`
  );
  return data;
}
