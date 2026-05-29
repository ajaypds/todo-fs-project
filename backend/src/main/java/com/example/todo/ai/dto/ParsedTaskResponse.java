package com.example.todo.ai.dto;

import lombok.*;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ParsedTaskResponse {

    private String title;

    private String description;

    private String priority;

    private String dueDate;
}