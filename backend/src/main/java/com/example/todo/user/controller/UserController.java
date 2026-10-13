package com.example.todo.user.controller;

import com.example.todo.auth.security.CustomUserDetails;
import com.example.todo.exception.BadRequestException;
import com.example.todo.user.dto.UpdateTimezoneRequest;
import com.example.todo.user.dto.UserResponse;
import com.example.todo.user.entity.User;
import com.example.todo.user.repository.UserRepository;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.time.ZoneId;

@RestController
@RequestMapping("/api/v1/users")
@RequiredArgsConstructor
public class UserController {

    private final UserRepository userRepository;

    @GetMapping("/me")
    public UserResponse me(@AuthenticationPrincipal CustomUserDetails userDetails) {
        User user = userRepository.findById(userDetails.getUserId()).orElseThrow();

        return UserResponse.builder()
                .id(user.getId())
                .email(user.getEmail())
                .fullName(user.getFullName())
                .timezone(user.getTimezone())
                .createdAt(user.getCreatedAt())
                .build();
    }

    @PatchMapping("/me/timezone")
    public UserResponse updateTimezone(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @Valid @RequestBody UpdateTimezoneRequest request
    ) {
        try {
            ZoneId.of(request.getTimezone());
        } catch (Exception e) {
            throw new BadRequestException("Invalid timezone identifier: " + request.getTimezone());
        }

        User user = userRepository.findById(userDetails.getUserId()).orElseThrow();
        user.setTimezone(request.getTimezone());
        userRepository.save(user);

        return UserResponse.builder()
                .id(user.getId())
                .email(user.getEmail())
                .fullName(user.getFullName())
                .timezone(user.getTimezone())
                .createdAt(user.getCreatedAt())
                .build();
    }
}
