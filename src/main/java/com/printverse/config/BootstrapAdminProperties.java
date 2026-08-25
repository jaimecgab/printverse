package com.printverse.config;

import jakarta.validation.constraints.AssertTrue;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties("app.bootstrap-admin")
public record BootstrapAdminProperties(String username, String displayName, String password, boolean required) {

    @AssertTrue(message = "APP_ADMIN_PASSWORD must not be blank when bootstrap admin is required")
    public boolean isPasswordPresentWhenRequired() {
        return !required || (password != null && !password.isBlank());
    }

    @AssertTrue(message = "APP_ADMIN_PASSWORD must contain at least 12 characters when set")
    public boolean isPasswordLengthValid() {
        return password == null || password.isBlank() || password.length() >= 12;
    }
}
