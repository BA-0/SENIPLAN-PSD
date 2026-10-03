import { apiClient } from "@/lib/api-client";
import type { NoteBlock, SynthesisNote } from "@/types/synthesis-note";

/** La note telle que ses exports la rendront : la version corrigée s'il y en a une. */
export async function getSynthesisNote(): Promise<SynthesisNote> {
  const { data } = await apiClient.get<SynthesisNote>("/synthesis-note");
  return data;
}

/**
 * Enregistre les blocs modifiés depuis la version `baseVersion` : le serveur les fusionne avec les
 * corrections enregistrées entre-temps par les autres postes, et renvoie la note qui en résulte.
 * La note pèse plusieurs mégaoctets : on n'en renvoie jamais que les blocs modifiés.
 */
export async function saveSynthesisNote(
  baseVersion: string,
  changes: { index: number; block: NoteBlock }[],
  clientId: string
): Promise<SynthesisNote> {
  const { data } = await apiClient.put<SynthesisNote>("/synthesis-note", { baseVersion, changes, clientId });
  return data;
}

/** Abandonne les corrections : les exports reprennent le document généré, à jour. */
export async function resetSynthesisNote(clientId: string): Promise<SynthesisNote> {
  const { data } = await apiClient.delete<SynthesisNote>("/synthesis-note", { params: { clientId } });
  return data;
}
