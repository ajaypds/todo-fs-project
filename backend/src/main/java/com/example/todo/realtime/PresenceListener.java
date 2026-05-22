package com.example.todo.realtime;

import lombok.RequiredArgsConstructor;
import org.springframework.context.event.EventListener;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.messaging.*;

@Component
@RequiredArgsConstructor
public class PresenceListener {

    private final
    SimpMessagingTemplate messagingTemplate;

    @EventListener
    public void handleConnect(SessionConnectEvent event) {

        PresenceEvent payload = PresenceEvent
                        .builder()
                        .type("USER_CONNECTED")
                        .username("Someone")
                        .build();

        messagingTemplate.convertAndSend("/topic/presence", payload);
    }
}