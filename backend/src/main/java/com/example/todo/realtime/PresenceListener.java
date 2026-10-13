package com.example.todo.realtime;

import com.example.todo.realtime.dto.PresenceEvent;
import com.example.todo.realtime.service.PresenceService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.messaging.simp.SimpMessageHeaderAccessor;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.messaging.*;

import java.util.Map;
import java.util.UUID;

@Slf4j
@Component
@RequiredArgsConstructor
public class PresenceListener {

    private final SimpMessagingTemplate messagingTemplate;
    private final PresenceService presenceService;

    @EventListener
    public void handleConnect(SessionConnectEvent event) {
        SimpMessageHeaderAccessor headerAccessor = SimpMessageHeaderAccessor.wrap(event.getMessage());
        Map<String, Object> sessionAttributes = headerAccessor.getSessionAttributes();

        String userIdStr = sessionAttributes != null ? (String) sessionAttributes.get("userId") : null;
        String username = event.getUser() != null ? event.getUser().getName() : (sessionAttributes != null ? (String) sessionAttributes.get("email") : null);
        String sessionId = headerAccessor.getSessionId();

        if (userIdStr != null) {
            try {
                UUID userId = UUID.fromString(userIdStr);
                int activeSessions = presenceService.sessionConnected(userId, sessionId);
                log.info("User connected: {} ({}), active sessions: {}", username, userId, activeSessions);

                PresenceEvent payload = PresenceEvent.builder()
                        .type("USER_CONNECTED")
                        .username(username)
                        .userId(userIdStr)
                        .activeSessions(activeSessions)
                        .online(true)
                        .build();

                messagingTemplate.convertAndSend("/topic/users/" + userIdStr + "/presence", payload);
            } catch (IllegalArgumentException e) {
                log.warn("Invalid UUID in session attributes: {}", userIdStr);
            }
        }
    }

    @EventListener
    public void handleDisconnect(SessionDisconnectEvent event) {
        SimpMessageHeaderAccessor headerAccessor = SimpMessageHeaderAccessor.wrap(event.getMessage());
        Map<String, Object> sessionAttributes = headerAccessor.getSessionAttributes();

        String userIdStr = sessionAttributes != null ? (String) sessionAttributes.get("userId") : null;
        String username = event.getUser() != null ? event.getUser().getName() : (sessionAttributes != null ? (String) sessionAttributes.get("email") : null);
        String sessionId = headerAccessor.getSessionId();

        if (userIdStr != null) {
            try {
                UUID userId = UUID.fromString(userIdStr);
                int activeSessions = presenceService.sessionDisconnected(userId, sessionId);
                log.info("User disconnected: {} ({}), remaining sessions: {}", username, userId, activeSessions);

                PresenceEvent payload = PresenceEvent.builder()
                        .type("USER_DISCONNECTED")
                        .username(username)
                        .userId(userIdStr)
                        .activeSessions(activeSessions)
                        .online(activeSessions > 0)
                        .build();

                messagingTemplate.convertAndSend("/topic/users/" + userIdStr + "/presence", payload);
            } catch (IllegalArgumentException e) {
                log.warn("Invalid UUID in session attributes: {}", userIdStr);
            }
        }
    }
}