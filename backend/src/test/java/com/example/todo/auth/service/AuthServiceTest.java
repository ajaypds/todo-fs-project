package com.example.todo.auth.service;

import com.example.todo.auth.dto.AuthResponse;
import com.example.todo.auth.dto.LoginRequest;
import com.example.todo.auth.dto.RegisterRequest;
import com.example.todo.auth.dto.ResendVerificationRequest;
import com.example.todo.auth.dto.VerifyEmailRequest;
import com.example.todo.auth.entity.RefreshToken;
import com.example.todo.auth.repository.RefreshTokenRepository;
import com.example.todo.auth.security.JwtService;
import com.example.todo.exception.BadRequestException;
import com.example.todo.exception.UnauthorizedException;
import com.example.todo.user.entity.User;
import com.example.todo.user.repository.UserRepository;
import com.example.todo.workspace.entity.Workspace;
import com.example.todo.workspace.entity.WorkspaceMember;
import com.example.todo.workspace.repository.WorkspaceMemberRepository;
import com.example.todo.workspace.repository.WorkspaceRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Mock
    private JwtService jwtService;

    @Mock
    private RefreshTokenRepository refreshTokenRepository;

    @Mock
    private WorkspaceRepository workspaceRepository;

    @Mock
    private WorkspaceMemberRepository workspaceMemberRepository;

    @InjectMocks
    private AuthService authService;

    private RegisterRequest registerRequest;

    @BeforeEach
    void setUp() {
        registerRequest = new RegisterRequest();
        registerRequest.setEmail("  Test.User@Example.COM  ");
        registerRequest.setPassword("securePassword123");
        registerRequest.setFullName("Test User");
        registerRequest.setTimezone("Asia/Kolkata");
    }

    @Test
    void register_ShouldNormalizeEmailAndRequireVerification() {
        when(userRepository.findByEmail("test.user@example.com")).thenReturn(Optional.empty());
        when(passwordEncoder.encode("securePassword123")).thenReturn("encodedPassword");

        AuthResponse response = authService.register(registerRequest);

        assertNotNull(response);
        assertTrue(response.isEmailVerificationRequired());
        assertEquals("test.user@example.com", response.getEmail());
        assertNull(response.getToken());
        assertNull(response.getRefreshToken());

        ArgumentCaptor<User> userCaptor = ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(userCaptor.capture());
        User savedUser = userCaptor.getValue();
        assertEquals("test.user@example.com", savedUser.getEmail());
        assertEquals("encodedPassword", savedUser.getPassword());
        assertEquals("Test User", savedUser.getFullName());
        assertEquals("Asia/Kolkata", savedUser.getTimezone());
        assertFalse(savedUser.isEmailVerified());
        assertNotNull(savedUser.getVerificationCode());
        assertEquals(6, savedUser.getVerificationCode().length());
        assertNotNull(savedUser.getVerificationCodeExpiresAt());

        verify(workspaceRepository).save(any(Workspace.class));
        verify(workspaceMemberRepository).save(any(WorkspaceMember.class));
        verify(refreshTokenRepository, never()).save(any(RefreshToken.class));
    }

    @Test
    void register_ShouldThrowBadRequestException_WhenEmailAlreadyExists() {
        when(userRepository.findByEmail("test.user@example.com")).thenReturn(Optional.of(new User()));

        BadRequestException exception = assertThrows(BadRequestException.class, () ->
                authService.register(registerRequest)
        );

        assertEquals("Email already exists", exception.getMessage());
        verify(userRepository, never()).save(any(User.class));
        verify(workspaceRepository, never()).save(any(Workspace.class));
        verify(workspaceMemberRepository, never()).save(any(WorkspaceMember.class));
        verify(refreshTokenRepository, never()).save(any(RefreshToken.class));
    }

    @Test
    void login_ShouldNormalizeEmailAndSucceed_WhenCredentialsAreValidAndEmailVerified() {
        LoginRequest loginRequest = new LoginRequest();
        loginRequest.setEmail("  TEST.USER@EXAMPLE.COM ");
        loginRequest.setPassword("securePassword123");

        User existingUser = User.builder()
                .id(UUID.randomUUID())
                .email("test.user@example.com")
                .password("encodedPassword")
                .fullName("Test User")
                .timezone("UTC")
                .emailVerified(true)
                .createdAt(Instant.now())
                .build();

        when(userRepository.findByEmail("test.user@example.com")).thenReturn(Optional.of(existingUser));
        when(passwordEncoder.matches("securePassword123", "encodedPassword")).thenReturn(true);
        when(jwtService.generateToken(existingUser.getId(), existingUser.getEmail())).thenReturn("access-token-xyz");
        when(jwtService.generateRefreshToken(existingUser)).thenReturn("refresh-token-xyz");

        AuthResponse response = authService.login(loginRequest);

        assertNotNull(response);
        assertEquals("access-token-xyz", response.getToken());
        assertEquals("refresh-token-xyz", response.getRefreshToken());
        verify(refreshTokenRepository).save(any(RefreshToken.class));
    }

    @Test
    void login_ShouldThrowUnauthorizedException_WhenEmailNotVerified() {
        LoginRequest loginRequest = new LoginRequest();
        loginRequest.setEmail("test.user@example.com");
        loginRequest.setPassword("securePassword123");

        User existingUser = User.builder()
                .id(UUID.randomUUID())
                .email("test.user@example.com")
                .password("encodedPassword")
                .emailVerified(false)
                .build();

        when(userRepository.findByEmail("test.user@example.com")).thenReturn(Optional.of(existingUser));
        when(passwordEncoder.matches("securePassword123", "encodedPassword")).thenReturn(true);

        UnauthorizedException exception = assertThrows(UnauthorizedException.class, () ->
                authService.login(loginRequest)
        );

        assertEquals("Email not verified. Please verify your email before logging in.", exception.getMessage());
        verify(refreshTokenRepository, never()).save(any(RefreshToken.class));
    }

    @Test
    void login_ShouldThrowUnauthorizedException_WhenPasswordDoesNotMatch() {
        LoginRequest loginRequest = new LoginRequest();
        loginRequest.setEmail("test.user@example.com");
        loginRequest.setPassword("wrongPassword");

        User existingUser = User.builder()
                .id(UUID.randomUUID())
                .email("test.user@example.com")
                .password("encodedPassword")
                .emailVerified(true)
                .build();

        when(userRepository.findByEmail("test.user@example.com")).thenReturn(Optional.of(existingUser));
        when(passwordEncoder.matches("wrongPassword", "encodedPassword")).thenReturn(false);

        UnauthorizedException exception = assertThrows(UnauthorizedException.class, () ->
                authService.login(loginRequest)
        );

        assertEquals("Invalid credentials", exception.getMessage());
        verify(refreshTokenRepository, never()).save(any(RefreshToken.class));
    }

    @Test
    void verifyEmail_ShouldSucceed_WhenCodeIsValid() {
        VerifyEmailRequest request = VerifyEmailRequest.builder()
                .email("  Test.User@example.com  ")
                .code("123456")
                .build();

        User unverifiedUser = User.builder()
                .id(UUID.randomUUID())
                .email("test.user@example.com")
                .emailVerified(false)
                .verificationCode("123456")
                .verificationCodeExpiresAt(Instant.now().plus(10, ChronoUnit.MINUTES))
                .build();

        when(userRepository.findByEmail("test.user@example.com")).thenReturn(Optional.of(unverifiedUser));
        when(jwtService.generateToken(unverifiedUser.getId(), unverifiedUser.getEmail())).thenReturn("token-123");
        when(jwtService.generateRefreshToken(unverifiedUser)).thenReturn("refresh-123");

        AuthResponse response = authService.verifyEmail(request);

        assertNotNull(response);
        assertEquals("token-123", response.getToken());
        assertEquals("refresh-123", response.getRefreshToken());
        assertFalse(response.isEmailVerificationRequired());
        assertEquals("Email verified successfully", response.getMessage());

        assertTrue(unverifiedUser.isEmailVerified());
        assertNull(unverifiedUser.getVerificationCode());
        assertNull(unverifiedUser.getVerificationCodeExpiresAt());
        verify(userRepository).save(unverifiedUser);
        verify(refreshTokenRepository).save(any(RefreshToken.class));
    }

    @Test
    void verifyEmail_ShouldThrowBadRequest_WhenCodeIsInvalid() {
        VerifyEmailRequest request = VerifyEmailRequest.builder()
                .email("test.user@example.com")
                .code("000000")
                .build();

        User unverifiedUser = User.builder()
                .id(UUID.randomUUID())
                .email("test.user@example.com")
                .emailVerified(false)
                .verificationCode("123456")
                .verificationCodeExpiresAt(Instant.now().plus(10, ChronoUnit.MINUTES))
                .build();

        when(userRepository.findByEmail("test.user@example.com")).thenReturn(Optional.of(unverifiedUser));

        BadRequestException ex = assertThrows(BadRequestException.class, () ->
                authService.verifyEmail(request)
        );

        assertEquals("Invalid verification code", ex.getMessage());
        assertFalse(unverifiedUser.isEmailVerified());
        verify(refreshTokenRepository, never()).save(any(RefreshToken.class));
    }

    @Test
    void verifyEmail_ShouldThrowBadRequest_WhenCodeExpired() {
        VerifyEmailRequest request = VerifyEmailRequest.builder()
                .email("test.user@example.com")
                .code("123456")
                .build();

        User unverifiedUser = User.builder()
                .id(UUID.randomUUID())
                .email("test.user@example.com")
                .emailVerified(false)
                .verificationCode("123456")
                .verificationCodeExpiresAt(Instant.now().minus(5, ChronoUnit.MINUTES))
                .build();

        when(userRepository.findByEmail("test.user@example.com")).thenReturn(Optional.of(unverifiedUser));

        BadRequestException ex = assertThrows(BadRequestException.class, () ->
                authService.verifyEmail(request)
        );

        assertEquals("Verification code has expired. Please request a new one.", ex.getMessage());
        assertFalse(unverifiedUser.isEmailVerified());
        verify(refreshTokenRepository, never()).save(any(RefreshToken.class));
    }

    @Test
    void verifyEmail_ShouldThrowBadRequest_WhenAlreadyVerified() {
        VerifyEmailRequest request = VerifyEmailRequest.builder()
                .email("test.user@example.com")
                .code("123456")
                .build();

        User verifiedUser = User.builder()
                .email("test.user@example.com")
                .emailVerified(true)
                .build();

        when(userRepository.findByEmail("test.user@example.com")).thenReturn(Optional.of(verifiedUser));

        BadRequestException ex = assertThrows(BadRequestException.class, () ->
                authService.verifyEmail(request)
        );

        assertEquals("Email is already verified", ex.getMessage());
    }

    @Test
    void resendVerification_ShouldGenerateNewCodeAndExtendExpiry() {
        ResendVerificationRequest request = ResendVerificationRequest.builder()
                .email("  Test.User@example.com ")
                .build();

        User unverifiedUser = User.builder()
                .email("test.user@example.com")
                .emailVerified(false)
                .verificationCode("111111")
                .verificationCodeExpiresAt(Instant.now().minus(1, ChronoUnit.MINUTES))
                .build();

        when(userRepository.findByEmail("test.user@example.com")).thenReturn(Optional.of(unverifiedUser));

        AuthResponse response = authService.resendVerification(request);

        assertNotNull(response);
        assertTrue(response.isEmailVerificationRequired());
        assertEquals("test.user@example.com", response.getEmail());

        assertNotNull(unverifiedUser.getVerificationCode());
        assertEquals(6, unverifiedUser.getVerificationCode().length());
        assertTrue(unverifiedUser.getVerificationCodeExpiresAt().isAfter(Instant.now()));
        verify(userRepository).save(unverifiedUser);
    }

    @Test
    void resendVerification_ShouldThrowBadRequest_WhenAlreadyVerified() {
        ResendVerificationRequest request = ResendVerificationRequest.builder()
                .email("test.user@example.com")
                .build();

        User verifiedUser = User.builder()
                .email("test.user@example.com")
                .emailVerified(true)
                .build();

        when(userRepository.findByEmail("test.user@example.com")).thenReturn(Optional.of(verifiedUser));

        BadRequestException ex = assertThrows(BadRequestException.class, () ->
                authService.resendVerification(request)
        );

        assertEquals("Email is already verified", ex.getMessage());
    }
}
