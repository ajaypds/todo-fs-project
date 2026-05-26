package com.example.todo.activity.controller;

import com.example.todo.activity.dto.ActivityResponse;
import com.example.todo.activity.service.ActivityService;
import com.example.todo.auth.security.CustomUserDetails;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import java.util.List;

@RestController
@RequestMapping("/api/v1/activities")
@RequiredArgsConstructor
public class ActivityController {

    private final ActivityService service;

    @GetMapping
    public List<ActivityResponse> getActivities(@AuthenticationPrincipal CustomUserDetails userDetails) {
        return service.getActivities(userDetails.getUserId());
    }
}