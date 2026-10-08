package com.example.todo.task.dto;

import com.example.todo.label.dto.LabelResponse;
import lombok.Builder;
import lombok.Getter;

import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.UUID;

@Getter
@Builder
public class TaskResponse {

    private UUID id;

    private String title;

    private String description;

    private boolean completed;

    private Integer priority;

    private Instant dueDate;

    private UUID projectId;

    private Instant createdAt;

    private Instant updatedAt;

    private Integer position;

    private Set<LabelResponse> labels;

    private List<SubtaskResponse> subtasks;

    private String recurrenceType;

    private Integer recurrenceInterval;
}