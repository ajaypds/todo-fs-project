package com.example.todo.ai.dto;
import lombok.*;
import java.util.List;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ProductivityInsightResponse {

    private String summary;

    private List<String> recommendations;
}
