package com.example.todo.ai.dto;

import lombok.*;
import java.util.ArrayList;
import java.util.List;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class EisenhowerMatrix {
    @Builder.Default
    private List<MatrixTaskItem> doFirst = new ArrayList<>();

    @Builder.Default
    private List<MatrixTaskItem> schedule = new ArrayList<>();

    @Builder.Default
    private List<MatrixTaskItem> delegate = new ArrayList<>();

    @Builder.Default
    private List<MatrixTaskItem> eliminate = new ArrayList<>();
}
