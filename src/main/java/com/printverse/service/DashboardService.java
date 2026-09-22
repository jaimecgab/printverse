package com.printverse.service;

import com.printverse.domain.PrinterOperationalStatus;
import com.printverse.domain.ProductionOrder;
import com.printverse.domain.ProductionOrderStatus;
import com.printverse.domain.Quote;
import com.printverse.domain.QuoteStatus;
import com.printverse.dto.DashboardDtos;
import com.printverse.repository.MaterialRepository;
import com.printverse.repository.PrinterRepository;
import com.printverse.repository.ProductionOrderRepository;
import com.printverse.repository.QuoteRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Arrays;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

import static com.printverse.service.MoneyUtils.ROUNDING_MODE;
import static com.printverse.service.MoneyUtils.money;

@Service
public class DashboardService {

    private final QuoteRepository quoteRepository;
    private final ProductionOrderRepository productionOrderRepository;
    private final MaterialRepository materialRepository;
    private final PrinterRepository printerRepository;

    public DashboardService(QuoteRepository quoteRepository,
                            ProductionOrderRepository productionOrderRepository,
                            MaterialRepository materialRepository,
                            PrinterRepository printerRepository) {
        this.quoteRepository = quoteRepository;
        this.productionOrderRepository = productionOrderRepository;
        this.materialRepository = materialRepository;
        this.printerRepository = printerRepository;
    }

    @Transactional(readOnly = true)
    public DashboardDtos.Response get() {
        List<Quote> quotes = quoteRepository.findAllByOrderByCreatedAtDesc();
        List<ProductionOrder> orders = productionOrderRepository.findAllForList();
        Map<QuoteStatus, Long> quoteCounts = counts(QuoteStatus.values());
        Map<ProductionOrderStatus, Long> productionCounts = counts(ProductionOrderStatus.values());
        Map<PrinterOperationalStatus, Long> printerCounts = counts(PrinterOperationalStatus.values());
        BigDecimal sentPipeline = BigDecimal.ZERO;
        BigDecimal acceptedRevenue = BigDecimal.ZERO;
        BigDecimal acceptedProfit = BigDecimal.ZERO;

        for (Quote quote : quotes) {
            quoteCounts.compute(quote.getStatus(), (status, count) -> count + 1);
            BigDecimal netSubtotal = quote.getFinalSubtotal().subtract(quote.getDiscountAmount());
            if (quote.getStatus() == QuoteStatus.SENT) {
                sentPipeline = sentPipeline.add(netSubtotal);
            } else if (quote.getStatus() == QuoteStatus.ACCEPTED) {
                acceptedRevenue = acceptedRevenue.add(netSubtotal);
                acceptedProfit = acceptedProfit.add(quote.getEstimatedProfit());
            }
        }
        orders.forEach(order -> productionCounts.compute(order.getStatus(), (status, count) -> count + 1));
        printerRepository.findAllByActiveTrue().forEach(printer -> printerCounts.compute(
                printer.getOperationalStatus(), (status, count) -> count + 1));

        long accepted = quoteCounts.get(QuoteStatus.ACCEPTED);
        long decided = accepted + quoteCounts.get(QuoteStatus.REJECTED);
        BigDecimal acceptanceRate = decided == 0 ? BigDecimal.ZERO.setScale(4)
                : BigDecimal.valueOf(accepted).divide(BigDecimal.valueOf(decided), 4, ROUNDING_MODE);
        LocalDate today = LocalDate.now();
        LocalDate dueLimit = today.plusDays(7);
        List<DashboardDtos.OrderSummary> dueSoon = orders.stream()
                .filter(DashboardService::isOpen)
                .filter(order -> order.getDueDate() != null && !order.getDueDate().isBefore(today)
                        && !order.getDueDate().isAfter(dueLimit))
                .map(DashboardService::orderSummary)
                .toList();
        List<DashboardDtos.OrderSummary> overdue = orders.stream()
                .filter(DashboardService::isOpen)
                .filter(order -> order.getDueDate() != null && order.getDueDate().isBefore(today))
                .map(DashboardService::orderSummary)
                .toList();

        return new DashboardDtos.Response(quoteCounts, money(sentPipeline), money(acceptedRevenue),
                money(acceptedProfit), acceptanceRate, productionCounts,
                materialRepository.findActiveLowStock().stream()
                        .map(material -> new DashboardDtos.LowStockMaterial(material.getId(), material.getName(),
                                material.getMaterialType(), material.getBrand(), material.getColor(),
                                material.getStockGrams(), material.getLowStockThresholdGrams()))
                        .toList(),
                printerCounts, dueSoon, overdue,
                quotes.stream().limit(5).map(QuoteService::toSummaryResponse).toList(), Instant.now());
    }

    private static boolean isOpen(ProductionOrder order) {
        return order.getStatus() != ProductionOrderStatus.DELIVERED
                && order.getStatus() != ProductionOrderStatus.CANCELLED;
    }

    private static DashboardDtos.OrderSummary orderSummary(ProductionOrder order) {
        Quote quote = order.getQuote();
        String customerName = quote.getCustomerNameSnapshot() != null
                ? quote.getCustomerNameSnapshot() : quote.getCustomer().getName();
        return new DashboardDtos.OrderSummary(order.getId(), order.getOrderNumber(), quote.getId(),
                quote.getQuoteNumber(), customerName, order.getStatus(), order.getPriority(), order.getDueDate());
    }

    private static <E extends Enum<E>> Map<E, Long> counts(E[] values) {
        Map<E, Long> counts = new EnumMap<>(values[0].getDeclaringClass());
        Arrays.stream(values).forEach(value -> counts.put(value, 0L));
        return counts;
    }
}
