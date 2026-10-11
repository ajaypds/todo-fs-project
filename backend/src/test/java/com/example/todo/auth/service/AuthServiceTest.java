package com.example.todo.auth.service;

import com.example.todo.auth.dto.AuthResponse;
import com.example.todo.auth.dto.LoginRequest;
import com.example.todo.auth.dto.RegisterRequest;
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
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
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
    void register_ShouldNormalizeEmailAndIssueBothTokens() {
        when(userRepository.findByEmail("test.user@example.com")).thenReturn(Optional.empty());
        when(passwordEncoder.encode("securePassword123")).thenReturn("encodedPassword");
        when(jwtService.generateToken(any(), eq("test.user@example.com"))).thenReturn("access-token-xyz");
        when(jwtService.generateRefreshToken(any())).thenReturn("refresh-token-xyz");

        AuthResponse response = authService.register(registerRequest);

        assertNotNull(response);
        assertEquals("access-token-xyz", response.getToken());
        assertEquals("refresh-token-xyz", response.getRefreshToken());

        ArgumentCaptor<User> userCaptor = ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(userCaptor.capture());
        User savedUser = userCaptor.getValue();
        assertEquals("test.user@example.com", savedUser.getEmail());
        assertEquals("encodedPassword", savedUser.getPassword());
        assertEquals("Test User", savedUser.getFullName());
        assertEquals("Asia/Kolkata", savedUser.getTimezone());

        verify(workspaceRepository).save(any(Workspace.class));
        verify(workspaceMemberRepository).save(any(WorkspaceMember.class));
        verify(refreshTokenRepository).save(any(RefreshToken.class));
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
    void login_ShouldNormalizeEmailAndSucceed_WhenCredentialsAreValid() {
        LoginRequest loginRequest = new LoginRequest();
        loginRequest.setEmail("  TEST.USER@EXAMPLE.COM ");
        loginRequest.setPassword("securePassword123");

        User existingUser = User.builder()
                .id(UUID.randomUUID())
                .email("test.user@example.com")
                .password("encodedPassword")
                .fullName("Test User")
                .timezone("UTC")
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
    void login_ShouldThrowUnauthorizedException_WhenPasswordDoesNotMatch() {
        LoginRequest loginRequest = new LoginRequest();
        loginRequest.setEmail("test.user@example.com");
        loginRequest.setPassword("wrongPassword");

        User existingUser = User.builder()
                .id(UUID.randomUUID())
                .email("test.user@example.com")
                .password("encodedPassword")
                .build();

        when(userRepository.findByEmail("test.user@example.com")).thenReturn(Optional.of(existingUser));
        when(passwordEncoder.matches("wrongPassword", "encodedPassword")).thenReturn(false);

        UnauthorizedException exception = assertThrows(UnauthorizedException.class, () ->
                authService.login(loginRequest)
        );

        assertEquals("Invalid credentials", exception.getMessage());
        verify(refreshTokenRepository, never()).save(any(RefreshToken.class));
    }
}
