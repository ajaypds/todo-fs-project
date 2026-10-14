package com.example.todo.oauth.controller;

import com.example.todo.oauth.dto.OAuthErrorResponse;
import com.example.todo.oauth.dto.TokenResponse;
import com.example.todo.oauth.entity.OAuthAuthorizationCode;
import com.example.todo.oauth.entity.OAuthClient;
import com.example.todo.oauth.exception.OAuthException;
import com.example.todo.oauth.service.OAuthService;
import com.example.todo.user.entity.User;
import com.example.todo.user.repository.UserRepository;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.util.UriComponentsBuilder;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Map;

@Slf4j
@RestController
@RequiredArgsConstructor
public class OAuthController {

    private final OAuthService oAuthService;
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    /**
     * OAuth 2.1 Authorization Endpoint (GET).
     * Renders the interactive login / consent page for ChatGPT.
     */
    @GetMapping(value = {"/oauth2/authorize", "/oauth/authorize", "/api/v1/oauth2/authorize", "/api/v1/oauth/authorize"}, produces = MediaType.TEXT_HTML_VALUE)
    public ResponseEntity<String> authorizePage(
            @RequestParam(name = "response_type", required = false) String responseType,
            @RequestParam(name = "client_id", required = false) String clientId,
            @RequestParam(name = "redirect_uri", required = false) String redirectUri,
            @RequestParam(name = "scope", required = false) String scope,
            @RequestParam(name = "state", required = false) String state,
            @RequestParam(name = "code_challenge", required = false) String codeChallenge,
            @RequestParam(name = "code_challenge_method", required = false) String codeChallengeMethod,
            HttpServletRequest request
    ) {
        if (!"code".equals(responseType)) {
            return ResponseEntity.badRequest().body(renderErrorPage("Invalid Request", "response_type must be 'code' in OAuth 2.1"));
        }

        OAuthClient client;
        try {
            client = oAuthService.getClient(clientId);
        } catch (OAuthException ex) {
            return ResponseEntity.badRequest().body(renderErrorPage("Client Error", ex.getErrorDescription()));
        }

        if (redirectUri == null || !client.isRedirectUriAllowed(redirectUri)) {
            return ResponseEntity.badRequest().body(renderErrorPage("Redirect URI Error", "The redirect_uri provided is not registered for this client."));
        }

        String actionUrl = request.getRequestURI();
        String html = renderAuthorizeForm(actionUrl, client.getClientName(), clientId, redirectUri, scope, state, codeChallenge, codeChallengeMethod, null, "");
        return ResponseEntity.ok(html);
    }

    /**
     * OAuth 2.1 Authorization Form Submission (POST).
     * Authenticates user credentials, generates authorization code, and redirects browser back to ChatGPT.
     */
    @PostMapping(value = {"/oauth2/authorize", "/oauth/authorize", "/api/v1/oauth2/authorize", "/api/v1/oauth/authorize"}, consumes = MediaType.APPLICATION_FORM_URLENCODED_VALUE)
    public ResponseEntity<?> handleAuthorizeSubmit(
            @RequestParam("email") String email,
            @RequestParam("password") String password,
            @RequestParam("client_id") String clientId,
            @RequestParam("redirect_uri") String redirectUri,
            @RequestParam(name = "scope", required = false) String scope,
            @RequestParam(name = "state", required = false) String state,
            @RequestParam(name = "code_challenge", required = false) String codeChallenge,
            @RequestParam(name = "code_challenge_method", required = false) String codeChallengeMethod,
            HttpServletRequest request
    ) {
        OAuthClient client;
        try {
            client = oAuthService.getClient(clientId);
        } catch (OAuthException ex) {
            return ResponseEntity.badRequest().body(renderErrorPage("Client Error", ex.getErrorDescription()));
        }

        if (!client.isRedirectUriAllowed(redirectUri)) {
            return ResponseEntity.badRequest().body(renderErrorPage("Redirect URI Error", "The redirect_uri is not allowed."));
        }

        String normalizedEmail = email.trim().toLowerCase();
        User user = userRepository.findByEmail(normalizedEmail).orElse(null);

        boolean authenticated = user != null && passwordEncoder.matches(password, user.getPassword());
        if (!authenticated) {
            String html = renderAuthorizeForm(request.getRequestURI(), client.getClientName(), clientId, redirectUri, scope, state, codeChallenge, codeChallengeMethod, "Invalid email or password. Please try again.", normalizedEmail);
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).contentType(MediaType.TEXT_HTML).body(html);
        }

