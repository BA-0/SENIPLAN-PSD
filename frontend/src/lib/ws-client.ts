import { Client } from "@stomp/stompjs";
import SockJS from "sockjs-client";
import { useNetworkPauseStore } from "@/store/network-pause-store";

const WS_BASE_URL = process.env.NEXT_PUBLIC_WS_BASE_URL ?? "http://localhost:8080/ws";

let client: Client | null = null;

export function getStompClient(): Client {
  if (!client) {
    client = new Client({
      webSocketFactory: () => new SockJS(WS_BASE_URL) as unknown as WebSocket,
      // Chaque tentative SockJS envoie plusieurs requetes : toutes les 5 s, un serveur injoignable
      // suffisait a faire bloquer le poste par le pare-feu AWS.
      reconnectDelay: 15000,
      heartbeatIncoming: 10000,
      heartbeatOutgoing: 10000,
      // Pendant une pause pare-feu (cf. network-pause-store), la reconnexion attend qu'elle se termine.
      beforeConnect: async () => {
        const wait = useNetworkPauseStore.getState().pausedUntil - Date.now();
        if (wait > 0) await new Promise((resolve) => setTimeout(resolve, wait));
      },
    });
  }
  return client;
}
