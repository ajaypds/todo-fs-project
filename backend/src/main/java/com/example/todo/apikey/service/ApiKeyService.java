package com.example.todo.apikey.service;

import com.example.todo.apikey.dto.ApiKeyResponse;
import com.example.todo.apikey.entity.ApiKey;
import com.example.todo.apikey.repository.ApiKeyRepository;
import com.example.todo.user.entity.User;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.HexFormat;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class ApiKeyService {

    private final ApiKeyRepository apiKeyRepository;
    private static final String KEY_PREFIX = "todo_live_";
    private static final SecureRandom SECURE_RANDOM = new SecureRandom();

    @Transactional
    public ApiKeyResponse generateKey(User user, String name) {
        byte[] randomBytes = new byte[24];
        SECURE_RANDOM.nextBytes(randomBytes);
        String randomHex = HexFormat.of().formatHex(randomBytes);
        String rawKey = KEY_PREFIX + randomHex;

        String keyHash = hashKey(rawKey);
        String prefixPreview = rawKey.substring(0, Math.min(16, rawKey.length()));

        ApiKey apiKey = ApiKey.builder()
                .user(user)
                .name(name.trim())
                .keyHash(keyHash)
                .keyPrefix(prefixPreview)
                .createdAt(Instant.now())
                .build();

        ApiKey saved = apiKeyRepository.save(apiKey);

        return ApiKeyResponse.builder()
                .id(saved.getId())
                .name(saved.getName())
                .keyPrefix(saved.getKeyPrefix())
                .apiKey(rawKey)
                .createdAt(saved.getCreatedAt())
                .lastUsedAt(saved.getLastUsedAt())
                .build();
    }

    @Transactional(readOnly = true)
    public List<ApiKeyResponse> listKeys(User user) {
        return apiKeyRepository.findByUserIdOrderByCreatedAtDesc(user.getId())
                .stream()
                .map(k -> ApiKeyResponse.builder()
                        .id(k.getId())
                        .name(k.getName())
                        .keyPrefix(k.getKeyPrefix())
                        .createdAt(k.getCreatedAt())
                        .lastUsedAt(k.getLastUsedAt())
                        .build())
                .toList();
    }

    @Transactional
    public void revokeKey(User user, UUID id) {
        apiKeyRepository.deleteByIdAndUserId(id, user.getId());
    }

    @Transactional
    public Optional<User> validateKey(String rawKey) {
        if (rawKey == null || rawKey.isBlank() || !rawKey.startsWith(KEY_PREFIX)) {
            return Optional.empty();
        }

        String keyHash = hashKey(rawKey.trim());
        Optional<ApiKey> apiKeyOpt = apiKeyRepository.findByKeyHash(keyHash);

        if (apiKeyOpt.isPresent()) {
            ApiKey apiKey = apiKeyOpt.get();
            apiKey.setLastUsedAt(Instant.now());
            apiKeyRepository.save(apiKey);
            return Optional.of(apiKey.getUser());
        }

        return Optional.empty();
    }

    public static String hashKey(String rawKey) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hashBytes = digest.digest(rawKey.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hashBytes);
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException("SHA-256 algorithm not available", e);
        }
    }
}
