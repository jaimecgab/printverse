package com.printverse.service;

import com.printverse.domain.Printer;
import com.printverse.domain.PrinterOperationalStatus;
import com.printverse.domain.ProductionOrder;
import com.printverse.domain.ProductionOrderItem;
import com.printverse.domain.ProductionOrderItemStatus;
import com.printverse.domain.ProductionOrderPriority;
import com.printverse.domain.ProductionOrderStatus;
import com.printverse.domain.Quote;
import com.printverse.domain.QuoteItem;
import com.printverse.domain.QuoteStatus;
import com.printverse.dto.ProductionOrderDtos;
import com.printverse.dto.QuoteDtos;
import com.printverse.exception.BusinessRuleException;
import com.printverse.exception.ConflictException;
import com.printverse.exception.ResourceNotFoundException;
import com.printverse.repository.PrinterRepository;
import com.printverse.repository.ProductionOrderRepository;
import com.printverse.repository.QuoteRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.Year;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeSet;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class ProductionOrderService {

    private final ProductionOrderRepository repository;
    private final QuoteRepository quoteRepository;
    private final PrinterRepository printerRepository;

    public ProductionOrderService(ProductionOrderRepository repository, QuoteRepository quoteRepository,
                                   PrinterRepository printerRepository) {
        this.repository = repository;
        this.quoteRepository = quoteRepository;
        this.printerRepository = printerRepository;
    }

    @Transactional
    public ProductionOrderDtos.ConversionResult convert(Long quoteId, ProductionOrderDtos.CreateRequest request) {
        Quote quote = quoteRepository.findByIdForUpdate(quoteId)
                .orElseThrow(() -> new ResourceNotFoundException("Quote " + quoteId + " was not found"));
        ProductionOrder existing = repository.findByQuoteId(quoteId).orElse(null);
        if (existing != null) {
            return new ProductionOrderDtos.ConversionResult(toResponse(existing), false);
        }
        if (quote.getStatus() != QuoteStatus.ACCEPTED) {
            throw new BusinessRuleException("Only ACCEPTED quotes can be converted to production orders");
        }

        ProductionOrderDtos.CreateRequest values = request == null
                ? new ProductionOrderDtos.CreateRequest(null, null, null) : request;
        LocalDate dueDate = values.dueDate() != null ? values.dueDate() : quote.getEstimatedDeliveryDate();
        ProductionOrderPriority priority = values.priority() != null
                ? values.priority() : ProductionOrderPriority.NORMAL;
        ProductionOrder order = new ProductionOrder(quote, generateOrderNumber(), dueDate, priority,
                CustomerService.nullable(values.notes()));
        quote.getItems().stream().map(ProductionOrderItem::new).forEach(order::addItem);
        repository.save(order);
        repository.flush();
        return new ProductionOrderDtos.ConversionResult(toResponse(order), true);
    }

    @Transactional(readOnly = true)
    public List<ProductionOrderDtos.Response> list() {
        return repository.findAllForList().stream().map(ProductionOrderService::toResponse).toList();
    }

    @Transactional(readOnly = true)
    public ProductionOrderDtos.Response get(Long id) {
        return toResponse(find(id));
    }

    @Transactional(readOnly = true)
    public ProductionOrderDtos.Response getByQuote(Long quoteId) {
        return toResponse(repository.findByQuoteId(quoteId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Production order for quote " + quoteId + " was not found")));
    }

    @Transactional
    public ProductionOrderDtos.Response changeStatus(Long id, ProductionOrderStatus target) {
        ProductionOrder order = findForUpdate(id);
        ProductionOrderStatus current = order.getStatus();
        boolean valid = current == ProductionOrderStatus.PENDING && target == ProductionOrderStatus.IN_PRODUCTION
                || current == ProductionOrderStatus.IN_PRODUCTION && target == ProductionOrderStatus.READY
                || current == ProductionOrderStatus.READY && target == ProductionOrderStatus.DELIVERED
                || target == ProductionOrderStatus.CANCELLED
                && (current == ProductionOrderStatus.PENDING
                || current == ProductionOrderStatus.IN_PRODUCTION
                || current == ProductionOrderStatus.READY);
        if (!valid) {
            throw new BusinessRuleException("Production order status cannot change from " + current + " to " + target);
        }
        if (target == ProductionOrderStatus.READY && order.getItems().stream()
                .anyMatch(item -> item.getStatus() != ProductionOrderItemStatus.COMPLETED)) {
            throw new BusinessRuleException("All production order items must be COMPLETED before marking the order READY");
        }
        if (target == ProductionOrderStatus.CANCELLED) {
            blockActiveItemsAndReleasePrinters(order);
        }
        order.changeStatus(target);
        repository.flush();
        return toResponse(order);
    }

    @Transactional
    public ProductionOrderDtos.Response updateItem(Long orderId, Long itemId,
                                                    ProductionOrderDtos.ItemUpdateRequest request) {
        ProductionOrder order = findForUpdate(orderId);
        if (order.getStatus() == ProductionOrderStatus.READY
                || order.getStatus() == ProductionOrderStatus.DELIVERED
                || order.getStatus() == ProductionOrderStatus.CANCELLED) {
            throw new BusinessRuleException("Items cannot be modified when the production order is " + order.getStatus());
        }
        ProductionOrderItem item = order.getItems().stream()
                .filter(value -> Objects.equals(value.getId(), itemId))
                .findFirst()
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Production order item " + itemId + " was not found in order " + orderId));
        if (item.getVersion() != request.expectedVersion()) {
            throw new ConflictException("Production order item changed after it was loaded; reload and retry");
        }

        ProductionOrderItemStatus currentStatus = item.getStatus();
        if (currentStatus == ProductionOrderItemStatus.COMPLETED) {
            throw new BusinessRuleException("A COMPLETED production order item cannot be modified");
        }
        ProductionOrderItemStatus status = request.status() != null ? request.status() : item.getStatus();
        validateItemTransition(currentStatus, status);

        Long currentPrinterId = item.getAssignedPrinter() == null ? null : item.getAssignedPrinter().getId();
        Long requestedPrinterId = request.assignedPrinterId();
        boolean assignmentChanged = requestedPrinterId != null
                && !Objects.equals(currentPrinterId, requestedPrinterId);
        if (currentStatus == ProductionOrderItemStatus.IN_PROGRESS && assignmentChanged) {
            throw new BusinessRuleException("An IN_PROGRESS item must be BLOCKED before reassigning its printer");
        }
        Long effectivePrinterId = requestedPrinterId != null ? requestedPrinterId : currentPrinterId;
        Map<Long, Printer> lockedPrinters = lockRelevantPrinters(currentStatus, status,
                currentPrinterId, requestedPrinterId, effectivePrinterId);
        Printer assignedPrinter = effectivePrinterId == null ? null
                : lockedPrinters.getOrDefault(effectivePrinterId, item.getAssignedPrinter());

        if (assignmentChanged) {
            validateNewAssignment(assignedPrinter);
        }
        int completed = request.completedQuantity() != null
                ? request.completedQuantity() : item.getCompletedQuantity();
        validateProgress(item.getQuantity(), status, completed);
        if (status == ProductionOrderItemStatus.IN_PROGRESS
                && currentStatus != ProductionOrderItemStatus.IN_PROGRESS) {
            acquirePrinter(order, assignedPrinter);
        }
        String notes = request.notes() == null ? item.getNotes() : CustomerService.nullable(request.notes());
        applyItemTransition(item, status, assignedPrinter, completed, notes);
        if (currentStatus == ProductionOrderItemStatus.IN_PROGRESS
                && status != ProductionOrderItemStatus.IN_PROGRESS) {
            Printer currentPrinter = lockedPrinters.get(currentPrinterId);
            currentPrinter.release();
        }
        order.touch();
        repository.flush();
        return toResponse(order);
    }

    private Map<Long, Printer> lockRelevantPrinters(ProductionOrderItemStatus currentStatus,
                                                     ProductionOrderItemStatus targetStatus,
                                                     Long currentPrinterId,
                                                     Long requestedPrinterId,
                                                     Long effectivePrinterId) {
        Set<Long> ids = new TreeSet<>();
        if (currentStatus == ProductionOrderItemStatus.IN_PROGRESS && currentPrinterId != null) {
            ids.add(currentPrinterId);
        }
        if (requestedPrinterId != null) {
            ids.add(requestedPrinterId);
        }
        if (targetStatus == ProductionOrderItemStatus.IN_PROGRESS && effectivePrinterId != null) {
            ids.add(effectivePrinterId);
        }
        return ids.stream().map(this::findPrinterForUpdate)
                .collect(Collectors.toMap(Printer::getId, Function.identity()));
    }

    private Printer findPrinterForUpdate(Long id) {
        return printerRepository.findByIdForUpdate(id)
                .orElseThrow(() -> new ResourceNotFoundException("Printer " + id + " was not found"));
    }

    private static void validateNewAssignment(Printer printer) {
        if (!printer.isActive()) {
            throw new BusinessRuleException("Printer " + printer.getId() + " is inactive");
        }
        if (printer.getOperationalStatus() == PrinterOperationalStatus.MAINTENANCE
                || printer.getOperationalStatus() == PrinterOperationalStatus.OUT_OF_SERVICE) {
            throw new BusinessRuleException("Printer " + printer.getId() + " is "
                    + printer.getOperationalStatus() + " and cannot be assigned");
        }
    }

    private static void acquirePrinter(ProductionOrder order, Printer printer) {
        if (order.getStatus() != ProductionOrderStatus.IN_PRODUCTION) {
            throw new BusinessRuleException("An item can only be IN_PROGRESS when its order is IN_PRODUCTION");
        }
        if (printer == null) {
            throw new BusinessRuleException("An IN_PROGRESS item must have an assigned printer");
        }
        if (!printer.isActive()) {
            throw new BusinessRuleException("Printer " + printer.getId() + " is inactive");
        }
        if (printer.getOperationalStatus() == PrinterOperationalStatus.BUSY) {
            throw new ConflictException("Printer " + printer.getId() + " is already occupied");
        }
        if (printer.getOperationalStatus() != PrinterOperationalStatus.AVAILABLE) {
            throw new BusinessRuleException("Printer " + printer.getId() + " is "
                    + printer.getOperationalStatus() + " and cannot start production");
        }
        printer.occupy();
    }

    private static void applyItemTransition(ProductionOrderItem item, ProductionOrderItemStatus target,
                                            Printer printer, int completed, String notes) {
        switch (target) {
            case PENDING -> item.keepPending(printer, notes);
            case IN_PROGRESS -> {
                if (item.getStatus() == ProductionOrderItemStatus.IN_PROGRESS) {
                    item.updateInProgress(completed, notes);
                } else {
                    item.start(printer, completed, notes);
                }
            }
            case BLOCKED -> item.block(printer, completed, notes);
            case COMPLETED -> item.complete(notes);
        }
    }

    private static void validateItemTransition(ProductionOrderItemStatus current,
                                               ProductionOrderItemStatus target) {
        boolean valid = switch (current) {
            case PENDING -> target == ProductionOrderItemStatus.PENDING
                    || target == ProductionOrderItemStatus.IN_PROGRESS
                    || target == ProductionOrderItemStatus.BLOCKED;
            case IN_PROGRESS -> target == ProductionOrderItemStatus.IN_PROGRESS
                    || target == ProductionOrderItemStatus.BLOCKED
                    || target == ProductionOrderItemStatus.COMPLETED;
            case BLOCKED -> target == ProductionOrderItemStatus.BLOCKED
                    || target == ProductionOrderItemStatus.PENDING
                    || target == ProductionOrderItemStatus.IN_PROGRESS;
            case COMPLETED -> false;
        };
        if (!valid) {
            throw new BusinessRuleException("Production order item status cannot change from "
                    + current + " to " + target);
        }
    }

    private void blockActiveItemsAndReleasePrinters(ProductionOrder order) {
        List<ProductionOrderItem> activeItems = order.getItems().stream()
                .filter(item -> item.getStatus() == ProductionOrderItemStatus.IN_PROGRESS)
                .toList();
        Map<Long, Printer> printers = activeItems.stream()
                .map(item -> item.getAssignedPrinter().getId())
                .distinct().sorted()
                .map(this::findPrinterForUpdate)
                .collect(Collectors.toMap(Printer::getId, Function.identity()));
        activeItems.forEach(item -> {
            Printer printer = printers.get(item.getAssignedPrinter().getId());
            item.block(printer, item.getCompletedQuantity(), item.getNotes());
        });
        printers.values().forEach(Printer::release);
    }

    private ProductionOrder find(Long id) {
        return repository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Production order " + id + " was not found"));
    }

    private ProductionOrder findForUpdate(Long id) {
        return repository.findByIdForUpdate(id)
                .orElseThrow(() -> new ResourceNotFoundException("Production order " + id + " was not found"));
    }

    static void validateProgress(int quantity, ProductionOrderItemStatus status, int completed) {
        if (completed < 0 || completed > quantity) {
            throw new BusinessRuleException("Completed quantity must be between 0 and " + quantity);
        }
        if (status == ProductionOrderItemStatus.COMPLETED && completed != quantity) {
            throw new BusinessRuleException("A COMPLETED item must have its full quantity completed");
        }
        if (status == ProductionOrderItemStatus.PENDING && completed != 0) {
            throw new BusinessRuleException("A PENDING item cannot have completed units");
        }
        if (status != ProductionOrderItemStatus.COMPLETED && completed == quantity) {
            throw new BusinessRuleException("An item with its full quantity completed must be COMPLETED");
        }
    }

    static ProductionOrderDtos.Response toResponse(ProductionOrder order) {
        Quote quote = order.getQuote();
        String customerName = quote.getCustomerNameSnapshot() != null
                ? quote.getCustomerNameSnapshot() : quote.getCustomer().getName();
        String customerPhone = quote.getCustomerNameSnapshot() != null
                ? quote.getCustomerPhoneSnapshot() : quote.getCustomer().getPhone();
        String customerEmail = quote.getCustomerNameSnapshot() != null
                ? quote.getCustomerEmailSnapshot() : quote.getCustomer().getEmail();
        QuoteDtos.CustomerSummary customer = new QuoteDtos.CustomerSummary(quote.getCustomer().getId(),
                customerName, customerPhone, customerEmail);
        ProductionOrderDtos.QuoteSummary quoteSummary = new ProductionOrderDtos.QuoteSummary(
                quote.getId(), quote.getQuoteNumber(), quote.getTitle(), customer);
        List<ProductionOrderDtos.ItemResponse> items = order.getItems().stream()
                .map(ProductionOrderService::toItemResponse).toList();
        return new ProductionOrderDtos.Response(order.getId(), order.getOrderNumber(), quoteSummary,
                order.getStatus(), order.getPriority(), order.getDueDate(), order.getNotes(), order.getCreatedAt(),
                order.getUpdatedAt(), order.getStartedAt(), order.getReadyAt(), order.getDeliveredAt(),
                order.getCancelledAt(), items);
    }

    private static ProductionOrderDtos.ItemResponse toItemResponse(ProductionOrderItem item) {
        Printer assigned = item.getAssignedPrinter();
        ProductionOrderDtos.AssignedPrinter assignedResponse = assigned == null ? null
                : new ProductionOrderDtos.AssignedPrinter(assigned.getId(), assigned.getName(), assigned.getModel());
        return new ProductionOrderDtos.ItemResponse(item.getId(), item.getQuoteItem().getId(), item.getName(),
                item.getQuantity(), item.getMaterialName(), item.getPrinterName(), item.getPrinterModel(),
                item.getWeightGrams(), item.getPrintTimeMinutes(), assignedResponse, item.getCompletedQuantity(),
                item.getStatus(), item.getNotes(), item.getVersion());
    }

    private static String generateOrderNumber() {
        String token = UUID.randomUUID().toString().replace("-", "").substring(0, 10).toUpperCase();
        return "OP-" + Year.now().getValue() + "-" + token;
    }
}
