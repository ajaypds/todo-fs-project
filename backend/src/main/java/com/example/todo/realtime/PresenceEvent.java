package com.example.todo.realtime;

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