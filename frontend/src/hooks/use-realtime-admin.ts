import { useEffect, useRef } from "react";
import { useQueryClient } from "@tanstack/react-query";
import { getStompClient } from "@/lib/ws-client";
import { usePresenceStore } from "@/store/presence-store";
import { useVoiceNotificationsStore } from "@/store/voice-notifications-store";
import { useConnectionStore } from "@/store/connection-store";
import { speak } from "@/lib/voice";
import type { ActivityEntryDto } from "@/types/api";

/**
 * Messages vocaux pour les actions marquantes du flux d'activite admin (les autres actions restent silencieuses).
 * Seule la soumission complete de toutes les sections d'une direction declenche une annonce
 * (SUBMIT_ALL) : les soumissions section par section restent silencieuses.
 */
const ADMIN_VOICE_MESSAGES: Partial<Record<string, (entry: ActivityEntryDto) => string>> = {
  SUBMIT_ALL: (e) => `${e.groupName ?? "Une direction"} a soumis la totalité de ses sections.`,
};

/**
 * Abonnement STOMP aux evenements temps reel du dashboard admin.
 * Invalide les caches React Query correspondants a chaque evenement recu.
 * Les annonces vocales sont reservees a la session projection (options.voice)
 * et ne se declenchent que lorsqu'une direction a soumis la totalite de ses sections.
 * options.onSubmitAll permet a l'appelant de reagir visuellement (bandeau, etc.)
 * a ce meme evenement, independamment du reglage vocal.
 * Le polling (refetchInterval: 15s) configure sur les queries sert de repli
 * si la connexion WebSocket est indisponible.
 */
export function useRealtimeAdmin(options?: { voice?: boolean; onSubmitAll?: (entry: ActivityEntryDto) => void }) {
  const voice = options?.voice ?? false;
  const queryClient = useQueryClient();
  const setPresence = usePresenceStore((s) => s.setPresence);
  const setConnected = useConnectionStore((s) => s.setConnected);
  const onSubmitAllRef = useRef(options?.onSubmitAll);
  onSubmitAllRef.current = options?.onSubmitAll;

  useEffect(() => {
    const client = getStompClient();

    client.onConnect = () => {
      setConnected(true);
      client.subscribe("/topic/admin/progress", () => {
        queryClient.invalidateQueries({ queryKey: ["admin", "dashboard"] });
        queryClient.invalidateQueries({ queryKey: ["admin", "matrix"] });
      });
      client.subscribe("/topic/admin/activity", (message) => {
        queryClient.invalidateQueries({ queryKey: ["admin", "activity"] });
        try {
          const entry = JSON.parse(message.body) as ActivityEntryDto;
          if (entry.action === "SUBMIT_ALL") onSubmitAllRef.current?.(entry);
          if (!voice || !useVoiceNotificationsStore.getState().enabled) return;
          const buildMessage = ADMIN_VOICE_MESSAGES[entry.action];
          if (buildMessage) speak(buildMessage(entry));
        } catch {
          // ignore malformed activity payloads
        }
      });
      client.subscribe("/topic/admin/presence", (message) => {
        try {
          const payload = JSON.parse(message.body) as {
            groupId: number;
            sectionCode: string | null;
            typing: boolean;
          };
          setPresence(payload.groupId, payload.sectionCode, payload.typing);
        } catch {
          // ignore malformed presence payloads
        }
      });
    };
    client.onDisconnect = () => setConnected(false);
    client.onWebSocketClose = () => setConnected(false);

    if (!client.active) {
      client.activate();
    }

    return () => {
      client.onConnect = () => {};
    };
  }, [queryClient, setPresence, setConnected, voice]);
}
