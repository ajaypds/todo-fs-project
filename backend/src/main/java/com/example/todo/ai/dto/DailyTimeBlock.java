package com.example.todo.ai.dto;

import lombok.*;
import java.util.ArrayList;
import java.util.List;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DailyTimeBlock {
    private String timePeriod;
    private String focusTheme;
    @Builder.Default
    private List<String> taskIds = new ArrayList<>();
    @Builder.Default
    private List<String> taskTitles = new ArrayList<>();
}
