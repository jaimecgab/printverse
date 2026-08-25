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
import com.printverse.exception.ResourceNotFoundException;
import com.printverse.repository.QuoteRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Year;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

import static com.printverse.service.MoneyUtils.ROUNDING_MODE;
import static com.printverse.service.MoneyUtils.money;
import static com.printverse.service.MoneyUtils.percentage;

@Service
public class QuoteService {

    private final QuoteRepository quoteRepository;
    private final CustomerService customerService;
    private final MaterialService materialService;
    private final PrinterService printerService;
    private final QuoteCalculationService calculationService;

    public QuoteService(QuoteRepository quoteRepository,
                        CustomerService customerService,
                        MaterialService materialService,
                        PrinterService printerService,
                        QuoteCalculationService calculationService) {
        this.quoteRepository = quoteRepository;
        this.customerService = customerService;
        this.materialService = materialService;
        this.printerService = printerService;
        this.calculationService = calculationService;
    }

    @Transactional
    public QuoteDtos.Response create(QuoteDtos.CreateRequest request) {
        Customer customer = customerService.find(request.customerId());
        Quote quote = new Quote(generateQuoteNumber(), customer, request.validUntil(),
                request.estimatedDeliveryDate(), normalizedPercentage(request.depositPercentage()),
                CustomerService.nullable(request.notes()), normalizedPercentage(request.markupPercentage()),
                normalizedPercentage(orZero(request.discountPercentage())), request.taxEnabled(),
                normalizedPercentage(request.taxPercentage()));
        quote.setTitle(CustomerService.nullable(request.title()));
        quote.setInternalNotes(CustomerService.nullable(request.internalNotes()));
        calculationService.recalculate(quote);
        return toResponse(quoteRepository.save(quote));
    }

