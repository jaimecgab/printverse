package com.printverse.dto;

import com.printverse.domain.ProductionOrderItemStatus;
import com.printverse.domain.ProductionOrderPriority;
import com.printverse.domain.ProductionOrderStatus;
import jakarta.validation.constraints.FutureOrPresent;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

public final class ProductionOrderDtos {

    private ProductionOrderDtos() {
    }

    public record CreateRequest(
            @FutureOrPresent LocalDate dueDate,
            ProductionOrderPriority priority,
            @Size(max = 3000) String notes) {
    }

    public record StatusRequest(@NotNull ProductionOrderStatus status) {
    }

    public record ItemUpdateRequest(
            @Positive Long assignedPrinterId,
            ProductionOrderItemStatus status,
            @PositiveOrZero @Max(100000) Integer completedQuantity,
            @Size(max = 2000) String notes,
            @NotNull @PositiveOrZero Long expectedVersion) {
    }

    public record QuoteSummary(Long id, String quoteNumber, String title,
                               QuoteDtos.CustomerSummary customer) {
    }

    public record AssignedPrinter(Long id, String name, String model) {
    }

    public record ItemResponse(
            Long id,
            Long quoteItemId,
            String name,
            int quantity,
            String materialName,
            String printerName,
            String printerModel,
            BigDecimal weightGrams,
            int printTimeMinutes,
            AssignedPrinter assignedPrinter,
            int completedQuantity,
            ProductionOrderItemStatus status,
            String notes,
            long version) {
    }

    public record Response(
            Long id,
            String orderNumber,
            QuoteSummary quote,
            ProductionOrderStatus status,
            ProductionOrderPriority priority,
            LocalDate dueDate,
            String notes,
            Instant createdAt,
            Instant updatedAt,
            Instant startedAt,
            Instant readyAt,
            Instant deliveredAt,
            Instant cancelledAt,
            List<ItemResponse> items) {
    }

    public record ConversionResult(Response order, boolean created) {
    }
}
