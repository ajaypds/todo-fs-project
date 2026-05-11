package com.example.todo.task.dto;

import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

@Getter
@Setter
public class UpdateTaskRequest {

    @Size(max = 500)
    private String title;

    private String description;

    private Boolean completed;

    private Integer priority;

    private LocalDateTime dueDate;
}