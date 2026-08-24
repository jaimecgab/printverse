package com.printverse.dto;

import com.printverse.domain.QuoteStatus;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.FutureOrPresent;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

public final class QuoteDtos {

    private QuoteDtos() {
    }

    public record CreateRequest(
            @NotNull Long customerId,
            @NotNull @FutureOrPresent LocalDate validUntil,
            @FutureOrPresent LocalDate estimatedDeliveryDate,
            @DecimalMin("0.00") @DecimalMax("100.00") @Digits(integer = 3, fraction = 4) BigDecimal depositPercentage,
            @Size(max = 3000) String notes,
            @NotNull @DecimalMin("0.00") @Digits(integer = 3, fraction = 4) BigDecimal markupPercentage,
            @DecimalMin("0.00") @DecimalMax("100.00") @Digits(integer = 3, fraction = 4) BigDecimal discountPercentage,
            @NotNull Boolean taxEnabled,
            @NotNull @DecimalMin("0.00") @DecimalMax("100.00") @Digits(integer = 3, fraction = 4) BigDecimal taxPercentage) {
    }

    public record UpdateRequest(
            @NotNull @FutureOrPresent LocalDate validUntil,
            @FutureOrPresent LocalDate estimatedDeliveryDate,
            @DecimalMin("0.00") @DecimalMax("100.00") @Digits(integer = 3, fraction = 4) BigDecimal depositPercentage,
            @Size(max = 3000) String notes,
            @NotNull @DecimalMin("0.00") @Digits(integer = 3, fraction = 4) BigDecimal markupPercentage,
            @NotNull @DecimalMin("0.00") @DecimalMax("100.00") @Digits(integer = 3, fraction = 4) BigDecimal discountPercentage,
            @NotNull Boolean taxEnabled,
            @NotNull @DecimalMin("0.00") @DecimalMax("100.00") @Digits(integer = 3, fraction = 4) BigDecimal taxPercentage) {
    }

    public record ItemRequest(
            @NotBlank @Size(max = 200) String name,
            @Positive int quantity,
            @NotNull Long materialId,
            @NotNull Long printerId,
            @NotNull @PositiveOrZero @Digits(integer = 11, fraction = 3) BigDecimal weightGrams,
            @NotNull @PositiveOrZero Integer printTimeMinutes,
            @NotNull @DecimalMin("0.00") @DecimalMax("100.00") @Digits(integer = 3, fraction = 4) BigDecimal failureRiskPercentage,
            @DecimalMin("0.00") @Digits(integer = 12, fraction = 2) BigDecimal manualUnitPrice) {
    }

    public record ChargeRequest(
            @NotBlank @Size(max = 200) String description,
            @NotNull @DecimalMin("0.00") @Digits(integer = 12, fraction = 2) BigDecimal amount) {
    }

    public record StatusRequest(@NotNull QuoteStatus status) {
    }

    public record CustomerSummary(Long id, String name, String phone, String email) {
    }

    public record MaterialSummary(Long id, String name) {
    }

    public record PrinterSummary(Long id, String name, String model) {
    }

    public record ChargeResponse(Long id, String description, BigDecimal amount) {
    }

    public record ItemResponse(
            Long id,
            String name,
            int quantity,
            MaterialSummary material,
            PrinterSummary printer,
            BigDecimal weightGrams,
            int printTimeMinutes,
            BigDecimal failureRiskPercentage,
            BigDecimal materialPricePerKgSnapshot,
            BigDecimal printerCostPerHourSnapshot,
            BigDecimal materialCostUnit,
            BigDecimal machineCostUnit,
            BigDecimal failureRiskCostUnit,
            BigDecimal additionalChargesUnit,
            BigDecimal internalCostUnit,
            BigDecimal suggestedPriceUnit,
            BigDecimal manualUnitPrice,
            BigDecimal finalUnitPrice,
            BigDecimal itemSubtotal,
            List<ChargeResponse> additionalCharges) {
    }

    public record SummaryResponse(
            Long id,
            String quoteNumber,
            CustomerSummary customer,
            QuoteStatus status,
            Instant createdAt,
            LocalDate validUntil,
            BigDecimal total,
            BigDecimal estimatedProfit,
            BigDecimal realMarginPercentage) {
    }

    public record Response(
            Long id,
            String quoteNumber,
            CustomerSummary customer,
            QuoteStatus status,
            Instant createdAt,
            LocalDate validUntil,
            LocalDate estimatedDeliveryDate,
            BigDecimal depositPercentage,
            String notes,
            BigDecimal markupPercentage,
            BigDecimal discountPercentage,
            boolean taxEnabled,
            BigDecimal taxPercentage,
            BigDecimal internalCost,
            BigDecimal suggestedSubtotal,
            BigDecimal finalSubtotal,
            BigDecimal discountAmount,
            BigDecimal subtotalAfterDiscount,
            BigDecimal taxAmount,
            BigDecimal total,
            BigDecimal estimatedProfit,
            BigDecimal realMarginPercentage,
            List<ItemResponse> items) {
    }
}
