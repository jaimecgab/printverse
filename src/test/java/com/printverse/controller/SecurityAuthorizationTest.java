package com.printverse.controller;

import com.printverse.config.SecurityConfig;
import com.printverse.domain.AppUser;
import com.printverse.domain.UserRole;
import com.printverse.service.JwtTokenService;
import com.printverse.service.MaterialService;
import com.printverse.service.ProductionOrderService;
import com.printverse.service.QuoteService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = {MaterialController.class, QuoteController.class, ProductionOrderController.class}, properties = {
        "app.security.jwt.secret=test-only-jwt-signing-key-with-32-characters",
        "app.security.jwt.issuer=PrintVerse",
        "app.security.jwt.duration=8h"
})
@Import({SecurityConfig.class, JwtTokenService.class})
class SecurityAuthorizationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JwtTokenService tokenService;

    @MockitoBean
    private MaterialService materialService;

    @MockitoBean
    private QuoteService quoteService;

    @MockitoBean
    private ProductionOrderService productionOrderService;

    private String operatorToken;

    @BeforeEach
    void issueToken() {
        AppUser operator = new AppUser("operator", "unused-bcrypt-hash", "Operador", UserRole.OPERATOR, true);
        operatorToken = tokenService.issue(operator).accessToken();
    }

    @Test
    void protectedApiReturnsJsonProblemWithoutToken() throws Exception {
        mockMvc.perform(get("/api/materials"))
                .andExpect(status().isUnauthorized())
                .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.status").value(401))
                .andExpect(jsonPath("$.detail").value("A valid Bearer access token is required"));
    }

    @Test
    void validSignedTokenCanReadCatalog() throws Exception {
        mockMvc.perform(get("/api/materials").header("Authorization", bearer(operatorToken)))
                .andExpect(status().isOk());
    }

    @Test
    void operatorCannotMutateCatalog() throws Exception {
        mockMvc.perform(post("/api/materials")
                        .header("Authorization", bearer(operatorToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isForbidden())
                .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.status").value(403));
    }

    @Test
    void operatorCanMutateQuotesAndProduction() throws Exception {
        mockMvc.perform(post("/api/quotes/7/recalculate").header("Authorization", bearer(operatorToken)))
                .andExpect(status().isOk());
        mockMvc.perform(patch("/api/production-orders/9/status")
                        .header("Authorization", bearer(operatorToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"IN_PRODUCTION\"}"))
                .andExpect(status().isOk());

        verify(quoteService).recalculate(7L);
        verify(productionOrderService).changeStatus(9L, com.printverse.domain.ProductionOrderStatus.IN_PRODUCTION);
    }

    private static String bearer(String token) {
        return "Bearer " + token;
    }
}
