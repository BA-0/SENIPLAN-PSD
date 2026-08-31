import { apiClient } from "@/lib/api-client";
import type { GroupCycleSectionContentDto, GroupCycleSummaryDto, MyDashboardDto } from "@/types/api";
import type { SectionContentResponse, SectionStatusSummary } from "@/types/common";

export async function getMyDashboard(): Promise<MyDashboardDto> {
  const { data } = await apiClient.get<MyDashboardDto>("/me/dashboard");
  return data;
}

export async function listMySections(): Promise<SectionStatusSummary[]> {
  const { data } = await apiClient.get<SectionStatusSummary[]>("/me/sections");
  return data;
}

export async function getMySectionContent<T>(code: string): Promise<SectionContentResponse<T>> {
  const { data } = await apiClient.get<SectionContentResponse<T>>(`/me/sections/${code}`);
  return data;
}

export async function saveMySectionDraft<T>(code: string, content: T): Promise<SectionContentResponse<T>> {
  const { data } = await apiClient.put<SectionContentResponse<T>>(`/me/sections/${code}/draft`, { content });
  return data;
}

export async function submitMySection<T>(code: string): Promise<SectionContentResponse<T>> {
  const { data } = await apiClient.post<SectionContentResponse<T>>(`/me/sections/${code}/submit`);
  return data;
}

export async function listMyCycles(): Promise<GroupCycleSummaryDto[]> {
  const { data } = await apiClient.get<GroupCycleSummaryDto[]>("/me/cycles");
  return data;
}

export async function getMyCycleSections(cycleNumber: number): Promise<SectionStatusSummary[]> {
  const { data } = await apiClient.get<SectionStatusSummary[]>(`/me/cycles/${cycleNumber}/sections`);
  return data;
}

export async function getMyCycleSectionContent<T>(
  cycleNumber: number,
  code: string
): Promise<GroupCycleSectionContentDto<T>> {
  const { data } = await apiClient.get<GroupCycleSectionContentDto<T>>(`/me/cycles/${cycleNumber}/sections/${code}`);
  return data;
}
