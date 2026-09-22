import { apiClient } from "@/lib/api-client";
import type { PeerGroupDto } from "@/types/api";
import type { SectionContentResponse, SectionStatusSummary } from "@/types/common";

/**
 * Consultation croisee entre directions, en lecture seule (cf. PeerSectionController) :
 * aucune fonction d'ecriture ici, le serveur les refuserait de toute facon.
 */
export async function listPeerGroups(): Promise<PeerGroupDto[]> {
  const { data } = await apiClient.get<PeerGroupDto[]>("/peers/groups");
  return data;
}

export async function listPeerSections(groupId: number): Promise<SectionStatusSummary[]> {
  const { data } = await apiClient.get<SectionStatusSummary[]>(`/peers/groups/${groupId}/sections`);
  return data;
}

export async function getPeerSectionContent<T>(groupId: number, code: string): Promise<SectionContentResponse<T>> {
  const { data } = await apiClient.get<SectionContentResponse<T>>(`/peers/groups/${groupId}/sections/${code}`);
  return data;
}
