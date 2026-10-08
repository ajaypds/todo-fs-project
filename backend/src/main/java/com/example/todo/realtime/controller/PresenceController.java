package com.example.todo.realtime.controller;

import com.example.todo.auth.security.CustomUserDetails;
import com.example.todo.realtime.dto.UserPresenceResponse;
import com.example.todo.realtime.service.PresenceService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/presence")
@RequiredArgsConstructor
public class PresenceController {

    private final PresenceService presenceService;

    @GetMapping
    public UserPresenceResponse getPresence(@AuthenticationPrincipal CustomUserDetails userDetails) {
        int sessions = presenceService.getActiveSessionCount(userDetails.getUserId());
        return UserPresenceResponse.builder()
                .userId(userDetails.getUserId().toString())
                .username(userDetails.getUsername())
                .activeSessions(sessions)
                .online(sessions > 0)
                .build();
    }
}