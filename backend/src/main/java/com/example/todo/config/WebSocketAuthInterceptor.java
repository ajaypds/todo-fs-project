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
                String userId = jwtService.extractUserId(token);

                if (email == null || userId == null || !jwtService.isTokenValid(token)) {
                    log.info("WebSocket connection rejected: Invalid JWT token / User extraction failed");
                    throw new IllegalArgumentException("Invalid JWT token");
                } else {
                    log.info("WebSocket connection accepted for user: {} ({})", email, userId);
                }

                accessor.setUser(() -> email);
                if (accessor.getSessionAttributes() != null) {
                    accessor.getSessionAttributes().put("userId", userId);
                    accessor.getSessionAttributes().put("email", email);
                }
            }
        }

        if (accessor != null && StompCommand.SUBSCRIBE.equals(accessor.getCommand())) {
            String destination = accessor.getDestination();
            if (destination != null && destination.startsWith("/topic/users/")) {
                String[] parts = destination.split("/");
                if (parts.length >= 4) {
                    String targetUserId = parts[3];
                    String sessionUserId = accessor.getSessionAttributes() != null
                            ? (String) accessor.getSessionAttributes().get("userId")
                            : null;
                    if (sessionUserId == null || !sessionUserId.equalsIgnoreCase(targetUserId)) {
                        log.warn("Blocked unauthorized subscription to {} by session user {}", destination, sessionUserId);
                        throw new IllegalArgumentException("Unauthorized subscription to another user's channel");
                    }
                }
            }
        }

        return message;
    }
}