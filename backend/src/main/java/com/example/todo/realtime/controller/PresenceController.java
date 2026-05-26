package com.example.todo.realtime.controller;

import com.example.todo.realtime.service.PresenceService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import java.util.Set;

@RestController
@RequestMapping("/api/v1/presence")
@RequiredArgsConstructor
public class PresenceController {

    private final PresenceService presenceService;

    @GetMapping
    public Set<String> getOnlineUsers() {

        return presenceService.getOnlineUsers();
    }
}