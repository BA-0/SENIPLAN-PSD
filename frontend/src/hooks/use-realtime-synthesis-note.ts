import { useEffect, useRef } from "react";
import { getStompClient } from "@/lib/ws-client";
import { useConnectionStore } from "@/store/connection-store";

export interface SynthesisNoteEvent {
  clientId: string | null;
  version: string;
}

/**
 * Annonce de chaque enregistrement de la note de synthèse (/topic/synthesis-note), quel que soit
 * le poste : la page « Corriger la note de synthèse » s'en sert pour afficher aussitôt les
 * corrections des autres. Chaîne les gestionnaires du client STOMP partagé, comme les autres hooks
 * temps réel.
 */
export function useRealtimeSynthesisNote(onEvent: (event: SynthesisNoteEvent) => void) {
  const setConnected = useConnectionStore((s) => s.setConnected);
  const handler = useRef(onEvent);
  handler.current = onEvent;

  useEffect(() => {
    const client = getStompClient();
    const subscribe = () =>
      client.subscribe("/topic/synthesis-note", (message) => {
        try {
          handler.current(JSON.parse(message.body) as SynthesisNoteEvent);
        } catch {
          // Annonce illisible : la prochaine fera l'affaire.
        }
      });

    let subscription = client.connected ? subscribe() : undefined;
    const previousOnConnect = client.onConnect;
    const previousOnDisconnect = client.onDisconnect;
    const previousOnWebSocketClose = client.onWebSocketClose;

    client.onConnect = (frame) => {
      previousOnConnect?.(frame);
      setConnected(true);
      subscription = subscribe();
    };
    client.onDisconnect = (frame) => {
      previousOnDisconnect?.(frame);
      setConnected(false);
    };
    client.onWebSocketClose = (evt) => {
      previousOnWebSocketClose?.(evt);
      setConnected(false);
    };

    if (client.connected) setConnected(true);
    if (!client.active) client.activate();

    return () => {
      client.onConnect = previousOnConnect;
      client.onDisconnect = previousOnDisconnect;
      client.onWebSocketClose = previousOnWebSocketClose;
      subscription?.unsubscribe();
    };
  }, [setConnected]);
}
