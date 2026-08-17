import { useEffect } from "react";
import { useQueryClient } from "@tanstack/react-query";
import { getStompClient } from "@/lib/ws-client";
import { usePresenceStore } from "@/store/presence-store";
import { useVoiceNotificationsStore } from "@/store/voice-notifications-store";
import { useConnectionStore } from "@/store/connection-store";
import { speak } from "@/lib/voice";
import type { ActivityEntryDto } from "@/types/api";

/** Messages vocaux pour les actions marquantes du flux d'activite admin (les autres actions restent silencieuses). */
const ADMIN_VOICE_MESSAGES: Partial<Record<string, (entry: ActivityEntryDto) => string>> = {
  SUBMIT: (e) => `${e.groupName ?? "Un groupe"} a soumis la section ${e.sectionTitle ?? e.sectionCode ?? ""}.`,
  VALIDATE: (e) => `Section ${e.sectionTitle ?? e.sectionCode ?? ""} validée pour ${e.groupName ?? "un groupe"}.`,
  REQUEST_REVISION: (e) =>
    `Révision demandée sur la section ${e.sectionTitle ?? e.sectionCode ?? ""} pour ${e.groupName ?? "un groupe"}.`,
};

/**
 * Abonnement STOMP aux evenements temps reel du dashboard admin.
 * Invalide les caches React Query correspondants a chaque evenement recu et
 * annonce vocalement les evenements marquants (soumission, validation, revision).
 * Le polling (refetchInterval: 15s) configure sur les queries sert de repli
 * si la connexion WebSocket est indisponible.
 */
export function useRealtimeAdmin() {
  const queryClient = useQueryClient();
  const setPresence = usePresenceStore((s) => s.setPresence);
  const setConnected = useConnectionStore((s) => s.setConnected);

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
        if (!useVoiceNotificationsStore.getState().enabled) return;
        try {
          const entry = JSON.parse(message.body) as ActivityEntryDto;
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
  }, [queryClient, setPresence, setConnected]);
}
