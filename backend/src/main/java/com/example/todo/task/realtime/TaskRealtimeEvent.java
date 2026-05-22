package com.example.todo.task.realtime;

import lombok.*;

import java.util.UUID;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TaskRealtimeEvent {

    private String type;

    private UUID taskId;

    private String title;
}