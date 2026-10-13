package com.example.todo.ai.dto;

import lombok.*;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MatrixTaskItem {
    private String taskId;
    private String title;
    private String rationale;
    private Integer urgencyScore;
    private Integer importanceScore;
}
