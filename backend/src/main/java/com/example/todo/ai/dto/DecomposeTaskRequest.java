package com.example.todo.ai.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.*;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DecomposeTaskRequest {

    @NotBlank(message = "Task title is required")
    private String title;

    private String description;
}
