package com.example.todo.user.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "users")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class User {

    @Id
    @GeneratedValue
    private UUID id;

    @Column(nullable = false, unique = true)
    private String email;

    @Column(nullable = false)
    private String password;

    private String fullName;

    @Builder.Default
    @Column(nullable = false, length = 50)
    private String timezone = "UTC";

    @Builder.Default
    @Column(nullable = false)
    private boolean emailVerified = false;

    @Column(length = 10)
    private String verificationCode;

    private Instant verificationCodeExpiresAt;

    @Column(nullable = false)
    private Instant createdAt;

    public String getName() {
        return fullName != null && !fullName.isBlank() ? fullName : email;
    }
}
