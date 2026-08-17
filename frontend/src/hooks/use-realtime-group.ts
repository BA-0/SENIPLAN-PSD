import { useEffect } from "react";
import { useQueryClient } from "@tanstack/react-query";
import { getStompClient } from "@/lib/ws-client";
import { useVoiceNotificationsStore } from "@/store/voice-notifications-store";
import { speak } from "@/lib/voice";
import type { ActivityEntryDto } from "@/types/api";

/** Messages vocaux pour un chef de groupe : retours de l'administrateur sur ses sections. */
const GROUP_VOICE_MESSAGES: Partial<Record<string, (entry: ActivityEntryDto) => string>> = {
  VALIDATE: (e) => `Bonne nouvelle : la section ${e.sectionTitle ?? e.sectionCode ?? ""} a été validée.`,
  REQUEST_REVISION: (e) =>
    `L'administrateur demande une révision sur la section ${e.sectionTitle ?? e.sectionCode ?? ""}.`,
};

/**
 * Abonnement STOMP aux evenements temps reel du groupe de l'utilisateur connecte
 * (retours de l'administrateur : validation, demande de revision).
 * Invalide les caches React Query correspondants et annonce vocalement l'evenement.
 */
export function useRealtimeGroup(groupId: number | null | undefined) {
  const queryClient = useQueryClient();

  useEffect(() => {
    if (!groupId) return;

    const client = getStompClient();
    const topic = `/topic/group/${groupId}/activity`;

    const subscribeToGroupTopic = () => {
      const subscription = client.subscribe(topic, (message) => {
        queryClient.invalidateQueries({ queryKey: ["me", "dashboard"] });
        queryClient.invalidateQueries({ queryKey: ["me", "sections", "nav"] });
        try {
          const entry = JSON.parse(message.body) as ActivityEntryDto;
          if (entry.sectionCode) {
            queryClient.invalidateQueries({ queryKey: ["me", "section", entry.sectionCode] });
          }
          if (!useVoiceNotificationsStore.getState().enabled) return;
          const buildMessage = GROUP_VOICE_MESSAGES[entry.action];
          if (buildMessage) speak(buildMessage(entry));
        } catch {
          // ignore malformed activity payloads
        }
      });
      return subscription;
    };

    let subscription = client.connected ? subscribeToGroupTopic() : undefined;
    const previousOnConnect = client.onConnect;
    client.onConnect = (frame) => {
      previousOnConnect?.(frame);
      subscription = subscribeToGroupTopic();
    };

    if (!client.active) {
      client.activate();
    }

    return () => {
      client.onConnect = previousOnConnect;
      subscription?.unsubscribe();
    };
  }, [groupId, queryClient]);
}
