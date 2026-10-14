package com.example.todo.oauth;

import com.example.todo.auth.security.ApiKeyAuthenticationFilter;
import com.example.todo.auth.security.CustomUserDetailsService;
import com.example.todo.auth.security.JwtAuthenticationFilter;
import com.example.todo.auth.security.JwtService;
import com.example.todo.oauth.controller.OAuthController;
import com.example.todo.oauth.dto.TokenResponse;
import com.example.todo.oauth.entity.OAuthAuthorizationCode;
import com.example.todo.oauth.entity.OAuthClient;
import com.example.todo.oauth.service.OAuthService;
import com.example.todo.user.entity.User;
import com.example.todo.user.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.FilterType;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(
        controllers = OAuthController.class,
        excludeFilters = @ComponentScan.Filter(type = FilterType.ASSIGNABLE_TYPE, classes = {
                JwtAuthenticationFilter.class,
                ApiKeyAuthenticationFilter.class
        })
)
@AutoConfigureMockMvc(addFilters = false)
public class OAuthControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private OAuthService oAuthService;

    @MockitoBean
    private UserRepository userRepository;

    @MockitoBean
    private PasswordEncoder passwordEncoder;

    @MockitoBean
    private JwtService jwtService;

    @MockitoBean
    private CustomUserDetailsService customUserDetailsService;

    @Test
    void testAuthorizePage_RenderSuccess() throws Exception {
        OAuthClient client = OAuthClient.builder()
                .clientId("chatgpt-plugin")
                .clientName("ChatGPT TodoFlow Plugin")
                .redirectUris("https://chatgpt.com/aip/callback")
                .build();

        when(oAuthService.getClient("chatgpt-plugin")).thenReturn(client);

        mockMvc.perform(get("/oauth2/authorize")
                        .param("response_type", "code")
                        .param("client_id", "chatgpt-plugin")
                        .param("redirect_uri", "https://chatgpt.com/aip/callback")
                        .param("state", "xyz123")
                        .param("code_challenge", "E9Melhoa2OwvFrEMTJguCHaoeK1t8URWbuGJSstw-cM")
                        .param("code_challenge_method", "S256"))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.TEXT_HTML))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("Authorize ChatGPT TodoFlow Plugin")));
    }

    @Test
    void testAuthorizeSubmit_SuccessRedirects() throws Exception {
        OAuthClient client = OAuthClient.builder()
                .clientId("chatgpt-plugin")
                .clientName("ChatGPT TodoFlow Plugin")
                .redirectUris("https://chatgpt.com/aip/callback")
                .build();

        User user = User.builder()
                .id(UUID.randomUUID())
                .email("ajay@example.com")
                .password("hashed_password")
                .emailVerified(true)
                .build();

        OAuthAuthorizationCode code = OAuthAuthorizationCode.builder()
                .code("generated-code-456")
                .build();

        when(oAuthService.getClient("chatgpt-plugin")).thenReturn(client);
        when(userRepository.findByEmail("ajay@example.com")).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("mypassword", "hashed_password")).thenReturn(true);
        when(oAuthService.createAuthorizationCode(eq(user), eq("chatgpt-plugin"), eq("https://chatgpt.com/aip/callback"), any(), any(), any()))
                .thenReturn(code);

        mockMvc.perform(post("/oauth2/authorize")
                        .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                        .param("email", "ajay@example.com")
                        .param("password", "mypassword")
                        .param("client_id", "chatgpt-plugin")
                        .param("redirect_uri", "https://chatgpt.com/aip/callback")
                        .param("state", "xyz123"))
                .andExpect(status().isFound())
                .andExpect(header().string("Location", org.hamcrest.Matchers.containsString("https://chatgpt.com/aip/callback?code=generated-code-456&state=xyz123")));
    }

    @Test
    void testTokenEndpoint_FormUrlEncodedSuccess() throws Exception {
        TokenResponse tokenResponse = TokenResponse.builder()
                .accessToken("jwt-access-token-abc")
                .tokenType("Bearer")
                .expiresIn(900L)
                .refreshToken("refresh-token-xyz")
                .scope("read write all")
                .build();

        when(oAuthService.exchangeCode("authorization_code", "test-code", "https://chatgpt.com/aip/callback", "chatgpt-plugin", "secret", "verifier123"))
                .thenReturn(tokenResponse);

        mockMvc.perform(post("/oauth2/token")
                        .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                        .param("grant_type", "authorization_code")
                        .param("code", "test-code")
                        .param("redirect_uri", "https://chatgpt.com/aip/callback")
                        .param("client_id", "chatgpt-plugin")
                        .param("client_secret", "secret")
                        .param("code_verifier", "verifier123"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.access_token").value("jwt-access-token-abc"))
                .andExpect(jsonPath("$.token_type").value("Bearer"))
                .andExpect(jsonPath("$.expires_in").value(900))
                .andExpect(jsonPath("$.refresh_token").value("refresh-token-xyz"));
    }

    @Test
    void testDiscoveryEndpoint() throws Exception {
        when(oAuthService.getDiscoveryMetadata(any())).thenReturn(Map.of(
                "authorization_endpoint", "http://localhost/oauth2/authorize",
                "token_endpoint", "http://localhost/oauth2/token"
        ));

        mockMvc.perform(get("/.well-known/oauth-authorization-server"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.authorization_endpoint").value("http://localhost/oauth2/authorize"))
                .andExpect(jsonPath("$.token_endpoint").value("http://localhost/oauth2/token"));
    }
}
