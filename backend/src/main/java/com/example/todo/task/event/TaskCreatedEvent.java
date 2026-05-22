package com.example.todo.task.event;

import lombok.Getter;
import java.util.UUID;

@Getter
public class TaskCreatedEvent {

    private final UUID taskId;

    private final String title;

    public TaskCreatedEvent(UUID taskId, String title) {

        this.taskId = taskId;

        this.title = title;
    }
}