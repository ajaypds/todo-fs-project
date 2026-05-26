package com.example.todo.activity.controller;

import com.example.todo.activity.dto.ActivityResponse;
import com.example.todo.activity.service.ActivityService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import java.util.List;

@RestController
@RequestMapping("/api/v1/activities")
@RequiredArgsConstructor
public class ActivityController {

    private final ActivityService service;

    @GetMapping
    public List<ActivityResponse> getActivities() {
        return service.getActivities();
    }
}