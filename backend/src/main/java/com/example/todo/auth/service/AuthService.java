package com.example.todo.auth.service;

import com.example.todo.auth.dto.AuthResponse;
import com.example.todo.auth.dto.LoginRequest;
import com.example.todo.auth.dto.RefreshRequest;
import com.example.todo.auth.dto.RegisterRequest;
import com.example.todo.auth.dto.ResendVerificationRequest;
import com.example.todo.auth.dto.VerifyEmailRequest;
import com.example.todo.auth.entity.RefreshToken;
import com.example.todo.auth.repository.RefreshTokenRepository;
import com.example.todo.exception.BadRequestException;
import com.example.todo.exception.UnauthorizedException;
import com.example.todo.auth.security.JwtService;
import com.example.todo.user.entity.User;
import com.example.todo.user.repository.UserRepository;
import com.example.todo.workspace.entity.Workspace;
import com.example.todo.workspace.entity.WorkspaceMember;
import com.example.todo.workspace.entity.WorkspaceRole;
import com.example.todo.workspace.repository.WorkspaceMemberRepository;
import com.example.todo.workspace.repository.WorkspaceRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.time.Instant;
import java.time.temporal.ChronoUnit;

@Slf4j
@Service
@RequiredArgsConstructor
public class AuthService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final RefreshTokenRepository refreshTokenRepository;
    private final WorkspaceRepository workspaceRepository;
    private final WorkspaceMemberRepository workspaceMemberRepository;

    @Transactional
    public AuthResponse register(RegisterRequest request) {

        String normalizedEmail = request.getEmail().trim().toLowerCase();

        boolean exists = userRepository.findByEmail(normalizedEmail).isPresent();

        if (exists) {
            throw new BadRequestException("Email already exists");
        }

        String timezone = (request.getTimezone() != null && !request.getTimezone().isBlank())
                ? request.getTimezone().trim()
                : "UTC";

        String verificationCode = generateVerificationCode();
        Instant codeExpiresAt = Instant.now().plus(15, ChronoUnit.MINUTES);

        User user = User.builder()
                .email(normalizedEmail)
                .password(passwordEncoder.encode(request.getPassword()))
                .fullName(request.getFullName().trim())
                .timezone(timezone)
                .emailVerified(false)
                .verificationCode(verificationCode)
                .verificationCodeExpiresAt(codeExpiresAt)
                .createdAt(Instant.now())
                .build();

        userRepository.save(user);

        // Create default personal workspace for the new user
        String workspaceName = (user.getFullName() != null && !user.getFullName().isBlank())
                ? user.getFullName() + "'s Workspace"
                : "Personal Workspace";

        Workspace defaultWorkspace = Workspace.builder()
                .name(workspaceName)
                .owner(user)
                .createdAt(Instant.now())
                .build();
        workspaceRepository.save(defaultWorkspace);

        WorkspaceMember ownerMember = WorkspaceMember.builder()
                .workspace(defaultWorkspace)
                .user(user)
                .role(WorkspaceRole.OWNER)
                .joinedAt(Instant.now())
                .build();
        workspaceMemberRepository.save(ownerMember);

        // Log OTP to console banner
        logVerificationCode(normalizedEmail, verificationCode);

        return AuthResponse.builder()
                .emailVerificationRequired(true)
                .email(normalizedEmail)
                .message("Verification code sent to email")
                .build();
    }

    @Transactional
    public AuthResponse login(LoginRequest request) {

        String normalizedEmail = request.getEmail().trim().toLowerCase();

        User user = userRepository.findByEmail(normalizedEmail)
                .orElseThrow(() -> new UnauthorizedException("Invalid credentials"));

        boolean passwordMatches = passwordEncoder.matches(
                request.getPassword(),
                user.getPassword()
        );

        if (!passwordMatches) {
            throw new UnauthorizedException("Invalid credentials");
        }

        if (!user.isEmailVerified()) {
            throw new UnauthorizedException("Email not verified. Please verify your email before logging in.");
        }

        String token = jwtService.generateToken(user.getId(), user.getEmail());
        String refreshToken = jwtService.generateRefreshToken(user);

        refreshTokenRepository.save(
            RefreshToken.builder()
                    .token(refreshToken)
                    .user(user)
                    .expiresAt(Instant.now().plus(7, ChronoUnit.DAYS))
                    .revoked(false)
                    .build()
        );

        return AuthResponse.builder()
                .token(token)
                .refreshToken(refreshToken)
                .build();
    }

    @Transactional
    public AuthResponse verifyEmail(VerifyEmailRequest request) {

        String normalizedEmail = request.getEmail().trim().toLowerCase();

        User user = userRepository.findByEmail(normalizedEmail)
                .orElseThrow(() -> new BadRequestException("User not found with provided email"));

        if (user.isEmailVerified()) {
            throw new BadRequestException("Email is already verified");
        }

        if (user.getVerificationCode() == null || !user.getVerificationCode().equals(request.getCode().trim())) {
            throw new BadRequestException("Invalid verification code");
        }

        if (user.getVerificationCodeExpiresAt() == null || user.getVerificationCodeExpiresAt().isBefore(Instant.now())) {
            throw new BadRequestException("Verification code has expired. Please request a new one.");
        }

        user.setEmailVerified(true);
        user.setVerificationCode(null);
        user.setVerificationCodeExpiresAt(null);
        userRepository.save(user);

        String token = jwtService.generateToken(user.getId(), user.getEmail());
        String refreshToken = jwtService.generateRefreshToken(user);

        refreshTokenRepository.save(
            RefreshToken.builder()
                    .token(refreshToken)
                    .user(user)
                    .expiresAt(Instant.now().plus(7, ChronoUnit.DAYS))
                    .revoked(false)
                    .build()
        );

        return AuthResponse.builder()
                .token(token)
                .refreshToken(refreshToken)
                .emailVerificationRequired(false)
                .email(user.getEmail())
                .message("Email verified successfully")
                .build();
    }

    @Transactional
    public AuthResponse resendVerification(ResendVerificationRequest request) {

        String normalizedEmail = request.getEmail().trim().toLowerCase();

        User user = userRepository.findByEmail(normalizedEmail)
                .orElseThrow(() -> new BadRequestException("User not found with provided email"));

        if (user.isEmailVerified()) {
            throw new BadRequestException("Email is already verified");
        }

        String verificationCode = generateVerificationCode();
        user.setVerificationCode(verificationCode);
        user.setVerificationCodeExpiresAt(Instant.now().plus(15, ChronoUnit.MINUTES));
        userRepository.save(user);

        logVerificationCode(normalizedEmail, verificationCode);

        return AuthResponse.builder()
                .emailVerificationRequired(true)
                .email(normalizedEmail)
                .message("A new verification code has been generated and sent to email")
                .build();
    }

    private String generateVerificationCode() {
        SecureRandom random = new SecureRandom();
        int code = 100000 + random.nextInt(900000);
        return String.valueOf(code);
    }

    private void logVerificationCode(String email, String code) {
        String banner = "\n" +
                "============================================================\n" +
                " [EMAIL VERIFICATION OTP]\n" +
                " To: " + email + "\n" +
                " Verification Code: " + code + "\n" +
                " Expires in: 15 minutes\n" +
                " (Email service is in console mode. Configure SMTP in application.yaml to send real emails)\n" +
                "============================================================";

        log.info(banner);
        System.out.println(banner);
    }

    @Transactional
    public AuthResponse refresh(String refreshToken) {

        RefreshToken storedToken = refreshTokenRepository
                        .findByToken(refreshToken)
                        .orElseThrow(() -> new UnauthorizedException("Invalid refresh token"));

        if (storedToken.isRevoked()) {
            throw new UnauthorizedException("Refresh token revoked");
        }

        if (storedToken.getExpiresAt().isBefore(Instant.now())) {

            throw new UnauthorizedException("Refresh token expired");
        }

        User user = storedToken.getUser();

        String newAccessToken = jwtService.generateToken(user.getId(), user.getEmail());

        return AuthResponse.builder()
                .token(newAccessToken)
                .refreshToken(refreshToken)
                .build();
    }
}