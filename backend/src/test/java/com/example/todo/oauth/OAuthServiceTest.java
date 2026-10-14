package com.example.todo.oauth;

import com.example.todo.auth.entity.RefreshToken;
import com.example.todo.auth.repository.RefreshTokenRepository;
import com.example.todo.auth.security.JwtService;
import com.example.todo.oauth.dto.TokenResponse;
import com.example.todo.oauth.entity.OAuthAuthorizationCode;
import com.example.todo.oauth.entity.OAuthClient;
import com.example.todo.oauth.exception.OAuthException;
import com.example.todo.oauth.repository.OAuthAuthorizationCodeRepository;
import com.example.todo.oauth.repository.OAuthClientRepository;
import com.example.todo.oauth.service.OAuthService;
import com.example.todo.user.entity.User;
import com.example.todo.user.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Base64;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class OAuthServiceTest {

    @Mock
    private OAuthClientRepository clientRepository;

    @Mock
    private OAuthAuthorizationCodeRepository codeRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private RefreshTokenRepository refreshTokenRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Mock
    private JwtService jwtService;

    @InjectMocks
    private OAuthService oAuthService;

    private User testUser;
    private OAuthClient testClient;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(oAuthService, "tokenExpiration", 900000L);

        testUser = User.builder()
                .id(UUID.randomUUID())
                .email("test@example.com")
                .password("encoded_pass")
                .emailVerified(true)
                .build();

        testClient = OAuthClient.builder()
                .id(UUID.randomUUID())
                .clientId("chatgpt-plugin")
                .clientSecret("secret123")
                .clientName("ChatGPT TodoFlow Plugin")
                .redirectUris("https://chat.openai.com/aip,https://chatgpt.com/aip,http://localhost")
                .scopes("read write all")
                .build();
    }

    @Test
    void testVerifyPkce_ValidS256() throws Exception {
        String verifier = "dBjftJeZ4CVP-mB92K27uhbUJU1p1r_wW1gFWFOEjXk";
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        byte[] hash = digest.digest(verifier.getBytes(StandardCharsets.US_ASCII));
        String challenge = Base64.getUrlEncoder().withoutPadding().encodeToString(hash);

        assertTrue(oAuthService.verifyPkce(verifier, challenge, "S256"));
        assertFalse(oAuthService.verifyPkce("wrong-verifier", challenge, "S256"));
    }

    @Test
    void testCreateAuthorizationCode_Success() {
        when(clientRepository.findByClientId("chatgpt-plugin")).thenReturn(Optional.of(testClient));
        when(codeRepository.save(any(OAuthAuthorizationCode.class))).thenAnswer(invocation -> invocation.getArgument(0));

        OAuthAuthorizationCode code = oAuthService.createAuthorizationCode(
                testUser,
                "chatgpt-plugin",
                "https://chatgpt.com/aip/callback",
                "read write",
                "some-challenge",
                "S256"
        );

        assertNotNull(code);
        assertNotNull(code.getCode());
        assertEquals("chatgpt-plugin", code.getClientId());
        assertEquals(testUser, code.getUser());
        assertFalse(code.isUsed());
    }

    @Test
    void testCreateAuthorizationCode_DisallowedRedirectUri_ThrowsException() {
        when(clientRepository.findByClientId("chatgpt-plugin")).thenReturn(Optional.of(testClient));

        assertThrows(OAuthException.class, () ->
                oAuthService.createAuthorizationCode(
                        testUser,
                        "chatgpt-plugin",
                        "https://malicious-site.com/callback",
                        "read write",
                        "some-challenge",
                        "S256"
                )
        );
    }

    @Test
    void testExchangeCode_SuccessWithPkce() throws Exception {
        String verifier = "my-secret-verifier-123456789012345678901234";
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        byte[] hash = digest.digest(verifier.getBytes(StandardCharsets.US_ASCII));
        String challenge = Base64.getUrlEncoder().withoutPadding().encodeToString(hash);

        OAuthAuthorizationCode authCode = OAuthAuthorizationCode.builder()
                .code("test-auth-code-123")
                .user(testUser)
                .clientId("chatgpt-plugin")
                .redirectUri("https://chatgpt.com/aip/callback")
                .scope("read write all")
                .codeChallenge(challenge)
                .codeChallengeMethod("S256")
                .expiresAt(Instant.now().plus(5, ChronoUnit.MINUTES))
                .used(false)
                .build();

        when(codeRepository.findByCode("test-auth-code-123")).thenReturn(Optional.of(authCode));
        when(clientRepository.findByClientId("chatgpt-plugin")).thenReturn(Optional.of(testClient));
        when(jwtService.generateToken(testUser.getId(), testUser.getEmail())).thenReturn("mock-access-token");
        when(jwtService.generateRefreshToken(testUser)).thenReturn("mock-refresh-token");

        TokenResponse response = oAuthService.exchangeCode(
                "authorization_code",
                "test-auth-code-123",
                "https://chatgpt.com/aip/callback",
                "chatgpt-plugin",
                "secret123",
                verifier
        );

        assertNotNull(response);
        assertEquals("mock-access-token", response.getAccessToken());
        assertEquals("Bearer", response.getTokenType());
        assertEquals("mock-refresh-token", response.getRefreshToken());
        assertTrue(authCode.isUsed());
    }

    @Test
    void testExchangeCode_UsedCode_ThrowsException() {
        OAuthAuthorizationCode usedCode = OAuthAuthorizationCode.builder()
                .code("already-used-code")
                .user(testUser)
                .clientId("chatgpt-plugin")
                .redirectUri("https://chatgpt.com/aip/callback")
                .expiresAt(Instant.now().plus(5, ChronoUnit.MINUTES))
                .used(true)
                .build();

        when(codeRepository.findByCode("already-used-code")).thenReturn(Optional.of(usedCode));

        OAuthException ex = assertThrows(OAuthException.class, () ->
                oAuthService.exchangeCode(
                        "authorization_code",
                        "already-used-code",
                        "https://chatgpt.com/aip/callback",
                        "chatgpt-plugin",
                        "secret123",
                        "verifier"
                )
        );

        assertEquals("invalid_grant", ex.getError());
    }

    @Test
    void testRefreshToken_Success() {
        RefreshToken token = RefreshToken.builder()
                .token("valid-refresh-token")
                .user(testUser)
                .expiresAt(Instant.now().plus(7, ChronoUnit.DAYS))
                .revoked(false)
                .build();

        when(refreshTokenRepository.findByToken("valid-refresh-token")).thenReturn(Optional.of(token));
        when(jwtService.generateToken(testUser.getId(), testUser.getEmail())).thenReturn("new-access-token");
        when(jwtService.generateRefreshToken(testUser)).thenReturn("new-refresh-token");

        TokenResponse response = oAuthService.refreshToken(
                "refresh_token",
                "valid-refresh-token",
                null,
                null
        );

        assertNotNull(response);
        assertEquals("new-access-token", response.getAccessToken());
        assertEquals("new-refresh-token", response.getRefreshToken());
        assertTrue(token.isRevoked());
    }
}
