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
    private String userId;
    private Integer activeSessions;
    private Boolean online;
}