package com.example.todo.ai.dto;

import lombok.*;
import java.util.List;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DailyPlannerRequest {
    private List<DailyPlannerTaskSnapshot> tasks;
    private String userTimezone;
}
