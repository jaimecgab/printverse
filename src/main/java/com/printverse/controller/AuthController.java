package com.printverse.controller;

import com.printverse.dto.AuthDtos;
import com.printverse.service.AuthService;
import io.swagger.v3.oas.annotations.security.SecurityRequirements;
import jakarta.validation.Valid;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final AuthService service;

    public AuthController(AuthService service) {
        this.service = service;
    }

    @PostMapping("/login")
    @SecurityRequirements
    public AuthDtos.LoginResponse login(@Valid @RequestBody AuthDtos.LoginRequest request) {
        return service.login(request);
    }

    @GetMapping("/me")
    public AuthDtos.UserResponse me(Authentication authentication) {
        return service.currentUser(authentication.getName());
    }
}
