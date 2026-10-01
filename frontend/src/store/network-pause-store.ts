import { create } from "zustand";

/** Duree de la pause apres un blocage du pare-feu : le temps qu'il leve son interdiction. */
export const NETWORK_PAUSE_MS = 120_000;

interface NetworkPauseState {
  /** Horodatage (ms) jusqu'auquel l'application n'envoie plus de requetes ; 0 hors pause. */
  pausedUntil: number;
  pause: () => void;
  resume: () => void;
}

/**
 * Pause des echanges avec le serveur quand le pare-feu AWS en amont bloque le poste (403/429 en page
 * HTML). Continuer a interroger le serveur pendant le blocage le prolongerait : polling, autosave et
 * reconnexions temps reel attendent la fin de la pause (cf. api-client, ws-client).
 */
export const useNetworkPauseStore = create<NetworkPauseState>((set) => ({
  pausedUntil: 0,
  pause: () => set({ pausedUntil: Date.now() + NETWORK_PAUSE_MS }),
  resume: () => set({ pausedUntil: 0 }),
}));

export function isNetworkPaused(): boolean {
  return useNetworkPauseStore.getState().pausedUntil > Date.now();
}
