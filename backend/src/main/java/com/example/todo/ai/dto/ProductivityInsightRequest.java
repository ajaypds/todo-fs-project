package com.example.todo.ai.dto;

import lombok.*;
import java.util.List;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class ProductivityInsightRequest {

    private List<TaskSnapshot> tasks;
}
