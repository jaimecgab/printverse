package com.printverse.service;

import com.printverse.domain.Customer;
import com.printverse.domain.Material;
import com.printverse.domain.Printer;
import com.printverse.domain.Quote;
import com.printverse.domain.QuoteItem;
import com.printverse.domain.QuoteStatus;
import com.printverse.dto.QuoteDtos;
import com.printverse.exception.BusinessRuleException;
import com.printverse.repository.QuoteRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class QuoteServiceTest {

    @Mock
    private QuoteRepository quoteRepository;
    @Mock
    private CustomerService customerService;
    @Mock
    private MaterialService materialService;
    @Mock
    private PrinterService printerService;

    private QuoteService service;

    @BeforeEach
    void setUp() {
        service = new QuoteService(quoteRepository, customerService, materialService, printerService,
                new QuoteCalculationService());
    }

    @Test
    void newQuoteAlwaysStartsAsDraft() {
        Customer customer = new Customer("Ana", "555-0100", null, null);
        when(customerService.find(1L)).thenReturn(customer);
        when(quoteRepository.save(any(Quote.class))).thenAnswer(invocation -> invocation.getArgument(0));
        QuoteDtos.CreateRequest request = new QuoteDtos.CreateRequest(1L, LocalDate.now().plusDays(15),
                null, null, null, new BigDecimal("40"), null, true, new BigDecimal("16"));

        QuoteDtos.Response response = service.create(request);

        assertThat(response.status()).isEqualTo(QuoteStatus.DRAFT);
        assertThat(response.quoteNumber()).startsWith("PV-" + LocalDate.now().getYear() + "-");
        assertThat(response.total()).isEqualByComparingTo("0.00");
    }

    @Test
    void followsDraftSentAcceptedTransition() {
        Quote quote = quoteWithItem();
        when(quoteRepository.findByIdForUpdate(10L)).thenReturn(Optional.of(quote));

        QuoteDtos.Response sent = service.changeStatus(10L, QuoteStatus.SENT);
        QuoteDtos.Response accepted = service.changeStatus(10L, QuoteStatus.ACCEPTED);

        assertThat(sent.status()).isEqualTo(QuoteStatus.SENT);
        assertThat(accepted.status()).isEqualTo(QuoteStatus.ACCEPTED);
    }

    @Test
    void rejectsInvalidStatusTransitionAndLocksFinalQuote() {
        Quote quote = quoteWithItem();
        quote.setStatus(QuoteStatus.ACCEPTED);
        when(quoteRepository.findByIdForUpdate(10L)).thenReturn(Optional.of(quote));

        assertThatThrownBy(() -> service.changeStatus(10L, QuoteStatus.REJECTED))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("ACCEPTED to REJECTED");
        assertThatThrownBy(() -> service.recalculate(10L))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessage("Only DRAFT quotes can be modified");
    }

    @Test
    void draftWithoutItemsCannotBeSent() {
        Quote quote = quote();
        when(quoteRepository.findByIdForUpdate(10L)).thenReturn(Optional.of(quote));

        assertThatThrownBy(() -> service.changeStatus(10L, QuoteStatus.SENT))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("at least one item");
    }

    private static Quote quoteWithItem() {
        Quote quote = quote();
        Material material = new Material("PLA", new BigDecimal("350"), true);
        Printer printer = new Printer("K1C", null, new BigDecimal("10"), true);
        quote.addItem(new QuoteItem("Part", 1, material, printer, new BigDecimal("50"),
                30, new BigDecimal("5"), null));
        new QuoteCalculationService().recalculate(quote);
        return quote;
    }

    private static Quote quote() {
        return new Quote("PV-TEST", new Customer("Ana", "555-0100", null, null),
                LocalDate.now().plusDays(15), null, null, null,
                new BigDecimal("40"), BigDecimal.ZERO, true, new BigDecimal("16"));
    }
}
