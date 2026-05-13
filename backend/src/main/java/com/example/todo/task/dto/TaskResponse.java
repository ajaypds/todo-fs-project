package com.example.todo.task.dto;

import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;
import java.util.UUID;

@Getter
@Builder
public class TaskResponse {

    private UUID id;

    private String title;

    private String description;

    private boolean completed;

    private Integer priority;

    private LocalDateTime dueDate;

    private UUID projectId;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;

    private Integer position;
}