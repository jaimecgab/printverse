package com.printverse.service;

import com.printverse.domain.Customer;
import com.printverse.domain.Quote;
import com.printverse.domain.QuoteStatus;
import com.printverse.dto.CustomerDtos;
import com.printverse.repository.CustomerRepository;
import com.printverse.repository.ProductionOrderRepository;
import com.printverse.repository.QuoteRepository;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class CustomerServiceTest {

    @Test
    void overviewUsesOnlyAcceptedNetRevenueAndLimitsRecentQuotes() {
        CustomerRepository customers = mock(CustomerRepository.class);
        QuoteRepository quotes = mock(QuoteRepository.class);
        ProductionOrderRepository orders = mock(ProductionOrderRepository.class);
        CustomerService service = new CustomerService(customers, quotes, orders);
        Customer customer = new Customer("Ana", "555", null, null);
        ReflectionTestUtils.setField(customer, "id", 1L);
        List<Quote> history = java.util.stream.IntStream.range(0, 12)
                .mapToObj(index -> quote(customer, (long) index, index == 0 ? QuoteStatus.ACCEPTED : QuoteStatus.SENT))
                .toList();
        when(customers.findById(1L)).thenReturn(Optional.of(customer));
        when(quotes.findByCustomerIdOrderByCreatedAtDesc(1L)).thenReturn(history);
        when(orders.countByQuoteCustomerId(1L)).thenReturn(3L);

        CustomerDtos.Overview overview = service.overview(1L);

        assertThat(overview.totalAcceptedRevenue()).isEqualByComparingTo("90.00");
        assertThat(overview.totalEstimatedProfit()).isEqualByComparingTo("25.00");
        assertThat(overview.quoteCounts()).containsEntry(QuoteStatus.ACCEPTED, 1L)
                .containsEntry(QuoteStatus.SENT, 11L).containsEntry(QuoteStatus.DRAFT, 0L);
        assertThat(overview.recentQuotes()).hasSize(10);
        assertThat(overview.productionOrderCount()).isEqualTo(3L);
    }

    private static Quote quote(Customer customer, Long id, QuoteStatus status) {
        Quote quote = new Quote("PV-" + id, customer, LocalDate.now().plusDays(10), null,
                null, null, BigDecimal.ZERO, BigDecimal.ZERO, false, BigDecimal.ZERO);
        ReflectionTestUtils.setField(quote, "id", id);
        quote.setFinalSubtotal(new BigDecimal("100.00"));
        quote.setDiscountAmount(new BigDecimal("10.00"));
        quote.setEstimatedProfit(new BigDecimal("25.00"));
        quote.transitionTo(QuoteStatus.SENT);
        if (status == QuoteStatus.ACCEPTED) {
            quote.transitionTo(QuoteStatus.ACCEPTED);
        }
        return quote;
    }
}
