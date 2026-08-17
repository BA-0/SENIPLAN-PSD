package com.senico.diagnostic.websocket;

import com.senico.diagnostic.dto.realtime.PresenceEvent;
import com.senico.diagnostic.service.RealtimeEventPublisher;
import lombok.RequiredArgsConstructor;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.stereotype.Controller;

/**
 * Recoit les signaux de presence "en train de saisir" depuis les clients chefs de groupe
 * (STOMP destination /app/presence) et les rediffuse au dashboard admin.
 */
@Controller
@RequiredArgsConstructor
public class PresenceController {

    private final RealtimeEventPublisher realtimeEventPublisher;

    @MessageMapping("/presence")
    public void onPresence(PresenceEvent event) {
        realtimeEventPublisher.publishPresence(event);
    }
}
