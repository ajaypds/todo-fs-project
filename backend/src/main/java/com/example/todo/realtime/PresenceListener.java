package com.example.todo.realtime;

import com.example.todo.realtime.dto.PresenceEvent;
import com.example.todo.realtime.service.PresenceService;
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

    private final SimpMessagingTemplate messagingTemplate;
    private final PresenceService presenceService;

    @EventListener
    public void handleConnect(SessionConnectEvent event) {

        if(event.getUser() == null){
            return;
        }
        log.info("User connected: " + event.getUser().getName());

        String username = event.getUser().getName();
        presenceService.userConnected(username);

        PresenceEvent payload = PresenceEvent
                        .builder()
                        .type("USER_CONNECTED")
                        .username(event.getUser().getName())
                        .build();

        messagingTemplate.convertAndSend("/topic/presence", payload);
    }

    @EventListener
    public void handleDisconnect(SessionDisconnectEvent event){
        if(event.getUser() == null){
            return;
        }

        log.info("User disconnected: " + event.getUser().getName());

        String username = event.getUser().getName();
        presenceService.userDisconnected(username);

        PresenceEvent payload = PresenceEvent
                                .builder()
                                .type("USER_DISCONNECTED")
                                .username(username)
                                .build();

        messagingTemplate.convertAndSend("/topic/presence", payload);

    }
}