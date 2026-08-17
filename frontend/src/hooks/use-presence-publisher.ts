import { useCallback, useEffect, useRef } from "react";
import { getStompClient } from "@/lib/ws-client";

const TYPING_IDLE_MS = 4000;

/**
 * Publie les evenements de presence "en train de saisir" vers /app/presence,
 * consommes par le dashboard admin (cf. hooks/use-realtime-admin.ts).
 */
export function usePresencePublisher(params: {
  groupId: number | null;
  groupName: string | null;
  sectionId: number | null;
  sectionCode: string | null;
}) {
  const { groupId, groupName, sectionId, sectionCode } = params;
  const idleTimer = useRef<ReturnType<typeof setTimeout> | null>(null);
  const lastTypingState = useRef(false);

  const publish = useCallback(
    (typing: boolean) => {
      if (!groupId || !sectionId) return;
      const client = getStompClient();
      if (!client.active || !client.connected) return;

      client.publish({
        destination: "/app/presence",
        body: JSON.stringify({ groupId, groupName, sectionId, sectionCode, typing }),
      });
      lastTypingState.current = typing;
    },
    [groupId, groupName, sectionId, sectionCode]
  );

  const notifyTyping = useCallback(() => {
    if (!lastTypingState.current) {
      publish(true);
    }
    if (idleTimer.current) clearTimeout(idleTimer.current);
    idleTimer.current = setTimeout(() => publish(false), TYPING_IDLE_MS);
  }, [publish]);

  useEffect(() => {
    const client = getStompClient();
    if (!client.active) {
      client.activate();
    }
    return () => {
      if (idleTimer.current) clearTimeout(idleTimer.current);
      if (lastTypingState.current) publish(false);
    };
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [groupId, sectionId]);

  return { notifyTyping };
}
