package com.example.todo.task.dto;

import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;
import java.util.Set;
import java.util.UUID;

@Getter
@Setter
public class UpdateTaskRequest {

    @Size(max = 500)
    private String title;

    private String description;

    private Boolean completed;

    private Integer priority;

    private LocalDateTime dueDate;

    private Set<UUID> labelIds;
}