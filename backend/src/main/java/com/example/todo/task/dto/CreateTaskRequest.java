package com.example.todo.task.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;
import java.util.Set;
import java.util.UUID;

@Getter
@Setter
public class CreateTaskRequest {

    @NotBlank
    @Size(max=500)
    private String title;

    private String description;

    private Integer priority;

    private LocalDateTime dueDate;

    private UUID projectId;

    private Set<UUID> labelIds;
}
