package com.printverse.service;

import com.printverse.domain.AppUser;
import com.printverse.domain.UserRole;
import com.printverse.dto.AuthDtos;
import com.printverse.repository.AppUserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

import java.time.Instant;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AuthServiceTest {

    @Test
    void authenticatesNormalizedUsernameAgainstBcryptHash() {
        AppUserRepository repository = mock(AppUserRepository.class);
        JwtTokenService tokenService = mock(JwtTokenService.class);
        BCryptPasswordEncoder encoder = new BCryptPasswordEncoder();
        AppUser user = new AppUser("admin", encoder.encode("correct-password"), "Admin", UserRole.ADMIN, true);
        AuthDtos.LoginResponse token = new AuthDtos.LoginResponse("signed-token", "Bearer",
                Instant.parse("2030-01-01T00:00:00Z"), AuthDtos.UserResponse.from(user));
        when(repository.findByUsername("admin")).thenReturn(Optional.of(user));
        when(tokenService.issue(user)).thenReturn(token);
        AuthService service = new AuthService(repository, encoder, tokenService);

        assertThat(service.login(new AuthDtos.LoginRequest("  ADMIN ", "correct-password"))).isEqualTo(token);
        verify(repository).findByUsername("admin");
    }

    @Test
    void rejectsWrongPasswordWithoutIssuingToken() {
        AppUserRepository repository = mock(AppUserRepository.class);
        JwtTokenService tokenService = mock(JwtTokenService.class);
        BCryptPasswordEncoder encoder = new BCryptPasswordEncoder();
        AppUser user = new AppUser("operator", encoder.encode("correct-password"), "Operator", UserRole.OPERATOR, true);
        when(repository.findByUsername("operator")).thenReturn(Optional.of(user));
        AuthService service = new AuthService(repository, encoder, tokenService);

        assertThatThrownBy(() -> service.login(new AuthDtos.LoginRequest("operator", "wrong-password")))
                .isInstanceOf(BadCredentialsException.class);
    }
}
