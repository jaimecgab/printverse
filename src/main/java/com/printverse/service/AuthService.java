package com.printverse.service;

import com.printverse.domain.AppUser;
import com.printverse.dto.AuthDtos;
import com.printverse.repository.AppUserRepository;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AuthService {

    private final AppUserRepository repository;
    private final PasswordEncoder passwordEncoder;
    private final JwtTokenService tokenService;

    public AuthService(AppUserRepository repository, PasswordEncoder passwordEncoder, JwtTokenService tokenService) {
        this.repository = repository;
        this.passwordEncoder = passwordEncoder;
        this.tokenService = tokenService;
    }

    @Transactional(readOnly = true)
    public AuthDtos.LoginResponse login(AuthDtos.LoginRequest request) {
        AppUser user = repository.findByUsername(AppUser.normalizeUsername(request.username()))
                .filter(AppUser::isActive)
                .orElseThrow(() -> new BadCredentialsException("Invalid username or password"));
        if (!passwordEncoder.matches(request.password(), user.getPasswordHash())) {
            throw new BadCredentialsException("Invalid username or password");
        }
        return tokenService.issue(user);
    }

    @Transactional(readOnly = true)
    public AuthDtos.UserResponse currentUser(String username) {
        return repository.findByUsername(AppUser.normalizeUsername(username))
                .filter(AppUser::isActive)
                .map(AuthDtos.UserResponse::from)
                .orElseThrow(() -> new BadCredentialsException("Authenticated user is no longer available"));
    }
}
