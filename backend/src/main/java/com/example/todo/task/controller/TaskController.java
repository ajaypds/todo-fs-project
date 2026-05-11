package com.example.todo.task.controller;

import com.example.todo.security.CustomUserDetails;
import com.example.todo.task.dto.CreateTaskRequest;
import com.example.todo.task.dto.TaskResponse;
import com.example.todo.task.dto.UpdateTaskRequest;
import com.example.todo.task.service.TaskService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/tasks")
@RequiredArgsConstructor
public class TaskController {

    private final TaskService taskService;

    @PostMapping
    public TaskResponse createTask(
            @AuthenticationPrincipal
            CustomUserDetails userDetails,

            @Valid @RequestBody
            CreateTaskRequest request
    ) {

        return taskService.createTask(
                userDetails.getUserId(),
                request
        );
    }

    @GetMapping
    public Page<TaskResponse> getTasks(
            @AuthenticationPrincipal
            CustomUserDetails userDetails,

            @RequestParam(defaultValue = "0")
            int page,

            @RequestParam(defaultValue = "10")
            int size
    ) {

        return taskService.getTasks(
                userDetails.getUserId(),
                page,
                size
        );
    }

    @PutMapping("/{taskId}")
    public TaskResponse updateTask(
            @AuthenticationPrincipal
            CustomUserDetails userDetails,

            @PathVariable
            UUID taskId,

            @RequestBody
            UpdateTaskRequest request
    ) {

        return taskService.updateTask(
                userDetails.getUserId(),
                taskId,
                request
        );
    }

    @DeleteMapping("/{taskId}")
    public void deleteTask(
            @AuthenticationPrincipal
            CustomUserDetails userDetails,

            @PathVariable
            UUID taskId
    ) {

        taskService.deleteTask(
                userDetails.getUserId(),
                taskId
        );
    }
}