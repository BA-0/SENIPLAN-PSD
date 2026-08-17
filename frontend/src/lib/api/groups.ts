import { apiClient } from "@/lib/api-client";
import type { ResetPasswordResponse, WorkGroupDto } from "@/types/api";

export interface CreateWorkGroupPayload {
  name: string;
  description?: string;
  leaderUsername: string;
  leaderFullName: string;
  leaderPassword?: string;
}

export interface UpdateWorkGroupPayload {
  name: string;
  description?: string;
  leaderFullName?: string;
  enabled?: boolean;
}

export async function listGroups(): Promise<WorkGroupDto[]> {
  const { data } = await apiClient.get<WorkGroupDto[]>("/groups");
  return data;
}

export async function getGroup(id: number): Promise<WorkGroupDto> {
  const { data } = await apiClient.get<WorkGroupDto>(`/groups/${id}`);
  return data;
}

export async function createGroup(payload: CreateWorkGroupPayload): Promise<WorkGroupDto> {
  const { data } = await apiClient.post<WorkGroupDto>("/groups", payload);
  return data;
}

export async function updateGroup(id: number, payload: UpdateWorkGroupPayload): Promise<WorkGroupDto> {
  const { data } = await apiClient.put<WorkGroupDto>(`/groups/${id}`, payload);
  return data;
}

export async function setGroupEnabled(id: number, enabled: boolean): Promise<void> {
  await apiClient.patch(`/groups/${id}/enabled`, { enabled });
}

export async function resetLeaderPassword(id: number): Promise<ResetPasswordResponse> {
  const { data } = await apiClient.post<ResetPasswordResponse>(`/groups/${id}/reset-password`);
  return data;
}
