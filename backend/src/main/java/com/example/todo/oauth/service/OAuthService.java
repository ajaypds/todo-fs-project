package com.example.todo.oauth.service;

import com.example.todo.auth.entity.RefreshToken;
import com.example.todo.auth.repository.RefreshTokenRepository;
import com.example.todo.auth.security.JwtService;
import com.example.todo.oauth.dto.TokenResponse;
import com.example.todo.oauth.entity.OAuthAuthorizationCode;
import com.example.todo.oauth.entity.OAuthClient;
import com.example.todo.oauth.exception.OAuthException;
import com.example.todo.oauth.repository.OAuthAuthorizationCodeRepository;
import com.example.todo.oauth.repository.OAuthClientRepository;
import com.example.todo.user.entity.User;
import com.example.todo.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.*;

@Slf4j
@Service
@RequiredArgsConstructor
public class OAuthService {

    private final OAuthClientRepository clientRepository;
    private final OAuthAuthorizationCodeRepository codeRepository;
    private final UserRepository userRepository;
    private final RefreshTokenRepository refreshTokenRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;

    @Value("${jwt.expiration:900000}")
    private long tokenExpiration;

    private final SecureRandom secureRandom = new SecureRandom();

    @Transactional(readOnly = true)
    public OAuthClient getClient(String clientId) {
        if (clientId == null || clientId.isBlank()) {
            throw new OAuthException("invalid_request", "Missing client_id parameter");
        }
        return clientRepository.findByClientId(clientId.trim())
                .orElseThrow(() -> new OAuthException("invalid_client", "Client not found: " + clientId, HttpStatus.UNAUTHORIZED));
    }

    public void validateClientCredentials(OAuthClient client, String candidateSecret) {
        if (client.getClientSecret() != null && !client.getClientSecret().isBlank()) {
            if (candidateSecret == null || candidateSecret.isBlank()) {
                throw new OAuthException("invalid_client", "Client secret is required for this client", HttpStatus.UNAUTHORIZED);
            }
            boolean matches = candidateSecret.equals(client.getClientSecret()) ||
                    passwordEncoder.matches(candidateSecret, client.getClientSecret());
            if (!matches) {
                throw new OAuthException("invalid_client", "Invalid client secret", HttpStatus.UNAUTHORIZED);
            }
        }
    }

    @Transactional
    public OAuthAuthorizationCode createAuthorizationCode(
            User user,
            String clientId,
            String redirectUri,
            String scope,
            String codeChallenge,
            String codeChallengeMethod
    ) {
        OAuthClient client = getClient(clientId);

        if (!client.isRedirectUriAllowed(redirectUri)) {
            throw new OAuthException("invalid_request", "The provided redirect_uri is not registered for this client");
        }

        // OAuth 2.1 requires PKCE for authorization code grant
        if (codeChallenge == null || codeChallenge.isBlank()) {
            log.warn("OAuth 2.1 warning: Client {} did not send code_challenge", clientId);
        }

        byte[] randomBytes = new byte[32];
        secureRandom.nextBytes(randomBytes);
        String code = Base64.getUrlEncoder().withoutPadding().encodeToString(randomBytes);

        OAuthAuthorizationCode authCode = OAuthAuthorizationCode.builder()
                .code(code)
                .user(user)
                .clientId(client.getClientId())
                .redirectUri(redirectUri)
                .scope(scope != null ? scope : client.getScopes())
                .codeChallenge(codeChallenge)
                .codeChallengeMethod(codeChallengeMethod != null ? codeChallengeMethod : "S256")
                .expiresAt(Instant.now().plus(5, ChronoUnit.MINUTES))
                .used(false)
                .build();

        return codeRepository.save(authCode);
    }

    @Transactional
    public TokenResponse exchangeCode(
            String grantType,
            String code,
            String redirectUri,
            String clientId,
            String clientSecret,
            String codeVerifier
    ) {
        if (!"authorization_code".equals(grantType)) {
            throw new OAuthException("unsupported_grant_type", "grant_type must be authorization_code");
        }

        if (code == null || code.isBlank()) {
            throw new OAuthException("invalid_request", "Missing code parameter");
        }

        OAuthAuthorizationCode authCode = codeRepository.findByCode(code.trim())
                .orElseThrow(() -> new OAuthException("invalid_grant", "Invalid authorization code"));

        if (authCode.isUsed()) {
            log.warn("Security Alert: Reuse attempt detected for authorization code {}", code);
            throw new OAuthException("invalid_grant", "Authorization code already used");
        }

        if (authCode.isExpired()) {
            throw new OAuthException("invalid_grant", "Authorization code expired");
        }

        // Single-use guarantee
        authCode.setUsed(true);
        codeRepository.save(authCode);

        // Validate client
        if (clientId != null && !clientId.isBlank() && !clientId.equals(authCode.getClientId())) {
            throw new OAuthException("invalid_grant", "Client mismatch for authorization code");
        }

        OAuthClient client = getClient(authCode.getClientId());
        validateClientCredentials(client, clientSecret);

        // Validate redirect_uri
        if (redirectUri != null && !redirectUri.isBlank() && !redirectUri.equals(authCode.getRedirectUri())) {
            throw new OAuthException("invalid_grant", "redirect_uri mismatch");
        }

        // Validate PKCE (Mandatory in OAuth 2.1 if challenge was set)
        if (authCode.getCodeChallenge() != null && !authCode.getCodeChallenge().isBlank()) {
            if (codeVerifier == null || codeVerifier.isBlank()) {
                throw new OAuthException("invalid_request", "code_verifier required for PKCE validation");
            }

            boolean pkceValid = verifyPkce(codeVerifier, authCode.getCodeChallenge(), authCode.getCodeChallengeMethod());
            if (!pkceValid) {
                throw new OAuthException("invalid_grant", "PKCE verification failed: code_verifier does not match code_challenge");
            }
        }

        User user = authCode.getUser();
        String accessToken = jwtService.generateToken(user.getId(), user.getEmail());
        String refreshToken = jwtService.generateRefreshToken(user);

        refreshTokenRepository.save(
                RefreshToken.builder()
                        .token(refreshToken)
                        .user(user)
                        .expiresAt(Instant.now().plus(30, ChronoUnit.DAYS))
                        .revoked(false)
                        .build()
        );

        return TokenResponse.builder()
                .accessToken(accessToken)
                .tokenType("Bearer")
                .expiresIn(tokenExpiration / 1000)
                .refreshToken(refreshToken)
                .scope(authCode.getScope())
                .build();
    }

