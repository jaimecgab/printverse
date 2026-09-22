package com.printverse.controller;

import com.printverse.config.SecurityConfig;
import com.printverse.domain.UserRole;
import com.printverse.dto.AuthDtos;
import com.printverse.exception.GlobalExceptionHandler;
import com.printverse.service.AuthService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = AuthController.class, properties = {
        "app.security.jwt.secret=test-only-jwt-signing-key-with-32-characters",
        "app.security.jwt.issuer=PrintVerse",
        "app.security.jwt.duration=8h"
})
@Import({SecurityConfig.class, GlobalExceptionHandler.class})
class AuthControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private AuthService authService;

    @Test
    void loginIsPublicAndReturnsTokenContract() throws Exception {
        when(authService.login(any())).thenReturn(new AuthDtos.LoginResponse("signed-token", "Bearer",
                Instant.parse("2030-01-01T00:00:00Z"),
                new AuthDtos.UserResponse("admin", "Administrador", UserRole.ADMIN)));

        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"username":"admin","password":"secret"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").value("signed-token"))
                .andExpect(jsonPath("$.tokenType").value("Bearer"))
                .andExpect(jsonPath("$.expiresAt").value("2030-01-01T00:00:00Z"))
                .andExpect(jsonPath("$.user.role").value("ADMIN"));
    }

    @Test
    void badCredentialsUseProblemDetail() throws Exception {
        when(authService.login(any())).thenThrow(new BadCredentialsException("hidden"));

        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"username":"admin","password":"wrong"}
                                """))
                .andExpect(status().isUnauthorized())
                .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.title").value("Authentication failed"))
                .andExpect(jsonPath("$.detail").value("Invalid username or password"));
    }
}
