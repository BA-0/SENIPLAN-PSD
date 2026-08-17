package com.senico.diagnostic.service;

import com.senico.diagnostic.dto.realtime.ActivityEvent;
import com.senico.diagnostic.dto.realtime.PresenceEvent;
import com.senico.diagnostic.dto.realtime.SectionProgressEvent;
import lombok.RequiredArgsConstructor;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;

/**
 * Diffuse les evenements temps reel au dashboard admin et aux groupes (STOMP over SockJS).
 * Topics : /topic/admin/progress, /topic/admin/activity, /topic/admin/presence, /topic/group/{groupId}/activity
 */
@Service
@RequiredArgsConstructor
public class RealtimeEventPublisher {

    private final SimpMessagingTemplate messagingTemplate;

    public void publishProgress(SectionProgressEvent event) {
        messagingTemplate.convertAndSend("/topic/admin/progress", event);
    }

    public void publishActivity(ActivityEvent event) {
        messagingTemplate.convertAndSend("/topic/admin/activity", event);
        if (event.groupId() != null) {
            messagingTemplate.convertAndSend("/topic/group/" + event.groupId() + "/activity", event);
        }
    }

    public void publishPresence(PresenceEvent event) {
        messagingTemplate.convertAndSend("/topic/admin/presence", event);
    }
}
