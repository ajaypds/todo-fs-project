package com.example.todo.task.event;

import lombok.AllArgsConstructor;
import lombok.Getter;
import java.util.UUID;

@Getter
@AllArgsConstructor
public class TaskDeletedEvent {

    private final UUID taskId;
    private final UUID userId;

}