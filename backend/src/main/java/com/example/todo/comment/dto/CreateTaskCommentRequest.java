package com.example.todo.comment.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class CreateTaskCommentRequest {

    @NotBlank(message = "Comment content cannot be empty")
    @Size(max = 5000, message = "Comment cannot exceed 5000 characters")
    private String content;
}
