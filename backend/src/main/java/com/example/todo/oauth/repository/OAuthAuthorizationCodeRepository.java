package com.example.todo.oauth.repository;

import com.example.todo.oauth.entity.OAuthAuthorizationCode;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

public interface OAuthAuthorizationCodeRepository extends JpaRepository<OAuthAuthorizationCode, UUID> {
    Optional<OAuthAuthorizationCode> findByCode(String code);
    void deleteByExpiresAtBefore(Instant now);
}
