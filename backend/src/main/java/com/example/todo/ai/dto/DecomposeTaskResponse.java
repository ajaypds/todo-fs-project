package com.example.todo.ai.dto;

import lombok.*;

import java.util.ArrayList;
import java.util.List;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DecomposeTaskResponse {

    @Builder.Default
    private List<DecomposedSubtaskItem> subtasks = new ArrayList<>();
}
