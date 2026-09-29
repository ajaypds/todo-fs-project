package com.example.todo.ai.dto;

import lombok.*;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TaskSnapshot {
    private String title;
    private String priority;
    private Boolean completed;
    private String dueDate;
}
