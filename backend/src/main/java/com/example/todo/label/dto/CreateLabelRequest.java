package com.example.todo.label.dto;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class CreateLabelRequest {

    private String name;

    private String color;
}