    @Transactional
    public TokenResponse refreshToken(
            String grantType,
            String refreshTokenStr,
            String clientId,
            String clientSecret
    ) {
        if (!"refresh_token".equals(grantType)) {
            throw new OAuthException("unsupported_grant_type", "grant_type must be refresh_token");
        }

        if (refreshTokenStr == null || refreshTokenStr.isBlank()) {
            throw new OAuthException("invalid_request", "Missing refresh_token parameter");
        }

        if (clientId != null && !clientId.isBlank()) {
            OAuthClient client = getClient(clientId);
            validateClientCredentials(client, clientSecret);
        }

        RefreshToken refreshToken = refreshTokenRepository.findByToken(refreshTokenStr.trim())
                .orElseThrow(() -> new OAuthException("invalid_grant", "Invalid refresh token"));

        if (refreshToken.isRevoked() || refreshToken.getExpiresAt().isBefore(Instant.now())) {
            throw new OAuthException("invalid_grant", "Refresh token is expired or revoked");
        }

        User user = refreshToken.getUser();
        String newAccessToken = jwtService.generateToken(user.getId(), user.getEmail());

        // Rotate refresh token
        refreshToken.setRevoked(true);
        refreshTokenRepository.save(refreshToken);

        String newRefreshToken = jwtService.generateRefreshToken(user);
        refreshTokenRepository.save(
                RefreshToken.builder()
                        .token(newRefreshToken)
                        .user(user)
                        .expiresAt(Instant.now().plus(30, ChronoUnit.DAYS))
                        .revoked(false)
                        .build()
        );

        return TokenResponse.builder()
                .accessToken(newAccessToken)
                .tokenType("Bearer")
                .expiresIn(tokenExpiration / 1000)
                .refreshToken(newRefreshToken)
                .scope("read write all")
                .build();
    }

    @Transactional
    public void revokeToken(String token) {
        if (token != null && !token.isBlank()) {
            refreshTokenRepository.findByToken(token.trim()).ifPresent(t -> {
                t.setRevoked(true);
                refreshTokenRepository.save(t);
            });
        }
    }

    public boolean verifyPkce(String codeVerifier, String codeChallenge, String method) {
        if (codeVerifier == null || codeChallenge == null) {
            return false;
        }

        if ("S256".equalsIgnoreCase(method) || method == null) {
            try {
                MessageDigest digest = MessageDigest.getInstance("SHA-256");
                byte[] hash = digest.digest(codeVerifier.getBytes(StandardCharsets.US_ASCII));
                String computedChallenge = Base64.getUrlEncoder().withoutPadding().encodeToString(hash);
                return computedChallenge.equals(codeChallenge);
            } catch (Exception e) {
                log.error("PKCE S256 calculation failed", e);
                return false;
            }
        } else if ("plain".equalsIgnoreCase(method)) {
            return codeVerifier.equals(codeChallenge);
        }

        return false;
    }

    public Map<String, Object> getDiscoveryMetadata(String baseUrl) {
        Map<String, Object> metadata = new LinkedHashMap<>();
        metadata.put("issuer", baseUrl);
        metadata.put("authorization_endpoint", baseUrl + "/oauth2/authorize");
        metadata.put("token_endpoint", baseUrl + "/oauth2/token");
        metadata.put("revocation_endpoint", baseUrl + "/oauth2/revoke");
        metadata.put("response_types_supported", List.of("code"));
        metadata.put("grant_types_supported", List.of("authorization_code", "refresh_token"));
        metadata.put("code_challenge_methods_supported", List.of("S256", "plain"));
        metadata.put("token_endpoint_auth_methods_supported", List.of("client_secret_post", "client_secret_basic", "none"));
        metadata.put("scopes_supported", List.of("read", "write", "all"));
        return metadata;
    }
}
