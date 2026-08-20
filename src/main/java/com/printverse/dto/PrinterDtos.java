package com.printverse.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.Instant;

public final class PrinterDtos {

    private PrinterDtos() {
    }

    public record Request(
            @NotBlank @Size(max = 100) String name,
            @Size(max = 100) String model,
            @NotNull @DecimalMin("0.00") @Digits(integer = 12, fraction = 2) BigDecimal costPerHour,
            @NotNull Boolean active) {
    }

    public record ActiveRequest(@NotNull Boolean active) {
    }

    public record Response(Long id, String name, String model, BigDecimal costPerHour,
                           boolean active, Instant createdAt) {
    }
}
