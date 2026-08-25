package com.printverse.service;

import com.printverse.domain.Customer;
import com.printverse.domain.Material;
import com.printverse.domain.Printer;
import com.printverse.domain.PrinterOperationalStatus;
import com.printverse.domain.ProductionOrder;
import com.printverse.domain.ProductionOrderItemStatus;
import com.printverse.domain.ProductionOrderStatus;
import com.printverse.domain.Quote;
import com.printverse.domain.QuoteItem;
import com.printverse.domain.QuoteStatus;
import com.printverse.dto.ProductionOrderDtos;
import com.printverse.exception.BusinessRuleException;
import com.printverse.exception.ConflictException;
import com.printverse.repository.PrinterRepository;
import com.printverse.repository.ProductionOrderRepository;
import com.printverse.repository.QuoteRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ProductionOrderServiceTest {

    @Mock
    private ProductionOrderRepository repository;
    @Mock
    private QuoteRepository quoteRepository;
    @Mock
    private PrinterRepository printerRepository;

    private ProductionOrderService service;

    @BeforeEach
    void setUp() {
        service = new ProductionOrderService(repository, quoteRepository, printerRepository);
    }

    @Test
    void conversionIsIdempotentAndFreezesQuoteItemData() {
        Quote quote = acceptedQuote();
        when(quoteRepository.findByIdForUpdate(10L)).thenReturn(Optional.of(quote));
        when(repository.findByQuoteId(10L)).thenReturn(Optional.empty());
        when(repository.save(any(ProductionOrder.class))).thenAnswer(invocation -> invocation.getArgument(0));

        ProductionOrderDtos.ConversionResult first = service.convert(10L, null);
        ArgumentCaptor<ProductionOrder> captor = ArgumentCaptor.forClass(ProductionOrder.class);
        verify(repository).save(captor.capture());
        ProductionOrder persisted = captor.getValue();
        when(repository.findByQuoteId(10L)).thenReturn(Optional.of(persisted));
        quote.getItems().get(0).getMaterial().setName("Material renamed later");

        ProductionOrderDtos.ConversionResult second = service.convert(10L,
                new ProductionOrderDtos.CreateRequest(LocalDate.now().plusDays(30), null, "ignored"));

        assertThat(first.created()).isTrue();
        assertThat(second.created()).isFalse();
        assertThat(second.order().orderNumber()).isEqualTo(first.order().orderNumber());
        assertThat(second.order().items()).singleElement()
                .extracting(ProductionOrderDtos.ItemResponse::materialName).isEqualTo("PLA espanol");
        verify(repository, times(1)).save(any(ProductionOrder.class));
    }

    @Test
    void startsOnlyInProductionAndAcquiresAvailablePrinter() {
        ProductionOrder order = order();
        Printer printer = printer(97L, PrinterOperationalStatus.AVAILABLE, true);
        when(repository.findByIdForUpdate(20L)).thenReturn(Optional.of(order));
        when(printerRepository.findByIdForUpdate(97L)).thenReturn(Optional.of(printer));

        assertThatThrownBy(() -> update(97L, ProductionOrderItemStatus.IN_PROGRESS, 0))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("order is IN_PRODUCTION");

        order.changeStatus(ProductionOrderStatus.IN_PRODUCTION);
        ProductionOrderDtos.Response response = update(97L, ProductionOrderItemStatus.IN_PROGRESS, 0);

        assertThat(response.items()).singleElement().satisfies(item -> {
            assertThat(item.status()).isEqualTo(ProductionOrderItemStatus.IN_PROGRESS);
            assertThat(item.assignedPrinter().id()).isEqualTo(97L);
        });
        assertThat(printer.getOperationalStatus()).isEqualTo(PrinterOperationalStatus.BUSY);
    }

    @Test
    void allowsBusyQueueAssignmentButRejectsStartingOnBusyPrinter() {
        ProductionOrder order = order();
        Printer printer = printer(97L, PrinterOperationalStatus.BUSY, true);
        when(repository.findByIdForUpdate(20L)).thenReturn(Optional.of(order));
        when(printerRepository.findByIdForUpdate(97L)).thenReturn(Optional.of(printer));

        ProductionOrderDtos.Response queued = update(97L, ProductionOrderItemStatus.PENDING, 0);
        assertThat(queued.items()).singleElement().satisfies(item -> {
            assertThat(item.status()).isEqualTo(ProductionOrderItemStatus.PENDING);
            assertThat(item.assignedPrinter().id()).isEqualTo(97L);
        });

        order.changeStatus(ProductionOrderStatus.IN_PRODUCTION);
        assertThatThrownBy(() -> update(null, ProductionOrderItemStatus.IN_PROGRESS, 0))
                .isInstanceOf(ConflictException.class)
                .hasMessageContaining("already occupied");
    }

    @Test
    void assigningAvailablePrinterToPendingItemDoesNotOccupyIt() {
        ProductionOrder order = order();
        Printer printer = printer(97L, PrinterOperationalStatus.AVAILABLE, true);
        when(repository.findByIdForUpdate(20L)).thenReturn(Optional.of(order));
        when(printerRepository.findByIdForUpdate(97L)).thenReturn(Optional.of(printer));

        ProductionOrderDtos.Response queued = update(97L, ProductionOrderItemStatus.PENDING, 0);

        assertThat(queued.items()).singleElement().satisfies(item -> {
            assertThat(item.status()).isEqualTo(ProductionOrderItemStatus.PENDING);
            assertThat(item.assignedPrinter().id()).isEqualTo(97L);
        });
        assertThat(printer.getOperationalStatus()).isEqualTo(PrinterOperationalStatus.AVAILABLE);
    }

    @Test
    void blocksAndCompletesWorkWhileReleasingPrinter() {
        ProductionOrder order = inProductionOrder();
        Printer printer = printer(97L, PrinterOperationalStatus.AVAILABLE, true);
        when(repository.findByIdForUpdate(20L)).thenReturn(Optional.of(order));
        when(printerRepository.findByIdForUpdate(97L)).thenReturn(Optional.of(printer));

        update(97L, ProductionOrderItemStatus.IN_PROGRESS, 1);
        ProductionOrderDtos.Response blocked = update(null, ProductionOrderItemStatus.BLOCKED, 1);

        assertThat(blocked.items()).singleElement().satisfies(item -> {
            assertThat(item.status()).isEqualTo(ProductionOrderItemStatus.BLOCKED);
            assertThat(item.completedQuantity()).isEqualTo(1);
            assertThat(item.assignedPrinter().id()).isEqualTo(97L);
        });
        assertThat(printer.getOperationalStatus()).isEqualTo(PrinterOperationalStatus.AVAILABLE);

        update(null, ProductionOrderItemStatus.IN_PROGRESS, 1);
        assertThatThrownBy(() -> update(null, ProductionOrderItemStatus.COMPLETED, 1))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("full quantity");
        assertThat(printer.getOperationalStatus()).isEqualTo(PrinterOperationalStatus.BUSY);
        ProductionOrderDtos.Response completed = update(null, ProductionOrderItemStatus.COMPLETED, 2);
        assertThat(completed.items()).singleElement().satisfies(item -> {
            assertThat(item.status()).isEqualTo(ProductionOrderItemStatus.COMPLETED);
            assertThat(item.completedQuantity()).isEqualTo(2);
        });
        assertThat(printer.getOperationalStatus()).isEqualTo(PrinterOperationalStatus.AVAILABLE);
        assertThatThrownBy(() -> update(null, ProductionOrderItemStatus.COMPLETED, 2))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("cannot be modified");
    }

    @Test
    void rejectsDirectReassignmentOfInProgressItem() {
        ProductionOrder order = inProductionOrder();
        Printer first = printer(97L, PrinterOperationalStatus.AVAILABLE, true);
        when(repository.findByIdForUpdate(20L)).thenReturn(Optional.of(order));
        when(printerRepository.findByIdForUpdate(97L)).thenReturn(Optional.of(first));
        update(97L, ProductionOrderItemStatus.IN_PROGRESS, 0);

        assertThatThrownBy(() -> update(98L, ProductionOrderItemStatus.IN_PROGRESS, 0))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("BLOCKED before reassigning");
        assertThat(first.getOperationalStatus()).isEqualTo(PrinterOperationalStatus.BUSY);
        assertThat(order.getItems().get(0).getAssignedPrinter()).isSameAs(first);
    }

    @Test
    void cancellationBlocksActiveItemsAndReleasesTheirPrinters() {
        ProductionOrder order = inProductionOrder();
        com.printverse.domain.ProductionOrderItem queued = new com.printverse.domain.ProductionOrderItem(
                order.getQuote().getItems().get(0));
        ReflectionTestUtils.setField(queued, "id", 31L);
        order.addItem(queued);
        Printer printer = printer(97L, PrinterOperationalStatus.AVAILABLE, true);
        when(repository.findByIdForUpdate(20L)).thenReturn(Optional.of(order));
        when(printerRepository.findByIdForUpdate(97L)).thenReturn(Optional.of(printer));
        update(97L, ProductionOrderItemStatus.IN_PROGRESS, 1);

        ProductionOrderDtos.Response cancelled = service.changeStatus(20L, ProductionOrderStatus.CANCELLED);

        assertThat(cancelled.status()).isEqualTo(ProductionOrderStatus.CANCELLED);
        assertThat(cancelled.items()).filteredOn(item -> item.id().equals(30L)).singleElement().satisfies(item -> {
            assertThat(item.status()).isEqualTo(ProductionOrderItemStatus.BLOCKED);
            assertThat(item.completedQuantity()).isEqualTo(1);
            assertThat(item.assignedPrinter().id()).isEqualTo(97L);
        });
        assertThat(cancelled.items()).filteredOn(item -> item.id().equals(31L)).singleElement()
                .extracting(ProductionOrderDtos.ItemResponse::status)
                .isEqualTo(ProductionOrderItemStatus.PENDING);
        assertThat(printer.getOperationalStatus()).isEqualTo(PrinterOperationalStatus.AVAILABLE);
    }

    @Test
    void rejectsInactiveAndNonOperationalNewAssignments() {
        ProductionOrder order = order();
        Printer inactive = printer(96L, PrinterOperationalStatus.AVAILABLE, false);
        Printer maintenance = printer(98L, PrinterOperationalStatus.MAINTENANCE, true);
        Printer outOfService = printer(99L, PrinterOperationalStatus.OUT_OF_SERVICE, true);
        when(repository.findByIdForUpdate(20L)).thenReturn(Optional.of(order));
        when(printerRepository.findByIdForUpdate(96L)).thenReturn(Optional.of(inactive));
        when(printerRepository.findByIdForUpdate(98L)).thenReturn(Optional.of(maintenance));
        when(printerRepository.findByIdForUpdate(99L)).thenReturn(Optional.of(outOfService));

        assertThatThrownBy(() -> update(96L, ProductionOrderItemStatus.PENDING, 0))
                .isInstanceOf(BusinessRuleException.class).hasMessageContaining("inactive");
        assertThatThrownBy(() -> update(98L, ProductionOrderItemStatus.BLOCKED, 0))
                .isInstanceOf(BusinessRuleException.class).hasMessageContaining("MAINTENANCE");
        assertThatThrownBy(() -> update(99L, ProductionOrderItemStatus.PENDING, 0))
                .isInstanceOf(BusinessRuleException.class).hasMessageContaining("OUT_OF_SERVICE");
    }

    @Test
    void enforcesReadyOnlyAfterEveryItemIsCompleted() {
        ProductionOrder order = inProductionOrder();
        Printer printer = printer(97L, PrinterOperationalStatus.AVAILABLE, true);
        when(repository.findByIdForUpdate(20L)).thenReturn(Optional.of(order));
        when(printerRepository.findByIdForUpdate(97L)).thenReturn(Optional.of(printer));

        assertThatThrownBy(() -> service.changeStatus(20L, ProductionOrderStatus.READY))
                .isInstanceOf(BusinessRuleException.class).hasMessageContaining("COMPLETED");
        update(97L, ProductionOrderItemStatus.IN_PROGRESS, 0);
        update(null, ProductionOrderItemStatus.COMPLETED, 2);

        assertThat(service.changeStatus(20L, ProductionOrderStatus.READY).status())
                .isEqualTo(ProductionOrderStatus.READY);
    }

    private ProductionOrderDtos.Response update(Long printerId, ProductionOrderItemStatus status, int completed) {
        return service.updateItem(20L, 30L,
                new ProductionOrderDtos.ItemUpdateRequest(printerId, status, completed, null, 0L));
    }

    private static ProductionOrder inProductionOrder() {
        ProductionOrder order = order();
        order.changeStatus(ProductionOrderStatus.IN_PRODUCTION);
        return order;
    }

    private static ProductionOrder order() {
        Quote quote = acceptedQuote();
        ProductionOrder order = new ProductionOrder(quote, "OP-TEST", LocalDate.now().plusDays(3),
                com.printverse.domain.ProductionOrderPriority.NORMAL, null);
        order.addItem(new com.printverse.domain.ProductionOrderItem(quote.getItems().get(0)));
        ReflectionTestUtils.setField(order, "id", 20L);
        ReflectionTestUtils.setField(order.getItems().get(0), "id", 30L);
        return order;
    }

    private static Printer printer(Long id, PrinterOperationalStatus status, boolean active) {
        Printer printer = new Printer("Printer " + id, null, BigDecimal.ZERO, active, status, null);
        ReflectionTestUtils.setField(printer, "id", id);
        return printer;
    }

    private static Quote acceptedQuote() {
        Quote quote = quote();
        quote.transitionTo(QuoteStatus.SENT);
        quote.transitionTo(QuoteStatus.ACCEPTED);
        return quote;
    }

    private static Quote quote() {
        Customer customer = new Customer("Ana Nunez", "555-0100", "ana@example.com", null);
        ReflectionTestUtils.setField(customer, "id", 1L);
        Quote quote = new Quote("PV-TEST", customer, LocalDate.now().plusDays(15),
                LocalDate.now().plusDays(7), null, null, new BigDecimal("40"),
                BigDecimal.ZERO, true, new BigDecimal("16"));
        ReflectionTestUtils.setField(quote, "id", 10L);
        Material material = new Material("PLA espanol", new BigDecimal("350"), true);
        Printer printer = new Printer("K1C", "CoreXY", new BigDecimal("10"), true);
        QuoteItem item = new QuoteItem("Pieza", 2, material, printer, new BigDecimal("50"),
                30, BigDecimal.ZERO, new BigDecimal("100"));
        ReflectionTestUtils.setField(item, "id", 11L);
        quote.addItem(item);
        new QuoteCalculationService().recalculate(quote);
        return quote;
    }
}
