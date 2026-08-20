package com.printverse.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.Instant;

public final class MaterialDtos {

    private MaterialDtos() {
    }

    public record Request(
            @NotBlank @Size(max = 100) String name,
            @NotNull @DecimalMin("0.00") @Digits(integer = 12, fraction = 2) BigDecimal pricePerKg,
            @NotNull Boolean active) {
    }

    public record ActiveRequest(@NotNull Boolean active) {
    }

    public record Response(Long id, String name, BigDecimal pricePerKg, boolean active, Instant createdAt) {
    }
}
