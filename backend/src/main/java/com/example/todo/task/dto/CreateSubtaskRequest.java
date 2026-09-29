package com.example.todo.task.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class CreateSubtaskRequest {

    @NotBlank
    @Size(max = 500)
    private String title;
}
