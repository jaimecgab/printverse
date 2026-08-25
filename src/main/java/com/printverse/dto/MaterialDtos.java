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
            @Size(max = 60) String materialType,
            @Size(max = 100) String brand,
            @Size(max = 80) String color,
            @DecimalMin("0.000") @Digits(integer = 11, fraction = 3) BigDecimal stockGrams,
            @DecimalMin("0.000") @Digits(integer = 11, fraction = 3) BigDecimal lowStockThresholdGrams,
            @Size(max = 1000) String notes,
            @NotNull Boolean active) {
    }

    public record ActiveRequest(@NotNull Boolean active) {
    }

    public record Response(Long id, String name, BigDecimal pricePerKg, String materialType,
                           String brand, String color, BigDecimal stockGrams,
                           BigDecimal lowStockThresholdGrams, String notes,
                           boolean active, Instant createdAt) {
    }
}
