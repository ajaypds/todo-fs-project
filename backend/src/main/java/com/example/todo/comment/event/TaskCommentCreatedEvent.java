package com.example.todo.comment.event;

import lombok.AllArgsConstructor;
import lombok.Getter;

import java.time.Instant;
import java.util.UUID;

@Getter
@AllArgsConstructor
public class TaskCommentCreatedEvent {

    private final UUID commentId;
    private final UUID taskId;
    private final UUID authorId;
    private final String authorName;
    private final String authorEmail;
    private final String content;
    private final Instant createdAt;
}
