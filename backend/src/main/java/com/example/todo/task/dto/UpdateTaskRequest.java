package com.example.todo.task.dto;

import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

import com.example.todo.common.LenientInstantDeserializer;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import java.time.Instant;
import java.util.Set;
import java.util.UUID;

@Getter
@Setter
public class UpdateTaskRequest {

    @Size(max = 500)
    private String title;

    private String description;

    private Boolean completed;

    private Integer priority;

    @JsonDeserialize(using = LenientInstantDeserializer.class)
    private Instant dueDate;

    private Set<UUID> labelIds;

    private com.example.todo.task.entity.RecurrenceType recurrenceType;

    private Integer recurrenceInterval;
}