package com.example.todo.task.event;

import lombok.AllArgsConstructor;
import lombok.Getter;
import java.util.UUID;

@Getter
@AllArgsConstructor
public class TaskUpdatedEvent {

    private final UUID taskId;
    private final String title;
    private final UUID userId;

}