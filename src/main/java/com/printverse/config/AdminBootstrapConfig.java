package com.printverse.config;

import com.printverse.domain.AppUser;
import com.printverse.domain.UserRole;
import com.printverse.repository.AppUserRepository;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.crypto.password.PasswordEncoder;

@Configuration
@EnableConfigurationProperties(BootstrapAdminProperties.class)
public class AdminBootstrapConfig {

    @Bean
    ApplicationRunner bootstrapAdmin(BootstrapAdminProperties properties, AppUserRepository repository,
                                     PasswordEncoder passwordEncoder) {
        return arguments -> {
            if (properties.password() == null || properties.password().isBlank()) {
                return;
            }
            String username = AppUser.normalizeUsername(properties.username());
            if (username.isBlank()) {
                throw new IllegalStateException("APP_ADMIN_USERNAME must not be blank when APP_ADMIN_PASSWORD is set");
            }
            if (properties.displayName() == null || properties.displayName().isBlank()) {
                throw new IllegalStateException("APP_ADMIN_DISPLAY_NAME must not be blank when APP_ADMIN_PASSWORD is set");
            }
            if (repository.findByUsername(username).isEmpty()) {
                repository.save(new AppUser(username, passwordEncoder.encode(properties.password()),
                        properties.displayName(), UserRole.ADMIN, true));
            }
        };
    }
}
