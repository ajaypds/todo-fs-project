package com.example.todo.apikey.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ApiKeyResponse {

    private UUID id;
    private String name;
    private String keyPrefix;
    private String apiKey; // Populated only once upon generation
    private Instant createdAt;
    private Instant lastUsedAt;
}
