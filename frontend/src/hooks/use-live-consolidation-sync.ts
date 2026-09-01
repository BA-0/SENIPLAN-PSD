import { useEffect } from "react";
import { useQueryClient } from "@tanstack/react-query";
import { getStompClient } from "@/lib/ws-client";
import { useConnectionStore } from "@/store/connection-store";

/**
 * Abonnement STOMP pour la vue "Consolidation en direct" : toute soumission,
 * validation ou modification de section par une direction fait repartir
 * /topic/admin/progress, ce qui invalide ici le cache de comparaison (prefixe
 * ["admin", "live", "compare"]) et rafraichit donc automatiquement la section
 * actuellement affichee. Le polling (refetchInterval) configure sur la query
 * sert de repli si la connexion WebSocket est indisponible.
 *
 * Suit la convention deja etablie par use-realtime-group.ts : on chaine le
 * gestionnaire onConnect existant plutot que de l'ecraser, pour coexister
 * proprement avec les autres abonnements STOMP de l'app (client partage).
 */
export function useLiveConsolidationSync() {
  const queryClient = useQueryClient();
  const setConnected = useConnectionStore((s) => s.setConnected);

  useEffect(() => {
    const client = getStompClient();

    const invalidate = () => {
      queryClient.invalidateQueries({ queryKey: ["admin", "live", "compare"] });
      queryClient.invalidateQueries({ queryKey: ["admin", "matrix"] });
    };

    const subscribeToProgress = () => client.subscribe("/topic/admin/progress", invalidate);

    let subscription = client.connected ? subscribeToProgress() : undefined;
    const previousOnConnect = client.onConnect;
    const previousOnDisconnect = client.onDisconnect;
    const previousOnWebSocketClose = client.onWebSocketClose;

    client.onConnect = (frame) => {
      previousOnConnect?.(frame);
      setConnected(true);
      subscription = subscribeToProgress();
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
    if (!client.active) {
      client.activate();
    }

    return () => {
      client.onConnect = previousOnConnect;
      client.onDisconnect = previousOnDisconnect;
      client.onWebSocketClose = previousOnWebSocketClose;
      subscription?.unsubscribe();
    };
  }, [queryClient, setConnected]);
}
