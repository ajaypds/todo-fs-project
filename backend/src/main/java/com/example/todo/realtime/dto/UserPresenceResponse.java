package com.example.todo.realtime.dto;

import lombok.*;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserPresenceResponse {
    private String userId;
    private String username;
    private Integer activeSessions;
    private Boolean online;
}
