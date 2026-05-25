package com.example.todo.realtime;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.messaging.*;

@Slf4j
@Component
@RequiredArgsConstructor
public class PresenceListener {

    private final
    SimpMessagingTemplate messagingTemplate;

    @EventListener
    public void handleConnect(SessionConnectEvent event) {

        log.info("User connected: " + event.getUser().getName());

        PresenceEvent payload = PresenceEvent
                        .builder()
                        .type("USER_CONNECTED")
                        .username(event.getUser().getName())
                        .build();

        messagingTemplate.convertAndSend("/topic/presence", payload);
    }
}