        if (!user.isEmailVerified()) {
            String html = renderAuthorizeForm(request.getRequestURI(), client.getClientName(), clientId, redirectUri, scope, state, codeChallenge, codeChallengeMethod, "Your email is not verified yet. Please verify your email first.", normalizedEmail);
            return ResponseEntity.status(HttpStatus.FORBIDDEN).contentType(MediaType.TEXT_HTML).body(html);
        }

        OAuthAuthorizationCode authCode = oAuthService.createAuthorizationCode(
                user,
                client.getClientId(),
                redirectUri,
                scope,
                codeChallenge,
                codeChallengeMethod
        );

        UriComponentsBuilder uriBuilder = UriComponentsBuilder.fromUriString(redirectUri)
                .queryParam("code", authCode.getCode());

        if (state != null && !state.isBlank()) {
            uriBuilder.queryParam("state", state);
        }

        String redirectUrl = uriBuilder.build().toUriString();
        log.info("OAuth 2.1: Authorizing user {} for client {}. Redirecting to callback.", user.getEmail(), clientId);

        return ResponseEntity.status(HttpStatus.FOUND)
                .location(URI.create(redirectUrl))
                .build();
    }

    /**
     * OAuth 2.1 Token Endpoint (POST).
     * Accepts authorization code and PKCE code_verifier, and exchanges them for a JWT access token.
     */
    @PostMapping(
            value = {"/oauth2/token", "/oauth/token", "/api/v1/oauth2/token", "/api/v1/oauth/token"},
            consumes = MediaType.APPLICATION_FORM_URLENCODED_VALUE,
            produces = MediaType.APPLICATION_JSON_VALUE
    )
    public ResponseEntity<?> tokenEndpointForm(
            @RequestParam Map<String, String> formParams,
            @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authHeader
    ) {
        return handleTokenRequest(formParams, authHeader);
    }

    @PostMapping(
            value = {"/oauth2/token", "/oauth/token", "/api/v1/oauth2/token", "/api/v1/oauth/token"},
            consumes = MediaType.APPLICATION_JSON_VALUE,
            produces = MediaType.APPLICATION_JSON_VALUE
    )
    public ResponseEntity<?> tokenEndpointJson(
            @RequestBody Map<String, String> jsonParams,
            @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authHeader
    ) {
        return handleTokenRequest(jsonParams, authHeader);
    }

    private ResponseEntity<?> handleTokenRequest(
            Map<String, String> params,
            String authHeader
    ) {

        String grantType = params.get("grant_type");
        String code = params.get("code");
        String redirectUri = params.get("redirect_uri");
        String clientId = params.get("client_id");
        String clientSecret = params.get("client_secret");
        String codeVerifier = params.get("code_verifier");
        String refreshToken = params.get("refresh_token");

        // Parse HTTP Basic Auth header if client credentials are sent in header (RFC 6749 Section 2.3.1)
        if (authHeader != null && authHeader.startsWith("Basic ")) {
            try {
                String base64Credentials = authHeader.substring(6).trim();
                byte[] decoded = Base64.getDecoder().decode(base64Credentials);
                String credentials = new String(decoded, StandardCharsets.UTF_8);
                String[] parts = credentials.split(":", 2);
                if (parts.length >= 1 && (clientId == null || clientId.isBlank())) {
                    clientId = parts[0];
                }
                if (parts.length == 2 && (clientSecret == null || clientSecret.isBlank())) {
                    clientSecret = parts[1];
                }
            } catch (Exception e) {
                log.warn("Failed to parse Basic Auth header in token endpoint", e);
            }
        }

        try {
            if ("authorization_code".equals(grantType)) {
                TokenResponse response = oAuthService.exchangeCode(grantType, code, redirectUri, clientId, clientSecret, codeVerifier);
                return ResponseEntity.ok()
                        .header("Cache-Control", "no-store")
                        .header("Pragma", "no-cache")
                        .body(response);
            } else if ("refresh_token".equals(grantType)) {
                TokenResponse response = oAuthService.refreshToken(grantType, refreshToken, clientId, clientSecret);
                return ResponseEntity.ok()
                        .header("Cache-Control", "no-store")
                        .header("Pragma", "no-cache")
                        .body(response);
            } else {
                return ResponseEntity.badRequest().body(OAuthErrorResponse.builder()
                        .error("unsupported_grant_type")
                        .errorDescription("Supported grant_types are authorization_code and refresh_token")
                        .build());
            }
        } catch (OAuthException ex) {
            return ResponseEntity.status(ex.getStatus()).body(OAuthErrorResponse.builder()
                    .error(ex.getError())
                    .errorDescription(ex.getErrorDescription())
                    .build());
        } catch (Exception ex) {
            log.error("Unhandled error in token endpoint", ex);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(OAuthErrorResponse.builder()
                    .error("server_error")
                    .errorDescription("An internal error occurred during token processing")
                    .build());
        }
    }

    /**
     * OAuth 2.0 / 2.1 Token Revocation Endpoint (RFC 7009).
     */
    @PostMapping(value = {"/oauth2/revoke", "/oauth/revoke", "/api/v1/oauth2/revoke", "/api/v1/oauth/revoke"})
    public ResponseEntity<?> revokeEndpoint(@RequestParam(name = "token", required = false) String token) {
        oAuthService.revokeToken(token);
        return ResponseEntity.ok().build();
    }

    /**
     * RFC 8414 Authorization Server Metadata / OpenID Discovery Endpoint.
     */
    @GetMapping(value = {"/.well-known/oauth-authorization-server", "/.well-known/openid-configuration"}, produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<Map<String, Object>> discoveryEndpoint(HttpServletRequest request) {
        String scheme = request.getHeader("X-Forwarded-Proto") != null ? request.getHeader("X-Forwarded-Proto") : request.getScheme();
        String host = request.getHeader("Host") != null ? request.getHeader("Host") : request.getServerName();
        String baseUrl = scheme + "://" + host;
        return ResponseEntity.ok(oAuthService.getDiscoveryMetadata(baseUrl));
    }

    /**
     * RFC 9728 OAuth 2.0 Protected Resource Metadata Endpoint.
     */
    @GetMapping(
            value = {
                    "/.well-known/oauth-protected-resource",
                    "/.well-known/oauth-protected-resource/**",
                    "/mcp/.well-known/oauth-protected-resource"
            },
            produces = MediaType.APPLICATION_JSON_VALUE
    )
    public ResponseEntity<Map<String, Object>> protectedResourceMetadataEndpoint(HttpServletRequest request) {
        String scheme = request.getHeader("X-Forwarded-Proto") != null ? request.getHeader("X-Forwarded-Proto") : request.getScheme();
        String host = request.getHeader("Host") != null ? request.getHeader("Host") : request.getServerName();
        String baseUrl = scheme + "://" + host;
        return ResponseEntity.ok(oAuthService.getProtectedResourceMetadata(baseUrl));
    }

    // --- HTML Rendering Helpers ---

    private String renderAuthorizeForm(
            String actionUrl,
            String clientName,
            String clientId,
            String redirectUri,
            String scope,
            String state,
            String codeChallenge,
            String codeChallengeMethod,
            String errorMessage,
            String currentEmail
    ) {
        String cancelUrl = redirectUri + (redirectUri.contains("?") ? "&" : "?") + "error=access_denied" + (state != null && !state.isBlank() ? "&state=" + state : "");

        String errorBanner = (errorMessage != null && !errorMessage.isBlank())
                ? "<div class=\"alert-error\"><svg width=\"18\" height=\"18\" viewBox=\"0 0 24 24\" fill=\"none\" stroke=\"currentColor\" stroke-width=\"2\"><circle cx=\"12\" cy=\"12\" r=\"10\"/><line x1=\"12\" y1=\"8\" x2=\"12\" y2=\"12\"/><line x1=\"12\" y1=\"16\" x2=\"12.01\" y2=\"16\"/></svg><span>" + escapeHtml(errorMessage) + "</span></div>"
                : "";

        return "<!DOCTYPE html>\n" +
                "<html lang=\"en\">\n" +
                "<head>\n" +
                "  <meta charset=\"UTF-8\">\n" +
                "  <meta name=\"viewport\" content=\"width=device-width, initial-scale=1.0\">\n" +
                "  <title>Authorize " + escapeHtml(clientName) + " | TodoFlow</title>\n" +
                "  <style>\n" +
                "    :root { --primary: #3b82f6; --primary-hover: #2563eb; --bg: #0f172a; --card-bg: #1e293b; --text: #f8fafc; --text-muted: #94a3b8; --border: #334155; --danger: #ef4444; }\n" +
                "    body { margin: 0; font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, sans-serif; background: var(--bg); color: var(--text); display: flex; align-items: center; justify-content: center; min-height: 100vh; padding: 16px; box-sizing: border-box; }\n" +
                "    .card { background: var(--card-bg); border: 1px solid var(--border); border-radius: 16px; padding: 32px; width: 100%; max-width: 440px; box-shadow: 0 20px 25px -5px rgba(0, 0, 0, 0.5), 0 8px 10px -6px rgba(0, 0, 0, 0.5); }\n" +
                "    .header { text-align: center; margin-bottom: 24px; }\n" +
                "    .logo { display: inline-flex; align-items: center; justify-content: center; width: 52px; height: 52px; border-radius: 12px; background: linear-gradient(135deg, #3b82f6, #6366f1); margin-bottom: 16px; box-shadow: 0 0 20px rgba(59, 130, 246, 0.4); }\n" +
                "    .logo svg { width: 28px; height: 28px; color: white; }\n" +
                "    h1 { font-size: 20px; font-weight: 700; margin: 0 0 8px; }\n" +
                "    p.subtitle { color: var(--text-muted); font-size: 14px; margin: 0; line-height: 1.5; }\n" +
                "    .permissions { background: #0f172a80; border: 1px solid var(--border); border-radius: 12px; padding: 14px; margin: 20px 0; }\n" +
                "    .permissions-title { font-size: 12px; text-transform: uppercase; letter-spacing: 0.05em; color: var(--text-muted); font-weight: 600; margin-bottom: 8px; }\n" +
                "    .perm-item { display: flex; align-items: center; gap: 10px; font-size: 13px; color: #e2e8f0; margin-bottom: 6px; }\n" +
                "    .perm-item svg { color: #10b981; flex-shrink: 0; }\n" +
                "    .form-group { margin-bottom: 16px; text-align: left; }\n" +
                "    label { display: block; font-size: 13px; font-weight: 500; margin-bottom: 6px; color: #cbd5e1; }\n" +
                "    input[type=\"email\"], input[type=\"password\"] { width: 100%; box-sizing: border-box; background: #0f172a; border: 1px solid var(--border); border-radius: 8px; padding: 10px 14px; color: var(--text); font-size: 14px; outline: none; transition: border-color 0.2s; }\n" +
                "    input[type=\"email\"]:focus, input[type=\"password\"]:focus { border-color: var(--primary); box-shadow: 0 0 0 2px rgba(59, 130, 246, 0.2); }\n" +
                "    .actions { display: flex; flex-direction: column; gap: 10px; margin-top: 24px; }\n" +
                "    .btn-primary { background: var(--primary); color: white; border: none; border-radius: 8px; padding: 12px; font-size: 14px; font-weight: 600; cursor: pointer; transition: background 0.2s; width: 100%; }\n" +
                "    .btn-primary:hover { background: var(--primary-hover); }\n" +
                "    .btn-cancel { background: transparent; color: var(--text-muted); border: 1px solid var(--border); border-radius: 8px; padding: 10px; font-size: 14px; text-decoration: none; text-align: center; font-weight: 500; transition: all 0.2s; }\n" +
                "    .btn-cancel:hover { background: #334155; color: var(--text); }\n" +
                "    .alert-error { background: rgba(239, 68, 68, 0.15); border: 1px solid var(--danger); border-radius: 8px; padding: 10px 12px; font-size: 13px; color: #fca5a5; display: flex; align-items: center; gap: 8px; margin-bottom: 16px; }\n" +
                "    .footer { text-align: center; margin-top: 20px; font-size: 11px; color: var(--text-muted); }\n" +
                "  </style>\n" +
                "</head>\n" +
                "<body>\n" +
                "  <div class=\"card\">\n" +
                "    <div class=\"header\">\n" +
                "      <div class=\"logo\">\n" +
                "        <svg viewBox=\"0 0 24 24\" fill=\"none\" stroke=\"currentColor\" stroke-width=\"2\"><polyline points=\"9 11 12 14 22 4\"/><path d=\"M21 12v7a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2V5a2 2 0 0 1 2-2h11\"/></svg>\n" +
                "      </div>\n" +
                "      <h1>Authorize Connection</h1>\n" +
                "      <p class=\"subtitle\"><strong>" + escapeHtml(clientName) + "</strong> is requesting permission to access your TodoFlow account.</p>\n" +
                "    </div>\n" +
                "    <div class=\"permissions\">\n" +
                "      <div class=\"permissions-title\">Permissions Granted</div>\n" +
                "      <div class=\"perm-item\"><svg width=\"16\" height=\"16\" viewBox=\"0 0 24 24\" fill=\"none\" stroke=\"currentColor\" stroke-width=\"2.5\"><polyline points=\"20 6 9 17 4 12\"/></svg><span>Create and manage your tasks and checklists</span></div>\n" +
                "      <div class=\"perm-item\"><svg width=\"16\" height=\"16\" viewBox=\"0 0 24 24\" fill=\"none\" stroke=\"currentColor\" stroke-width=\"2.5\"><polyline points=\"20 6 9 17 4 12\"/></svg><span>Organize tasks into projects and workspaces</span></div>\n" +
                "    </div>\n" +
                "    " + errorBanner + "\n" +
                "    <form method=\"POST\" action=\"" + escapeHtml(actionUrl) + "\">\n" +
                "      <input type=\"hidden\" name=\"client_id\" value=\"" + escapeHtml(clientId) + "\">\n" +
                "      <input type=\"hidden\" name=\"redirect_uri\" value=\"" + escapeHtml(redirectUri) + "\">\n" +
                "      <input type=\"hidden\" name=\"state\" value=\"" + (state != null ? escapeHtml(state) : "") + "\">\n" +
                "      <input type=\"hidden\" name=\"scope\" value=\"" + (scope != null ? escapeHtml(scope) : "") + "\">\n" +
                "      <input type=\"hidden\" name=\"code_challenge\" value=\"" + (codeChallenge != null ? escapeHtml(codeChallenge) : "") + "\">\n" +
                "      <input type=\"hidden\" name=\"code_challenge_method\" value=\"" + (codeChallengeMethod != null ? escapeHtml(codeChallengeMethod) : "") + "\">\n" +
                "      <div class=\"form-group\">\n" +
                "        <label for=\"email\">TodoFlow Account Email</label>\n" +
                "        <input type=\"email\" id=\"email\" name=\"email\" value=\"" + escapeHtml(currentEmail) + "\" required autofocus placeholder=\"you@example.com\">\n" +
                "      </div>\n" +
                "      <div class=\"form-group\">\n" +
                "        <label for=\"password\">Password</label>\n" +
                "        <input type=\"password\" id=\"password\" name=\"password\" required placeholder=\"Enter your password\">\n" +
                "      </div>\n" +
                "      <div class=\"actions\">\n" +
                "        <button type=\"submit\" class=\"btn-primary\">Authorize ChatGPT</button>\n" +
                "        <a href=\"" + escapeHtml(cancelUrl) + "\" class=\"btn-cancel\">Cancel</a>\n" +
                "      </div>\n" +
                "    </form>\n" +
                "    <div class=\"footer\">TodoFlow OAuth 2.1 • Secured with PKCE</div>\n" +
                "  </div>\n" +
                "</body>\n" +
                "</html>";
    }

    private String renderErrorPage(String title, String description) {
        return "<!DOCTYPE html>\n" +
                "<html lang=\"en\">\n" +
                "<head>\n" +
                "  <meta charset=\"UTF-8\">\n" +
                "  <title>" + escapeHtml(title) + " | TodoFlow</title>\n" +
                "  <style>\n" +
                "    body { margin: 0; font-family: sans-serif; background: #0f172a; color: #f8fafc; display: flex; align-items: center; justify-content: center; min-height: 100vh; }\n" +
                "    .card { background: #1e293b; border: 1px solid #ef4444; border-radius: 12px; padding: 28px; max-width: 400px; text-align: center; }\n" +
                "    h2 { color: #ef4444; margin-top: 0; }\n" +
                "    p { color: #94a3b8; font-size: 14px; }\n" +
                "  </style>\n" +
                "</head>\n" +
                "<body>\n" +
                "  <div class=\"card\">\n" +
                "    <h2>" + escapeHtml(title) + "</h2>\n" +
                "    <p>" + escapeHtml(description) + "</p>\n" +
                "  </div>\n" +
                "</body>\n" +
                "</html>";
    }

    private String escapeHtml(String text) {
        if (text == null) return "";
        return text.replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;")
                .replace("'", "&#39;");
    }
}