    @Transactional(readOnly = true)
    public List<QuoteDtos.SummaryResponse> list() {
        return quoteRepository.findAllByOrderByCreatedAtDesc().stream()
                .map(QuoteService::toSummaryResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public QuoteDtos.Response get(Long id) {
        return toResponse(find(id));
    }

    @Transactional
    public QuoteDtos.Response update(Long id, QuoteDtos.UpdateRequest request) {
        Quote quote = findForUpdate(id);
        ensureDraft(quote);
        quote.setTitle(CustomerService.nullable(request.title()));
        quote.setValidUntil(request.validUntil());
        quote.setEstimatedDeliveryDate(request.estimatedDeliveryDate());
        quote.setDepositPercentage(normalizedPercentage(request.depositPercentage()));
        quote.setNotes(CustomerService.nullable(request.notes()));
        quote.setInternalNotes(CustomerService.nullable(request.internalNotes()));
        quote.setMarkupPercentage(normalizedPercentage(request.markupPercentage()));
        quote.setDiscountPercentage(normalizedPercentage(request.discountPercentage()));
        quote.setTaxEnabled(request.taxEnabled());
        quote.setTaxPercentage(normalizedPercentage(request.taxPercentage()));
        calculationService.recalculate(quote);
        quote.touch();
        return toResponse(quote);
    }

    @Transactional
    public QuoteDtos.Response addItem(Long quoteId, QuoteDtos.ItemRequest request) {
        Quote quote = findForUpdate(quoteId);
        ensureDraft(quote);
        Material material = requireActiveMaterial(request.materialId());
        Printer printer = requireActivePrinter(request.printerId());
        QuoteItem item = new QuoteItem(CustomerService.clean(request.name()), request.quantity(), material, printer,
                normalizedWeight(request.weightGrams()), request.printTimeMinutes(),
                normalizedPercentage(request.failureRiskPercentage()), normalizeMoney(request.manualUnitPrice()));
        replaceAdditionalCharges(item, request.additionalCharges() == null ? List.of() : request.additionalCharges());
        quote.addItem(item);
        calculationService.recalculate(quote);
        quote.touch();
        quoteRepository.flush();
        return toResponse(quote);
    }

    @Transactional
    public QuoteDtos.Response updateItem(Long quoteId, Long itemId, QuoteDtos.ItemRequest request) {
        Quote quote = findForUpdate(quoteId);
        ensureDraft(quote);
        QuoteItem item = findItem(quote, itemId);
        Material material = materialService.find(request.materialId());
        Printer printer = printerService.find(request.printerId());

        if (!Objects.equals(item.getMaterial().getId(), material.getId())) {
            ensureActive(material);
            item.changeMaterial(material);
        }
        if (!Objects.equals(item.getPrinter().getId(), printer.getId())) {
            ensureActive(printer);
            item.changePrinter(printer);
        }

        item.setName(CustomerService.clean(request.name()));
        item.setQuantity(request.quantity());
        item.setWeightGrams(normalizedWeight(request.weightGrams()));
        item.setPrintTimeMinutes(request.printTimeMinutes());
        item.setFailureRiskPercentage(normalizedPercentage(request.failureRiskPercentage()));
        item.setManualUnitPrice(normalizeMoney(request.manualUnitPrice()));
        if (request.additionalCharges() != null) {
            replaceAdditionalCharges(item, request.additionalCharges());
        }
        calculationService.recalculate(quote);
        quote.touch();
        quoteRepository.flush();
        return toResponse(quote);
    }

    @Transactional
    public void removeItem(Long quoteId, Long itemId) {
        Quote quote = findForUpdate(quoteId);
        ensureDraft(quote);
        quote.removeItem(findItem(quote, itemId));
        calculationService.recalculate(quote);
        quote.touch();
    }

    @Transactional
    public QuoteDtos.Response addCharge(Long quoteId, Long itemId, QuoteDtos.ChargeRequest request) {
        Quote quote = findForUpdate(quoteId);
        ensureDraft(quote);
        QuoteItem item = findItem(quote, itemId);
        item.addAdditionalCharge(new AdditionalCharge(CustomerService.clean(request.description()), money(request.amount())));
        calculationService.recalculate(quote);
        quote.touch();
        quoteRepository.flush();
        return toResponse(quote);
    }

    @Transactional
    public void removeCharge(Long quoteId, Long itemId, Long chargeId) {
        Quote quote = findForUpdate(quoteId);
        ensureDraft(quote);
        QuoteItem item = findItem(quote, itemId);
        AdditionalCharge charge = item.getAdditionalCharges().stream()
                .filter(value -> Objects.equals(value.getId(), chargeId))
                .findFirst()
                .orElseThrow(() -> new ResourceNotFoundException("Additional charge " + chargeId + " was not found in item " + itemId));
        item.removeAdditionalCharge(charge);
        calculationService.recalculate(quote);
        quote.touch();
    }

    @Transactional
    public QuoteDtos.Response recalculate(Long id) {
        Quote quote = findForUpdate(id);
        ensureDraft(quote);
        calculationService.recalculate(quote);
        quote.touch();
        return toResponse(quote);
    }

    @Transactional
    public QuoteDtos.Response duplicate(Long id, QuoteDtos.DuplicateRequest request) {
        Quote source = findForUpdate(id);
        Quote duplicate = new Quote(generateQuoteNumber(), source.getCustomer(), request.validUntil(),
                request.estimatedDeliveryDate(), source.getDepositPercentage(), source.getNotes(),
                source.getMarkupPercentage(), source.getDiscountPercentage(), source.isTaxEnabled(),
                source.getTaxPercentage());
        duplicate.setTitle(source.getTitle());
        duplicate.setInternalNotes(source.getInternalNotes());
        duplicate.copyCustomerSnapshotsFrom(source);

        for (QuoteItem sourceItem : source.getItems()) {
            QuoteItem item = new QuoteItem(sourceItem.getName(), sourceItem.getQuantity(), sourceItem.getMaterial(),
                    sourceItem.getPrinter(), sourceItem.getWeightGrams(), sourceItem.getPrintTimeMinutes(),
                    sourceItem.getFailureRiskPercentage(), sourceItem.getManualUnitPrice());
            item.copySnapshotsFrom(sourceItem);
            item.replaceAdditionalCharges(sourceItem.getAdditionalCharges().stream()
                    .map(charge -> new AdditionalCharge(charge.getDescription(), charge.getAmount()))
                    .toList());
            duplicate.addItem(item);
        }

        calculationService.recalculate(duplicate);
        quoteRepository.save(duplicate);
        quoteRepository.flush();
        return toResponse(duplicate);
    }

    @Transactional
    public QuoteDtos.Response changeStatus(Long id, QuoteStatus targetStatus) {
        Quote quote = findForUpdate(id);
        QuoteStatus current = quote.getStatus();
        boolean validTransition = current == QuoteStatus.DRAFT && targetStatus == QuoteStatus.SENT
                || current == QuoteStatus.SENT
                && (targetStatus == QuoteStatus.ACCEPTED || targetStatus == QuoteStatus.REJECTED);
        if (!validTransition) {
            throw new BusinessRuleException("Quote status cannot change from " + current + " to " + targetStatus);
        }
        if (current == QuoteStatus.DRAFT && quote.getItems().isEmpty()) {
            throw new BusinessRuleException("A quote must contain at least one item before it can be sent");
        }
        quote.transitionTo(targetStatus);
        return toResponse(quote);
    }

    Quote find(Long id) {
        return quoteRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Quote " + id + " was not found"));
    }

    private Quote findForUpdate(Long id) {
        return quoteRepository.findByIdForUpdate(id)
                .orElseThrow(() -> new ResourceNotFoundException("Quote " + id + " was not found"));
    }

    private Material requireActiveMaterial(Long id) {
        Material material = materialService.find(id);
        ensureActive(material);
        return material;
    }

    private Printer requireActivePrinter(Long id) {
        Printer printer = printerService.find(id);
        ensureActive(printer);
        return printer;
    }

    private static void ensureActive(Material material) {
        if (!material.isActive()) {
            throw new BusinessRuleException("Material " + material.getId() + " is inactive");
        }
    }

    private static void ensureActive(Printer printer) {
        if (!printer.isActive()) {
            throw new BusinessRuleException("Printer " + printer.getId() + " is inactive");
        }
    }

    private static void ensureDraft(Quote quote) {
        if (quote.getStatus() != QuoteStatus.DRAFT) {
            throw new BusinessRuleException("Only DRAFT quotes can be modified");
        }
    }

    private static QuoteItem findItem(Quote quote, Long itemId) {
        return quote.getItems().stream()
                .filter(item -> Objects.equals(item.getId(), itemId))
                .findFirst()
                .orElseThrow(() -> new ResourceNotFoundException("Quote item " + itemId + " was not found in quote " + quote.getId()));
    }

    private static BigDecimal normalizedPercentage(BigDecimal value) {
        return value == null ? null : value.setScale(4, ROUNDING_MODE);
    }

    private static BigDecimal normalizeMoney(BigDecimal value) {
        return value == null ? null : money(value);
    }

    private static BigDecimal normalizedWeight(BigDecimal value) {
        return value.setScale(3, ROUNDING_MODE);
    }

    private static BigDecimal orZero(BigDecimal value) {
        return value == null ? BigDecimal.ZERO : value;
    }

    private static void replaceAdditionalCharges(QuoteItem item, List<QuoteDtos.ChargeRequest> requests) {
        item.replaceAdditionalCharges(requests.stream()
                .map(request -> new AdditionalCharge(CustomerService.clean(request.description()), money(request.amount())))
                .toList());
    }

    private static String generateQuoteNumber() {
        String token = UUID.randomUUID().toString().replace("-", "").substring(0, 12).toUpperCase();
        return "PV-" + Year.now().getValue() + "-" + token;
    }

    static QuoteDtos.SummaryResponse toSummaryResponse(Quote quote) {
        return new QuoteDtos.SummaryResponse(quote.getId(), quote.getQuoteNumber(), quote.getTitle(), customerSummary(quote),
                quote.getStatus(), quote.getCreatedAt(), quote.getUpdatedAt(), quote.getValidUntil(),
                quote.getCurrencyCode(), quote.getTotal(),
                quote.getEstimatedProfit(), quote.getRealMarginPercentage());
    }

    private static QuoteDtos.Response toResponse(Quote quote) {
        List<QuoteDtos.ItemResponse> items = quote.getItems().stream().map(QuoteService::itemResponse).toList();
        BigDecimal depositAmount = quote.getDepositPercentage() == null
                ? money(BigDecimal.ZERO)
                : money(quote.getTotal().multiply(percentage(quote.getDepositPercentage())));
        BigDecimal remainingBalance = money(quote.getTotal().subtract(depositAmount));
        return new QuoteDtos.Response(quote.getId(), quote.getQuoteNumber(), quote.getTitle(), customerSummary(quote),
                quote.getStatus(), quote.getCreatedAt(), quote.getUpdatedAt(), quote.getSentAt(), quote.getAcceptedAt(),
                quote.getRejectedAt(), quote.getValidUntil(), quote.getEstimatedDeliveryDate(),
                quote.getDepositPercentage(), quote.getNotes(), quote.getInternalNotes(), quote.getCurrencyCode(),
                quote.getMarkupPercentage(), quote.getDiscountPercentage(),
                quote.isTaxEnabled(), quote.getTaxPercentage(), quote.getInternalCost(), quote.getSuggestedSubtotal(),
                quote.getFinalSubtotal(), quote.getDiscountAmount(), money(quote.getFinalSubtotal().subtract(quote.getDiscountAmount())),
                quote.getTaxAmount(), quote.getTotal(), depositAmount, remainingBalance, quote.getEstimatedProfit(),
                quote.getRealMarginPercentage(), items);
    }

    private static QuoteDtos.CustomerSummary customerSummary(Quote quote) {
        Customer customer = quote.getCustomer();
        if (quote.getCustomerNameSnapshot() != null) {
            return new QuoteDtos.CustomerSummary(customer.getId(), quote.getCustomerNameSnapshot(),
                    quote.getCustomerPhoneSnapshot(), quote.getCustomerEmailSnapshot());
        }
        return new QuoteDtos.CustomerSummary(customer.getId(), customer.getName(), customer.getPhone(), customer.getEmail());
    }

    private static QuoteDtos.ItemResponse itemResponse(QuoteItem item) {
        Material material = item.getMaterial();
        Printer printer = item.getPrinter();
        List<QuoteDtos.ChargeResponse> charges = item.getAdditionalCharges().stream()
                .map(charge -> new QuoteDtos.ChargeResponse(charge.getId(), charge.getDescription(), charge.getAmount()))
                .toList();
        String materialName = item.getMaterialNameSnapshot() != null
                ? item.getMaterialNameSnapshot() : material.getName();
        String printerName = item.getPrinterNameSnapshot() != null
                ? item.getPrinterNameSnapshot() : printer.getName();
        String printerModel = item.getPrinterNameSnapshot() != null
                ? item.getPrinterModelSnapshot() : printer.getModel();
        return new QuoteDtos.ItemResponse(item.getId(), item.getName(), item.getQuantity(),
                new QuoteDtos.MaterialSummary(material.getId(), materialName),
                new QuoteDtos.PrinterSummary(printer.getId(), printerName, printerModel),
                item.getWeightGrams(), item.getPrintTimeMinutes(), item.getFailureRiskPercentage(),
                item.getMaterialPricePerKgSnapshot(), item.getPrinterCostPerHourSnapshot(), item.getMaterialCostUnit(),
                item.getMachineCostUnit(), item.getFailureRiskCostUnit(), item.getAdditionalChargesUnit(),
                item.getInternalCostUnit(), item.getSuggestedPriceUnit(), item.getManualUnitPrice(),
                item.getFinalUnitPrice(), money(item.getFinalUnitPrice().multiply(BigDecimal.valueOf(item.getQuantity()))), charges);
    }
}
