package com.example.todo.task.dto;

import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class UpdateSubtaskRequest {

    @Size(max = 500)
    private String title;

    private Boolean completed;

    private Integer position;
}
