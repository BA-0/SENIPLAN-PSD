import { Client } from "@stomp/stompjs";
import SockJS from "sockjs-client";

const WS_BASE_URL = process.env.NEXT_PUBLIC_WS_BASE_URL ?? "http://localhost:8080/ws";

let client: Client | null = null;

export function getStompClient(): Client {
  if (!client) {
    client = new Client({
      webSocketFactory: () => new SockJS(WS_BASE_URL) as unknown as WebSocket,
      reconnectDelay: 5000,
      heartbeatIncoming: 10000,
      heartbeatOutgoing: 10000,
    });
  }
  return client;
}
