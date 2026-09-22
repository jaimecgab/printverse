package com.printverse.exception;

import org.junit.jupiter.api.Test;
import org.springframework.dao.PessimisticLockingFailureException;
import org.springframework.http.HttpStatus;

import static org.assertj.core.api.Assertions.assertThat;

class GlobalExceptionHandlerTest {

    private final GlobalExceptionHandler handler = new GlobalExceptionHandler();

    @Test
    void reportsCalculationOverflowAsBadRequest() {
        var detail = handler.handleBusinessRule(new BusinessRuleException("Quote total exceeds the limit"));

        assertThat(detail.getStatus()).isEqualTo(HttpStatus.BAD_REQUEST.value());
        assertThat(detail.getDetail()).isEqualTo("Quote total exceeds the limit");
    }

    @Test
    void usesAGenericResourceMessageForLockConflicts() {
        var detail = handler.handleConcurrentUpdate(new PessimisticLockingFailureException("locked"));

        assertThat(detail.getStatus()).isEqualTo(HttpStatus.CONFLICT.value());
        assertThat(detail.getDetail()).contains("resource").doesNotContain("quote");
    }
}
