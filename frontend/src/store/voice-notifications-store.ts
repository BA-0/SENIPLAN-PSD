import { create } from "zustand";
import { persist } from "zustand/middleware";

interface VoiceNotificationsState {
  enabled: boolean;
  toggle: () => void;
}

/** Preference utilisateur (persistee) pour les annonces vocales temps reel. */
export const useVoiceNotificationsStore = create<VoiceNotificationsState>()(
  persist(
    (set) => ({
      enabled: true,
      toggle: () => set((state) => ({ enabled: !state.enabled })),
    }),
    { name: "senico-voice-notifications" }
  )
);
