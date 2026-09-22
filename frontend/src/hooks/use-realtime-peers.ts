import { useEffect } from "react";
import { useQueryClient } from "@tanstack/react-query";
import type { StompSubscription } from "@stomp/stompjs";
import { getStompClient } from "@/lib/ws-client";
import { usePresenceStore } from "@/store/presence-store";

/**
 * Suivi en direct d'autres directions, en lecture seule : chaque autosave, soumission ou
 * validation d'une direction suivie (/topic/group/{id}/progress) rafraichit les donnees
 * affichees, et sa presence « en train de saisir » (/topic/group/{id}/presence) alimente
 * le presence-store. Le refetchInterval des queries « peers » sert de repli sans WebSocket.
 */
export function useRealtimePeers(groupIds: readonly number[]) {
  const queryClient = useQueryClient();
  const setPresence = usePresenceStore((s) => s.setPresence);
  // Cle stable : le tableau est souvent recree a chaque rendu par l'appelant.
  const key = groupIds.join(",");

  useEffect(() => {
    const ids = key ? key.split(",").map(Number) : [];
    if (ids.length === 0) return;

    const client = getStompClient();

    const subscribeAll = (): StompSubscription[] =>
      ids.flatMap((groupId) => [
        client.subscribe(`/topic/group/${groupId}/progress`, (message) => {
          queryClient.invalidateQueries({ queryKey: ["peers", "groups"] });
          queryClient.invalidateQueries({ queryKey: ["peers", "sections", groupId] });
          try {
            const event = JSON.parse(message.body) as { sectionCode?: string | null };
            if (event.sectionCode) {
              queryClient.invalidateQueries({ queryKey: ["peers", "section", groupId, event.sectionCode] });
            }
          } catch {
            // ignore malformed progress payloads
          }
        }),
        client.subscribe(`/topic/group/${groupId}/presence`, (message) => {
          try {
            const payload = JSON.parse(message.body) as { sectionCode: string | null; typing: boolean };
            setPresence(groupId, payload.sectionCode, payload.typing);
          } catch {
            // ignore malformed presence payloads
          }
        }),
      ]);

    let subscriptions = client.connected ? subscribeAll() : [];
    const previousOnConnect = client.onConnect;
    client.onConnect = (frame) => {
      previousOnConnect?.(frame);
      subscriptions = subscribeAll();
    };

    if (!client.active) {
      client.activate();
    }

    return () => {
      client.onConnect = previousOnConnect;
      subscriptions.forEach((s) => s.unsubscribe());
    };
  }, [key, queryClient, setPresence]);
}
