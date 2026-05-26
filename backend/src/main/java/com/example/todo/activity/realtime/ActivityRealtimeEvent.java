package com.example.todo.activity.realtime;

import lombok.*;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ActivityRealtimeEvent {

    private String type;

    private String message;
}