package com.example.todo.apikey.controller;

import com.example.todo.apikey.dto.ApiKeyResponse;
import com.example.todo.apikey.dto.CreateApiKeyRequest;
import com.example.todo.apikey.service.ApiKeyService;
import com.example.todo.auth.security.CustomUserDetails;
import com.example.todo.user.entity.User;
import com.example.todo.user.repository.UserRepository;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/api-keys")
@RequiredArgsConstructor
public class ApiKeyController {

    private final ApiKeyService apiKeyService;
    private final UserRepository userRepository;

    @PostMapping
    public ResponseEntity<ApiKeyResponse> createApiKey(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @Valid @RequestBody CreateApiKeyRequest request
    ) {
        User user = userRepository.findById(userDetails.getUserId()).orElseThrow();
        ApiKeyResponse response = apiKeyService.generateKey(user, request.getName());
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @GetMapping
    public List<ApiKeyResponse> listApiKeys(
            @AuthenticationPrincipal CustomUserDetails userDetails
    ) {
        User user = userRepository.findById(userDetails.getUserId()).orElseThrow();
        return apiKeyService.listKeys(user);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void revokeApiKey(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @PathVariable UUID id
    ) {
        User user = userRepository.findById(userDetails.getUserId()).orElseThrow();
        apiKeyService.revokeKey(user, id);
    }
}
