package com.example.todo.comment.dto;

import lombok.Builder;
import lombok.Getter;

import java.time.Instant;
import java.util.UUID;

@Getter
@Builder
public class TaskCommentResponse {

    private UUID id;
    private UUID taskId;
    private UUID userId;
    private String userName;
    private String userEmail;
    private String content;
    private Instant createdAt;
    private Instant updatedAt;
}
