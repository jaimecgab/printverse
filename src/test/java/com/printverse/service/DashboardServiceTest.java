package com.printverse.service;

import com.printverse.domain.Customer;
import com.printverse.domain.Material;
import com.printverse.domain.Printer;
import com.printverse.domain.PrinterOperationalStatus;
import com.printverse.domain.ProductionOrder;
import com.printverse.domain.ProductionOrderPriority;
import com.printverse.domain.Quote;
import com.printverse.domain.QuoteStatus;
import com.printverse.dto.DashboardDtos;
import com.printverse.repository.ProductionOrderRepository;
import com.printverse.repository.MaterialRepository;
import com.printverse.repository.PrinterRepository;
import com.printverse.repository.QuoteRepository;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class DashboardServiceTest {

    @Test
    void aggregatesOnlyTheRequiredStatusesWithoutTax() {
        QuoteRepository quotes = mock(QuoteRepository.class);
        ProductionOrderRepository orders = mock(ProductionOrderRepository.class);
        MaterialRepository materials = mock(MaterialRepository.class);
        PrinterRepository printers = mock(PrinterRepository.class);
        DashboardService service = new DashboardService(quotes, orders, materials, printers);
        Quote sent = quote(1L, QuoteStatus.SENT, "1000.00", "100.00", "111.00");
        Quote accepted = quote(2L, QuoteStatus.ACCEPTED, "800.00", "80.00", "250.00");
        Quote rejected = quote(3L, QuoteStatus.REJECTED, "900.00", "0.00", "999.00");
        ProductionOrder dueSoon = new ProductionOrder(accepted, "OP-DUE", LocalDate.now().plusDays(7),
                ProductionOrderPriority.HIGH, null);
        ProductionOrder overdue = new ProductionOrder(accepted, "OP-LATE", LocalDate.now().minusDays(1),
                ProductionOrderPriority.URGENT, null);
        when(quotes.findAllByOrderByCreatedAtDesc()).thenReturn(List.of(sent, accepted, rejected));
        when(orders.findAllForList()).thenReturn(List.of(dueSoon, overdue));
        Material lowStock = new Material("PLA rojo", new BigDecimal("350"), true,
                "PLA", "PrintVerse", "Rojo", new BigDecimal("150.000"),
                new BigDecimal("200.000"), null);
        ReflectionTestUtils.setField(lowStock, "id", 8L);
        Printer available = new Printer("K1C", null, BigDecimal.TEN, true);
        Printer busy = new Printer("MK4", null, BigDecimal.TEN, true,
                PrinterOperationalStatus.BUSY, null);
        when(materials.findActiveLowStock()).thenReturn(List.of(lowStock));
        when(printers.findAllByActiveTrue()).thenReturn(List.of(available, busy));

        DashboardDtos.Response response = service.get();

        assertThat(response.sentPipelineAmount()).isEqualByComparingTo("900.00");
        assertThat(response.acceptedRevenue()).isEqualByComparingTo("720.00");
        assertThat(response.acceptedEstimatedProfit()).isEqualByComparingTo("250.00");
        assertThat(response.acceptanceRate()).isEqualByComparingTo("0.5000");
        assertThat(response.quoteCounts()).containsEntry(QuoteStatus.DRAFT, 0L)
                .containsEntry(QuoteStatus.SENT, 1L).containsEntry(QuoteStatus.ACCEPTED, 1L);
        assertThat(response.productionCounts().get(com.printverse.domain.ProductionOrderStatus.PENDING)).isEqualTo(2L);
        assertThat(response.lowStockMaterials()).singleElement().satisfies(material -> {
            assertThat(material.id()).isEqualTo(8L);
            assertThat(material.stockGrams()).isEqualByComparingTo("150.000");
            assertThat(material.lowStockThresholdGrams()).isEqualByComparingTo("200.000");
        });
        assertThat(response.printerCounts()).containsEntry(PrinterOperationalStatus.AVAILABLE, 1L)
                .containsEntry(PrinterOperationalStatus.BUSY, 1L)
                .containsEntry(PrinterOperationalStatus.MAINTENANCE, 0L)
                .containsEntry(PrinterOperationalStatus.OUT_OF_SERVICE, 0L);
        assertThat(response.dueSoonOrders()).extracting(DashboardDtos.OrderSummary::orderNumber)
                .containsExactly("OP-DUE");
        assertThat(response.overdueOrders()).extracting(DashboardDtos.OrderSummary::orderNumber)
                .containsExactly("OP-LATE");
        assertThat(response.recentQuotes()).hasSize(3);
        assertThat(response.updatedAt()).isNotNull();
    }

    private static Quote quote(Long id, QuoteStatus status, String subtotal, String discount, String profit) {
        Customer customer = new Customer("Customer", "555", null, null);
        ReflectionTestUtils.setField(customer, "id", 1L);
        Quote quote = new Quote("PV-" + id, customer, LocalDate.now().plusDays(10), null,
                null, null, BigDecimal.ZERO, BigDecimal.ZERO, false, BigDecimal.ZERO);
        ReflectionTestUtils.setField(quote, "id", id);
        quote.setFinalSubtotal(new BigDecimal(subtotal));
        quote.setDiscountAmount(new BigDecimal(discount));
        quote.setEstimatedProfit(new BigDecimal(profit));
        if (status != QuoteStatus.DRAFT) {
            quote.transitionTo(QuoteStatus.SENT);
        }
        if (status == QuoteStatus.ACCEPTED || status == QuoteStatus.REJECTED) {
            quote.transitionTo(status);
        }
        return quote;
    }
}
