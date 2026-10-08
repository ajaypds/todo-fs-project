package com.example.todo.ai.dto;

import lombok.*;
import java.util.ArrayList;
import java.util.List;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DailyPlannerResponse {
    private String summary;
    private String coachingTip;
    @Builder.Default
    private List<DailyTimeBlock> timeBlocks = new ArrayList<>();
    private EisenhowerMatrix matrix;
}
