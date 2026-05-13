package com.example.todo.task.dto;

import lombok.Getter;
import lombok.Setter;

import java.util.List;
import java.util.UUID;

@Getter
@Setter
public class ReorderTasksRequest {

    private List<UUID> taskIds;
}