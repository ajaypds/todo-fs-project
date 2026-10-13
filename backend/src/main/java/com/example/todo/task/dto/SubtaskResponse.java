package com.example.todo.task.dto;

import lombok.Builder;
import lombok.Getter;

import java.time.Instant;
import java.util.UUID;

@Getter
@Builder
public class SubtaskResponse {

    private UUID id;

    private UUID taskId;

    private String title;

    private boolean completed;

    private Integer position;

    private Instant createdAt;
}
