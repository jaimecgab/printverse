package com.printverse.config;

import com.printverse.controller.AuthController;
import io.swagger.v3.oas.annotations.security.SecurityRequirements;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class OpenApiConfigTest {

    @Test
    void appliesBearerAuthenticationGloballyAndMarksLoginAsPublic() throws Exception {
        var openApi = new OpenApiConfig().printVerseOpenApi();

        assertThat(openApi.getComponents().getSecuritySchemes()).containsKey("bearerAuth");
        assertThat(openApi.getSecurity()).singleElement().satisfies(requirement ->
                assertThat(requirement).containsKey("bearerAuth"));
        assertThat(AuthController.class.getMethod("login", com.printverse.dto.AuthDtos.LoginRequest.class)
                .isAnnotationPresent(SecurityRequirements.class)).isTrue();
    }
}
