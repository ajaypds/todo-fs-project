package com.example.todo.ai.dto;

import lombok.*;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DailyPlannerTaskSnapshot {
    private String id;
    private String title;
    private String description;
    private Integer priority;
    private String dueDate;
    private String projectName;
    private Integer subtaskCount;
    private Integer completedSubtaskCount;
}
