import { create } from "zustand";

interface PresenceEntry {
  groupId: number;
  sectionCode: string | null;
  typing: boolean;
  updatedAt: number;
}

interface PresenceState {
  byGroup: Record<number, PresenceEntry>;
  setPresence: (groupId: number, sectionCode: string | null, typing: boolean) => void;
}

/** Presence "en train de saisir" par groupe, alimentee par le flux STOMP /topic/admin/presence. */
export const usePresenceStore = create<PresenceState>((set) => ({
  byGroup: {},
  setPresence: (groupId, sectionCode, typing) =>
    set((state) => ({
      byGroup: {
        ...state.byGroup,
        [groupId]: { groupId, sectionCode, typing, updatedAt: Date.now() },
      },
    })),
}));

const PRESENCE_STALE_MS = 8000;

export function useIsGroupTyping(groupId: number): boolean {
  const entry = usePresenceStore((s) => s.byGroup[groupId]);
  if (!entry || !entry.typing) return false;
  return Date.now() - entry.updatedAt < PRESENCE_STALE_MS;
}

/** Section en cours de saisie par la direction, ou null si elle ne saisit pas en ce moment. */
export function useGroupTypingSection(groupId: number): string | null {
  const entry = usePresenceStore((s) => s.byGroup[groupId]);
  if (!entry || !entry.typing) return null;
  return Date.now() - entry.updatedAt < PRESENCE_STALE_MS ? entry.sectionCode : null;
}
