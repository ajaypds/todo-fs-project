package com.example.todo.activity.dto;

import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;
import java.util.UUID;

@Getter
@Builder
public class ActivityResponse {

    private UUID id;

    private String type;

    private String message;

    private LocalDateTime createdAt;
}