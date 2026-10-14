package com.example.todo.oauth.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "oauth_clients")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OAuthClient {

    @Id
    @GeneratedValue
    private UUID id;

    @Column(name = "client_id", nullable = false, unique = true, length = 100)
    private String clientId;

    @Column(name = "client_secret")
    private String clientSecret;

    @Column(name = "client_name", nullable = false, length = 100)
    private String clientName;

    @Column(name = "redirect_uris", nullable = false, columnDefinition = "TEXT")
    private String redirectUris;

    @Column(length = 255)
    private String scopes;

    @Column(name = "created_at", nullable = false)
    @Builder.Default
    private Instant createdAt = Instant.now();

    public boolean isRedirectUriAllowed(String candidateUri) {
        if (candidateUri == null || candidateUri.isBlank()) {
            return false;
        }
        if (this.redirectUris == null || this.redirectUris.isBlank()) {
            return false;
        }
        String[] allowed = this.redirectUris.split(",");
        for (String uri : allowed) {
            String trimmed = uri.trim();
            if (candidateUri.equals(trimmed) || candidateUri.startsWith(trimmed)) {
                return true;
            }
        }
        return false;
    }
}
