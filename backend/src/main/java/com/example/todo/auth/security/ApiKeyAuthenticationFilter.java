package com.example.todo.auth.security;

import com.example.todo.apikey.service.ApiKeyService;
import com.example.todo.user.entity.User;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Optional;

@Component
@RequiredArgsConstructor
public class ApiKeyAuthenticationFilter extends OncePerRequestFilter {

    private final ApiKeyService apiKeyService;

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain
    ) throws ServletException, IOException {

        if (SecurityContextHolder.getContext().getAuthentication() == null) {
            String apiKey = extractApiKey(request);

            if (apiKey != null) {
                Optional<User> userOpt = apiKeyService.validateKey(apiKey);
                if (userOpt.isPresent()) {
                    CustomUserDetails userDetails = new CustomUserDetails(userOpt.get());
                    UsernamePasswordAuthenticationToken authentication =
                            new UsernamePasswordAuthenticationToken(
                                    userDetails,
                                    null,
                                    userDetails.getAuthorities()
                            );
                    authentication.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
                    SecurityContextHolder.getContext().setAuthentication(authentication);
                }
            }
        }

        filterChain.doFilter(request, response);
    }

    private String extractApiKey(HttpServletRequest request) {
        String xApiKey = request.getHeader("X-API-Key");
        if (xApiKey != null && !xApiKey.isBlank()) {
            String key = xApiKey.trim();
            if (key.regionMatches(true, 0, "Bearer ", 0, 7)) {
                key = key.substring(7).trim();
            }
            return key;
        }

        String authHeader = request.getHeader("Authorization");
        if (authHeader != null && !authHeader.isBlank()) {
            String token = authHeader.trim();
            if (token.regionMatches(true, 0, "Bearer ", 0, 7)) {
                token = token.substring(7).trim();
            } else if (token.regionMatches(true, 0, "ApiKey ", 0, 7)) {
                token = token.substring(7).trim();
            }
            // Strip accidental duplicate "Bearer " if user pasted with prefix
            if (token.regionMatches(true, 0, "Bearer ", 0, 7)) {
                token = token.substring(7).trim();
            }
            if (token.startsWith("todo_live_")) {
                return token;
            }
        }

        return null;
    }
}
