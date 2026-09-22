package com.printverse.config;

import com.printverse.domain.AppUser;
import com.printverse.domain.UserRole;
import com.printverse.repository.AppUserRepository;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.boot.DefaultApplicationArguments;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import jakarta.validation.Validation;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AdminBootstrapConfigTest {

    @Test
    void createsNormalizedAdminWithBcryptHashOnce() throws Exception {
        AppUserRepository repository = mock(AppUserRepository.class);
        when(repository.findByUsername("root")).thenReturn(Optional.empty());
        var runner = new AdminBootstrapConfig().bootstrapAdmin(
                new BootstrapAdminProperties(" Root ", "Taller Admin", "bootstrap-password", false),
                repository, new BCryptPasswordEncoder());

        runner.run(new DefaultApplicationArguments(new String[0]));

        ArgumentCaptor<AppUser> captor = ArgumentCaptor.forClass(AppUser.class);
        verify(repository).save(captor.capture());
        AppUser saved = captor.getValue();
        assertThat(saved.getUsername()).isEqualTo("root");
        assertThat(saved.getRole()).isEqualTo(UserRole.ADMIN);
        assertThat(saved.isActive()).isTrue();
        assertThat(new BCryptPasswordEncoder().matches("bootstrap-password", saved.getPasswordHash())).isTrue();
    }

    @Test
    void doesNothingWhenPasswordIsEmpty() throws Exception {
        AppUserRepository repository = mock(AppUserRepository.class);
        var runner = new AdminBootstrapConfig().bootstrapAdmin(
                new BootstrapAdminProperties("admin", "Admin", "", false), repository, new BCryptPasswordEncoder());

        runner.run(new DefaultApplicationArguments(new String[0]));

        verify(repository, never()).findByUsername("admin");
        verify(repository, never()).save(org.mockito.ArgumentMatchers.any());
    }

    @Test
    void optionalBlankPasswordIsValidButAnyConfiguredPasswordRequiresTwelveCharacters() {
        try (var factory = Validation.buildDefaultValidatorFactory()) {
            var validator = factory.getValidator();

            assertThat(validator.validate(new BootstrapAdminProperties("admin", "Admin", "", false)))
                    .isEmpty();
            assertThat(validator.validate(new BootstrapAdminProperties("admin", "Admin", "short", false)))
                    .extracting(value -> value.getMessage())
                    .contains("APP_ADMIN_PASSWORD must contain at least 12 characters when set");
            assertThat(validator.validate(new BootstrapAdminProperties("admin", "Admin", "", true)))
                    .extracting(value -> value.getMessage())
                    .contains("APP_ADMIN_PASSWORD must not be blank when bootstrap admin is required");
        }
    }
}
