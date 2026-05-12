package com.example.todo.project.dto;

import lombok.Builder;
import lombok.Getter;

import java.util.UUID;

@Getter
@Builder
public class ProjectResponse {

    private UUID id;

    private String name;

    private String color;
}