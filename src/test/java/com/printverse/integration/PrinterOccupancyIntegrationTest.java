package com.printverse.integration;

import com.printverse.domain.Customer;
import com.printverse.domain.Material;
import com.printverse.domain.Printer;
import com.printverse.domain.PrinterOperationalStatus;
import com.printverse.domain.ProductionOrderItemStatus;
import com.printverse.domain.ProductionOrderStatus;
import com.printverse.domain.Quote;
import com.printverse.domain.QuoteItem;
import com.printverse.domain.QuoteStatus;
import com.printverse.dto.ProductionOrderDtos;
import com.printverse.exception.ConflictException;
import com.printverse.repository.CustomerRepository;
import com.printverse.repository.MaterialRepository;
import com.printverse.repository.PrinterRepository;
import com.printverse.repository.QuoteRepository;
import com.printverse.service.ProductionOrderService;
import com.printverse.service.QuoteCalculationService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest(properties = {
        "app.seed.enabled=false",
        "spring.flyway.baseline-on-migrate=false"
})
@Testcontainers(disabledWithoutDocker = true)
class PrinterOccupancyIntegrationTest {

    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:17-alpine");

    @DynamicPropertySource
    static void databaseProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
        registry.add("app.security.jwt.secret",
                () -> "printer-occupancy-test-jwt-signing-key-at-least-32-characters");
    }

    @Autowired
    private ProductionOrderService productionOrderService;

    @Autowired
    private QuoteRepository quoteRepository;

    @Autowired
    private CustomerRepository customerRepository;

    @Autowired
    private MaterialRepository materialRepository;

    @Autowired
    private PrinterRepository printerRepository;

    @Test
    void serializesCompetingOrdersAndReusesPrinterAfterRelease() throws Exception {
        Customer customer = customerRepository.save(new Customer(
                "Concurrent customer", "555-0100", "concurrent@example.com", null));
        Material material = materialRepository.save(new Material("Concurrent PLA", new BigDecimal("350"), true));
        Printer printer = printerRepository.save(new Printer(
                "Exclusive printer", "Test", new BigDecimal("10"), true));
        OrderRef first = createInProductionOrder("A", customer, material, printer);
        OrderRef second = createInProductionOrder("B", customer, material, printer);

        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            Future<Attempt> firstAttempt = executor.submit(() -> start(first, printer.getId(), ready, start));
            Future<Attempt> secondAttempt = executor.submit(() -> start(second, printer.getId(), ready, start));
            assertThat(ready.await(5, TimeUnit.SECONDS)).isTrue();
            start.countDown();

            Attempt firstResult = firstAttempt.get(10, TimeUnit.SECONDS);
            Attempt secondResult = secondAttempt.get(10, TimeUnit.SECONDS);
            assertThat(firstResult.success ^ secondResult.success).isTrue();
            Attempt rejected = firstResult.success ? secondResult : firstResult;
            assertThat(rejected.failure).isInstanceOf(ConflictException.class);

            OrderRef winner = firstResult.success ? first : second;
            OrderRef loser = firstResult.success ? second : first;
            ProductionOrderDtos.Response winnerResponse = productionOrderService.get(winner.orderId);
            assertThat(winnerResponse.items()).singleElement()
                    .extracting(ProductionOrderDtos.ItemResponse::status)
                    .isEqualTo(ProductionOrderItemStatus.IN_PROGRESS);
            assertThat(productionOrderService.get(loser.orderId).items()).singleElement()
                    .extracting(ProductionOrderDtos.ItemResponse::status)
                    .isEqualTo(ProductionOrderItemStatus.PENDING);
            assertThat(printerRepository.findById(printer.getId()).orElseThrow().getOperationalStatus())
                    .isEqualTo(PrinterOperationalStatus.BUSY);

            assertThatThrownBy(() -> productionOrderService.updateItem(winner.orderId, winner.itemId,
                    new ProductionOrderDtos.ItemUpdateRequest(
                            null, ProductionOrderItemStatus.BLOCKED, 0, "Stale", 0L)))
                    .isInstanceOf(ConflictException.class)
                    .hasMessageContaining("changed after it was loaded");

            productionOrderService.updateItem(winner.orderId, winner.itemId,
                    new ProductionOrderDtos.ItemUpdateRequest(
                            null, ProductionOrderItemStatus.BLOCKED, 0, "Released",
                            winnerResponse.items().get(0).version()));
            assertThat(printerRepository.findById(printer.getId()).orElseThrow().getOperationalStatus())
                    .isEqualTo(PrinterOperationalStatus.AVAILABLE);

            productionOrderService.updateItem(loser.orderId, loser.itemId,
                    new ProductionOrderDtos.ItemUpdateRequest(
                            printer.getId(), ProductionOrderItemStatus.IN_PROGRESS, 0, "Reused", 0L));
            assertThat(productionOrderService.get(loser.orderId).items()).singleElement()
                    .extracting(ProductionOrderDtos.ItemResponse::status)
                    .isEqualTo(ProductionOrderItemStatus.IN_PROGRESS);
            assertThat(printerRepository.findById(printer.getId()).orElseThrow().getOperationalStatus())
                    .isEqualTo(PrinterOperationalStatus.BUSY);
        } finally {
            executor.shutdownNow();
        }
    }

    private Attempt start(OrderRef order, Long printerId, CountDownLatch ready, CountDownLatch start) {
        ready.countDown();
        try {
            if (!start.await(5, TimeUnit.SECONDS)) {
                return new Attempt(false, new IllegalStateException("Concurrent start was not released"));
            }
            productionOrderService.updateItem(order.orderId, order.itemId,
                    new ProductionOrderDtos.ItemUpdateRequest(
                            printerId, ProductionOrderItemStatus.IN_PROGRESS, 0, null, 0L));
            return new Attempt(true, null);
        } catch (RuntimeException exception) {
            return new Attempt(false, exception);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            return new Attempt(false, exception);
        }
    }

    private OrderRef createInProductionOrder(String suffix, Customer customer,
                                             Material material, Printer printer) {
        Quote quote = new Quote("PV-CONCURRENT-" + suffix, customer, LocalDate.now().plusDays(15),
                LocalDate.now().plusDays(7), null, null, new BigDecimal("40"),
                BigDecimal.ZERO, true, new BigDecimal("16"));
        quote.addItem(new QuoteItem("Part " + suffix, 2, material, printer, new BigDecimal("50"),
                30, BigDecimal.ZERO, new BigDecimal("100")));
        new QuoteCalculationService().recalculate(quote);
        quote.transitionTo(QuoteStatus.SENT);
        quote.transitionTo(QuoteStatus.ACCEPTED);
        quote = quoteRepository.saveAndFlush(quote);

        ProductionOrderDtos.Response order = productionOrderService.convert(quote.getId(), null).order();
        order = productionOrderService.changeStatus(order.id(), ProductionOrderStatus.IN_PRODUCTION);
        return new OrderRef(order.id(), order.items().get(0).id());
    }

    private record OrderRef(Long orderId, Long itemId) {
    }

    private record Attempt(boolean success, Throwable failure) {
    }
}
