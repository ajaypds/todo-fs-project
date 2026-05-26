package com.example.todo.realtime.dto;

import lombok.*;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PresenceEvent {

    private String type;
    private String username;
}