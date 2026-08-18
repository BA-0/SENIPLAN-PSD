import { apiClient } from "@/lib/api-client";
import type { ActivityEntryDto, AdminDashboardDto, MatrixCellDto, SubmissionSummaryDto } from "@/types/api";
import type {
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
  decision: "VALIDATE" | "REQUEST_REVISION",
  comment?: string
): Promise<SectionContentResponse<T>> {
  const { data } = await apiClient.post<SectionContentResponse<T>>(
    `/admin/groups/${groupId}/sections/${code}/review`,
    { decision, comment }
  );
  return data;
}

export async function compareSection<T>(sectionCode: string, groupIds: number[]): Promise<SectionContentResponse<T>[]> {
  const { data } = await apiClient.get<SectionContentResponse<T>[]>("/admin/compare", {
    params: { sectionCode, groupIds: groupIds.join(",") },
  });
  return data;
}
