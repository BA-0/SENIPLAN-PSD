import { apiClient } from "@/lib/api-client";
import type { NarrativeBlock } from "@/types/psd-narrative";

export async function listNarrativeBlocks(): Promise<NarrativeBlock[]> {
  const { data } = await apiClient.get<NarrativeBlock[]>("/admin/psd-narrative");
  return data;
}

export async function updateNarrativeBlock(key: string, content: string): Promise<NarrativeBlock> {
  const { data } = await apiClient.put<NarrativeBlock>(`/admin/psd-narrative/${key}`, { content });
  return data;
}
