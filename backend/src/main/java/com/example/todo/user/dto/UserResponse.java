package com.example.todo.user.dto;

import lombok.Builder;
import lombok.Getter;

import java.time.Instant;
import java.util.UUID;

@Getter
@Builder
public class UserResponse {

    private UUID id;
    private String email;
    private String fullName;
    private String timezone;
    private boolean emailVerified;
    private Instant createdAt;
}