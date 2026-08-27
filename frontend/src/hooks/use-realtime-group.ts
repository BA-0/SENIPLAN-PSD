import { useEffect } from "react";
import { useQueryClient } from "@tanstack/react-query";
import { getStompClient } from "@/lib/ws-client";
import type { ActivityEntryDto } from "@/types/api";

/**
 * Abonnement STOMP aux evenements temps reel du groupe de l'utilisateur connecte
 * (retours de l'administrateur : validation, demande de revision).
 * Invalide les caches React Query correspondants.
 * Aucune annonce vocale ici : les notifications sonores sont reservees a la session projection.
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
