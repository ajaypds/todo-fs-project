package com.example.todo.ai.controller;

import com.example.todo.ai.dto.*;
import com.example.todo.ai.service.AiTaskService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/ai")
@RequiredArgsConstructor
public class AiTaskController {

    private final AiTaskService aiTaskService;

    @PostMapping("/parse-task")
    public ParsedTaskResponse
    parseTask(@RequestBody ParseTaskRequest request) {
        return aiTaskService.parseTask(request.getInput());
    }

    @PostMapping("/productivity-coach")
    public ProductivityInsightResponse
    productivityCoach(@RequestBody ProductivityInsightRequest request) {
        return aiTaskService.generateInsights(request);
    }
}