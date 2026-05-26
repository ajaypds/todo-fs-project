package com.example.todo.realtime.service;

import lombok.Getter;
import org.springframework.stereotype.Service;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

@Getter
@Service
public class PresenceService {

    private final Set<String> onlineUsers = ConcurrentHashMap.newKeySet();

    public void userConnected(String username) {

        onlineUsers.add(username);
    }

    public void userDisconnected(String username) {

        onlineUsers.remove(username);
    }

}