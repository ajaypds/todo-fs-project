package com.example.todo.label.dto;

import lombok.Builder;
import lombok.Getter;
import java.util.UUID;

@Getter
@Builder
public class LabelResponse {

    private UUID id;

    private String name;

    private String color;
}