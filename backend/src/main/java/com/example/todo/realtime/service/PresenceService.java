package com.example.todo.realtime.service;

import lombok.Getter;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@Getter
@Service
public class PresenceService {

    // userId -> Set of active STOMP sessionIds
    private final Map<UUID, Set<String>> userSessions = new ConcurrentHashMap<>();

    public int sessionConnected(UUID userId, String sessionId) {
        if (userId == null || sessionId == null) {
            return 0;
        }
        Set<String> sessions = userSessions.computeIfAbsent(userId, k -> ConcurrentHashMap.newKeySet());
        sessions.add(sessionId);
        return sessions.size();
    }

    public int sessionDisconnected(UUID userId, String sessionId) {
        if (userId == null) {
            return 0;
        }
        Set<String> sessions = userSessions.get(userId);
        if (sessions != null) {
            if (sessionId != null) {
                sessions.remove(sessionId);
            }
            if (sessions.isEmpty()) {
                userSessions.remove(userId);
                return 0;
            }
            return sessions.size();
        }
        return 0;
    }

    public int getActiveSessionCount(UUID userId) {
        if (userId == null) {
            return 0;
        }
        Set<String> sessions = userSessions.get(userId);
        return sessions != null ? sessions.size() : 0;
    }

    public boolean isUserOnline(UUID userId) {
        return getActiveSessionCount(userId) > 0;
    }
}