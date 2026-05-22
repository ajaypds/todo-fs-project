package com.example.todo.task.event;

import lombok.Getter;
import java.util.UUID;

@Getter
public class TaskDeletedEvent {

    private final UUID taskId;

    public TaskDeletedEvent(UUID taskId) {

        this.taskId = taskId;
    }
}