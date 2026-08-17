import { create } from "zustand";

interface ConnectionState {
  connected: boolean;
  setConnected: (connected: boolean) => void;
}

/** Etat de la connexion WebSocket temps reel, alimente par useRealtimeAdmin. */
export const useConnectionStore = create<ConnectionState>((set) => ({
  connected: false,
  setConnected: (connected) => set({ connected }),
}));
