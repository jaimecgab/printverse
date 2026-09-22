package com.printverse.dto;

import com.printverse.domain.PrinterOperationalStatus;
import com.printverse.domain.ProductionOrderPriority;
import com.printverse.domain.ProductionOrderStatus;
import com.printverse.domain.QuoteStatus;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

public final class DashboardDtos {

    private DashboardDtos() {
    }

    public record OrderSummary(Long id, String orderNumber, Long quoteId, String quoteNumber,
                               String customerName, ProductionOrderStatus status,
                               ProductionOrderPriority priority, LocalDate dueDate) {
    }

    public record LowStockMaterial(Long id, String name, String materialType, String brand,
                                   String color, BigDecimal stockGrams,
                                   BigDecimal lowStockThresholdGrams) {
    }

    public record Response(
            Map<QuoteStatus, Long> quoteCounts,
            BigDecimal sentPipelineAmount,
            BigDecimal acceptedRevenue,
            BigDecimal acceptedEstimatedProfit,
            BigDecimal acceptanceRate,
            Map<ProductionOrderStatus, Long> productionCounts,
            List<LowStockMaterial> lowStockMaterials,
            Map<PrinterOperationalStatus, Long> printerCounts,
            List<OrderSummary> dueSoonOrders,
            List<OrderSummary> overdueOrders,
            List<QuoteDtos.SummaryResponse> recentQuotes,
            Instant updatedAt) {
    }
}
