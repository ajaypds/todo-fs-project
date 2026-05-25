package com.example.todo.config;

import com.example.todo.auth.security.JwtService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.*;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.*;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class WebSocketAuthInterceptor implements ChannelInterceptor {

    private final JwtService jwtService;

    @Override
    public Message<?> preSend(Message<?> message, MessageChannel channel) {

        StompHeaderAccessor accessor = MessageHeaderAccessor
                        .getAccessor(message, StompHeaderAccessor.class);

        if (accessor != null && StompCommand.CONNECT.equals(accessor.getCommand())) {

            String authHeader = accessor.getFirstNativeHeader("Authorization");

            if (authHeader != null && authHeader.startsWith("Bearer ")) {

                String token = authHeader.substring(7);

                String email = jwtService.extractUsername(token);

                if(email == null || !jwtService.isTokenValid(token)) {
                    log.info("WebSocket connection rejected: Invalid JWT token / Email extraction failed");
                    throw new IllegalArgumentException("Invalid JWT token");
                }else{
                    log.info("WebSocket connection accepted for user: {}", email);
                }

                accessor.setUser(() -> email);
            }
        }

        return message;
    }
}