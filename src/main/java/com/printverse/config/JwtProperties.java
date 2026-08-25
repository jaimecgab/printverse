package com.printverse.config;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import java.time.Duration;

@Validated
@ConfigurationProperties("app.security.jwt")
public record JwtProperties(
        @NotBlank @Size(min = 32) String secret,
        @NotBlank String issuer,
        @NotNull Duration duration) {

    public JwtProperties {
        if (duration != null && duration.compareTo(Duration.ofMinutes(1)) < 0) {
            throw new IllegalArgumentException("JWT duration must be at least one minute");
        }
    }
}
