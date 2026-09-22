package com.printverse.service;

import com.printverse.domain.AdditionalCharge;
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
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
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
        QuoteDtos.CreateRequest request = new QuoteDtos.CreateRequest(1L, "Prototype",
                LocalDate.now().plusDays(15), null, null, null, "Internal",
                new BigDecimal("40"), null, true, new BigDecimal("16"));

        QuoteDtos.Response response = service.create(request);

        assertThat(response.status()).isEqualTo(QuoteStatus.DRAFT);
        assertThat(response.quoteNumber()).startsWith("PV-" + LocalDate.now().getYear() + "-");
        assertThat(response.title()).isEqualTo("Prototype");
        assertThat(response.currencyCode()).isEqualTo("MXN");
        assertThat(response.total()).isEqualByComparingTo("0.00");
    }

    @Test
    void listSummaryUsesPersistedProfitAndMargin() {
        Quote quote = quoteWithItem();
        quote.setEstimatedProfit(new BigDecimal("123.45"));
        quote.setRealMarginPercentage(new BigDecimal("37.5000"));
        when(quoteRepository.findAllByOrderByCreatedAtDesc()).thenReturn(List.of(quote));

        QuoteDtos.SummaryResponse response = service.list().get(0);

        assertThat(response.estimatedProfit()).isEqualByComparingTo("123.45");
        assertThat(response.realMarginPercentage()).isEqualByComparingTo("37.5000");
    }

    @Test
    void followsDraftSentAcceptedTransition() {
        Quote quote = quoteWithItem();
        when(quoteRepository.findByIdForUpdate(10L)).thenReturn(Optional.of(quote));

        QuoteDtos.Response sent = service.changeStatus(10L, QuoteStatus.SENT);
        QuoteDtos.Response accepted = service.changeStatus(10L, QuoteStatus.ACCEPTED);

        assertThat(sent.status()).isEqualTo(QuoteStatus.SENT);
        assertThat(sent.sentAt()).isNotNull();
        assertThat(sent.updatedAt()).isEqualTo(sent.sentAt());
        assertThat(accepted.status()).isEqualTo(QuoteStatus.ACCEPTED);
        assertThat(accepted.acceptedAt()).isNotNull();
        assertThat(accepted.rejectedAt()).isNull();
        assertThat(accepted.updatedAt()).isEqualTo(accepted.acceptedAt());
        assertThat(accepted.acceptedAt()).isAfterOrEqualTo(sent.sentAt());
    }

    @Test
    void rejectsInvalidStatusTransitionAndLocksFinalQuote() {
        Quote quote = quoteWithItem();
        quote.transitionTo(QuoteStatus.SENT);
        quote.transitionTo(QuoteStatus.ACCEPTED);
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

    @Test
    void responsesKeepCustomerAndCatalogNamesCapturedAtSendAndItemCreation() {
        Quote quote = quoteWithItem();
        when(quoteRepository.findByIdForUpdate(10L)).thenReturn(Optional.of(quote));
        when(quoteRepository.findById(10L)).thenReturn(Optional.of(quote));

        service.changeStatus(10L, QuoteStatus.SENT);
        quote.getCustomer().setName("Renamed customer");
        quote.getCustomer().setPhone("555-9999");
        quote.getItems().get(0).getMaterial().setName("Renamed material");
        quote.getItems().get(0).getPrinter().setName("Renamed printer");
        quote.getItems().get(0).getPrinter().setModel("New model");

        QuoteDtos.Response response = service.get(10L);

        assertThat(response.customer().name()).isEqualTo("Ana");
        assertThat(response.customer().phone()).isEqualTo("555-0100");
        assertThat(response.items().get(0).material().name()).isEqualTo("PLA");
        assertThat(response.items().get(0).printer().name()).isEqualTo("K1C");
        assertThat(response.items().get(0).printer().model()).isNull();
    }

    @Test
    void itemCreateAndUpdatePersistAndReplaceAllChargesAtomically() {
        Quote quote = quote();
        Material material = new Material("PLA", new BigDecimal("350"), true);
        Printer printer = new Printer("K1C", null, new BigDecimal("10"), true);
        when(quoteRepository.findByIdForUpdate(10L)).thenReturn(Optional.of(quote));
        when(materialService.find(1L)).thenReturn(material);
        when(printerService.find(1L)).thenReturn(printer);
        QuoteDtos.ItemRequest create = itemRequest(List.of(
                new QuoteDtos.ChargeRequest("Finishing", new BigDecimal("10")),
                new QuoteDtos.ChargeRequest("Packaging", new BigDecimal("5"))));

        QuoteDtos.Response created = service.addItem(10L, create);
        QuoteItem item = quote.getItems().get(0);
        ReflectionTestUtils.setField(item, "id", 20L);
        QuoteDtos.ItemRequest update = itemRequest(List.of(
                new QuoteDtos.ChargeRequest("Painting", new BigDecimal("7.50"))));
        QuoteDtos.Response updated = service.updateItem(10L, 20L, update);

        assertThat(created.items().get(0).additionalCharges()).hasSize(2);
        assertThat(updated.items().get(0).additionalCharges())
                .extracting(QuoteDtos.ChargeResponse::description)
                .containsExactly("Painting");
        assertThat(updated.items().get(0).additionalChargesUnit()).isEqualByComparingTo("7.50");
        assertThat(item.getAdditionalCharges()).hasSize(1);
    }

    @Test
    void addItemTreatsOmittedChargesAsEmpty() {
        Quote quote = quote();
        Material material = new Material("PLA", new BigDecimal("350"), true);
        Printer printer = new Printer("K1C", null, new BigDecimal("10"), true);
        when(quoteRepository.findByIdForUpdate(10L)).thenReturn(Optional.of(quote));
        when(materialService.find(1L)).thenReturn(material);
        when(printerService.find(1L)).thenReturn(printer);

        QuoteDtos.Response response = service.addItem(10L, itemRequest(null));

        assertThat(response.items()).singleElement().satisfies(item ->
                assertThat(item.additionalCharges()).isEmpty());
    }

    @Test
    void overflowIsRejectedBeforeTheRepositoryIsFlushed() {
        Quote quote = quote();
        Material material = new Material("PLA", BigDecimal.ZERO, true);
        Printer printer = new Printer("K1C", null, BigDecimal.ZERO, true);
        when(quoteRepository.findByIdForUpdate(10L)).thenReturn(Optional.of(quote));
        when(materialService.find(1L)).thenReturn(material);
        when(printerService.find(1L)).thenReturn(printer);
        QuoteDtos.ItemRequest request = new QuoteDtos.ItemRequest("Part", 100000, 1L, 1L,
                BigDecimal.ZERO, 0, BigDecimal.ZERO, new BigDecimal("999999999999.99"), null);

        assertThatThrownBy(() -> service.addItem(10L, request))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("Final subtotal");
        verify(quoteRepository, never()).flush();
    }

    @Test
    void omittedChargesPreserveExistingValuesWhileAnEmptyListClearsThem() {
        Quote quote = quoteWithItem();
        QuoteItem item = quote.getItems().get(0);
        item.addAdditionalCharge(new AdditionalCharge("Finishing", new BigDecimal("12.00")));
        ReflectionTestUtils.setField(item, "id", 20L);
        ReflectionTestUtils.setField(item.getMaterial(), "id", 1L);
        ReflectionTestUtils.setField(item.getPrinter(), "id", 1L);
        when(quoteRepository.findByIdForUpdate(10L)).thenReturn(Optional.of(quote));
        when(materialService.find(1L)).thenReturn(item.getMaterial());
        when(printerService.find(1L)).thenReturn(item.getPrinter());

        QuoteDtos.Response preserved = service.updateItem(10L, 20L, itemRequest(null));
        QuoteDtos.Response cleared = service.updateItem(10L, 20L, itemRequest(List.of()));

        assertThat(preserved.items().get(0).additionalCharges()).singleElement()
                .extracting(QuoteDtos.ChargeResponse::description).isEqualTo("Finishing");
        assertThat(cleared.items().get(0).additionalCharges()).isEmpty();
    }

    @Test
    void explicitCatalogChangeRefreshesNameModelAndCostSnapshots() {
        Quote quote = quoteWithItem();
        QuoteItem item = quote.getItems().get(0);
        ReflectionTestUtils.setField(item, "id", 20L);
        ReflectionTestUtils.setField(item.getMaterial(), "id", 1L);
        ReflectionTestUtils.setField(item.getPrinter(), "id", 1L);
        Material petg = new Material("PETG", new BigDecimal("420"), true);
        Printer printer = new Printer("X1C", "X1 Carbon", new BigDecimal("18"), true);
        ReflectionTestUtils.setField(petg, "id", 2L);
        ReflectionTestUtils.setField(printer, "id", 2L);
        when(quoteRepository.findByIdForUpdate(10L)).thenReturn(Optional.of(quote));
        when(materialService.find(2L)).thenReturn(petg);
        when(printerService.find(2L)).thenReturn(printer);
        QuoteDtos.ItemRequest request = new QuoteDtos.ItemRequest("Part", 1, 2L, 2L,
                new BigDecimal("50"), 30, new BigDecimal("5"), null, null);

        QuoteDtos.Response response = service.updateItem(10L, 20L, request);
        petg.setName("Renamed PETG");
        printer.setName("Renamed X1C");

        assertThat(response.items().get(0).material().name()).isEqualTo("PETG");
        assertThat(response.items().get(0).printer().name()).isEqualTo("X1C");
        assertThat(response.items().get(0).printer().model()).isEqualTo("X1 Carbon");
        assertThat(item.getMaterialNameSnapshot()).isEqualTo("PETG");
        assertThat(item.getPrinterNameSnapshot()).isEqualTo("X1C");
        assertThat(item.getPrinterModelSnapshot()).isEqualTo("X1 Carbon");
        assertThat(item.getMaterialPricePerKgSnapshot()).isEqualByComparingTo("420");
        assertThat(item.getPrinterCostPerHourSnapshot()).isEqualByComparingTo("18");
    }

    @Test
    void duplicateFromFinalStatusKeepsOriginalSnapshotsTermsAndChargesButResetsLifecycle() {
        Quote source = quoteWithItem();
        source.setTitle("Replacement bracket");
        source.setInternalNotes("Check tolerances");
        source.setDepositPercentage(new BigDecimal("30"));
        source.getItems().get(0).addAdditionalCharge(new AdditionalCharge("Finishing", new BigDecimal("12.00")));
        new QuoteCalculationService().recalculate(source);
        source.transitionTo(QuoteStatus.SENT);
        source.transitionTo(QuoteStatus.ACCEPTED);
        source.getCustomer().setName("Changed customer");
        source.getItems().get(0).getMaterial().setName("Changed material");
        when(quoteRepository.findByIdForUpdate(10L)).thenReturn(Optional.of(source));
        when(quoteRepository.save(any(Quote.class))).thenAnswer(invocation -> invocation.getArgument(0));

        QuoteDtos.Response duplicate = service.duplicate(10L,
                new QuoteDtos.DuplicateRequest(LocalDate.now().plusDays(30), LocalDate.now().plusDays(20)));

        assertThat(duplicate.status()).isEqualTo(QuoteStatus.DRAFT);
        assertThat(duplicate.quoteNumber()).isNotEqualTo(source.getQuoteNumber());
        assertThat(duplicate.title()).isEqualTo("Replacement bracket");
        assertThat(duplicate.customer().name()).isEqualTo("Ana");
        assertThat(duplicate.items().get(0).material().name()).isEqualTo("PLA");
        assertThat(duplicate.items().get(0).additionalCharges()).singleElement()
                .extracting(QuoteDtos.ChargeResponse::description).isEqualTo("Finishing");
        assertThat(duplicate.total()).isEqualByComparingTo(source.getTotal());
        assertThat(duplicate.sentAt()).isNull();
        assertThat(duplicate.acceptedAt()).isNull();
        assertThat(duplicate.rejectedAt()).isNull();
    }

    @Test
    void responseCalculatesDepositAndRemainingBalanceWithoutPersistingThem() {
        Quote quote = quoteWithItem();
        quote.setDepositPercentage(new BigDecimal("30"));
        when(quoteRepository.findById(10L)).thenReturn(Optional.of(quote));

        QuoteDtos.Response response = service.get(10L);

        assertThat(response.depositAmount()).isEqualByComparingTo("11.51");
        assertThat(response.remainingBalance()).isEqualByComparingTo(
                response.total().subtract(response.depositAmount()));
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

    private static QuoteDtos.ItemRequest itemRequest(List<QuoteDtos.ChargeRequest> charges) {
        return new QuoteDtos.ItemRequest("Part", 2, 1L, 1L, new BigDecimal("50"), 30,
                new BigDecimal("5"), null, charges);
    }
}
