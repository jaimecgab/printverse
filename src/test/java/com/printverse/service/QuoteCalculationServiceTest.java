package com.printverse.service;

import com.printverse.domain.AdditionalCharge;
import com.printverse.domain.Customer;
import com.printverse.domain.Material;
import com.printverse.domain.Printer;
import com.printverse.domain.Quote;
import com.printverse.domain.QuoteItem;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;

class QuoteCalculationServiceTest {

    private final QuoteCalculationService service = new QuoteCalculationService();

    @Test
    void calculatesTechnicalCostsMarkupQuantityDiscountTaxProfitAndMargin() {
        Quote quote = quote("50", "10", true, "16");
        QuoteItem item = item("200", 120, "10", 2, null);
        item.addAdditionalCharge(new AdditionalCharge("Post-processing", new BigDecimal("11.00")));
        quote.addItem(item);

        service.recalculate(quote);

        assertMoney(item.getMaterialCostUnit(), "70.00");
        assertMoney(item.getMachineCostUnit(), "20.00");
        assertMoney(item.getFailureRiskCostUnit(), "9.00");
        assertMoney(item.getAdditionalChargesUnit(), "11.00");
        assertMoney(item.getInternalCostUnit(), "110.00");
        assertMoney(item.getSuggestedPriceUnit(), "165.00");
        assertMoney(quote.getInternalCost(), "220.00");
        assertMoney(quote.getSuggestedSubtotal(), "330.00");
        assertMoney(quote.getFinalSubtotal(), "330.00");
        assertMoney(quote.getDiscountAmount(), "33.00");
        assertMoney(quote.getTaxAmount(), "47.52");
        assertMoney(quote.getTotal(), "344.52");
        assertMoney(quote.getEstimatedProfit(), "77.00");
        assertThat(quote.getRealMarginPercentage()).isEqualByComparingTo("25.9259");
    }

    @Test
    void manualUnitPriceOverridesFinalPriceWithoutLosingSuggestion() {
        Quote quote = quote("50", "10", true, "16");
        QuoteItem item = item("200", 120, "10", 2, "150.00");
        item.addAdditionalCharge(new AdditionalCharge("Post-processing", new BigDecimal("11.00")));
        quote.addItem(item);

        service.recalculate(quote);

        assertMoney(item.getSuggestedPriceUnit(), "165.00");
        assertMoney(item.getFinalUnitPrice(), "150.00");
        assertMoney(quote.getSuggestedSubtotal(), "330.00");
        assertMoney(quote.getFinalSubtotal(), "300.00");
        assertMoney(quote.getDiscountAmount(), "30.00");
        assertMoney(quote.getTaxAmount(), "43.20");
        assertMoney(quote.getTotal(), "313.20");
        assertMoney(quote.getEstimatedProfit(), "50.00");
        assertThat(quote.getRealMarginPercentage()).isEqualByComparingTo("18.5185");
    }

    @Test
    void disabledTaxAlwaysProducesZeroTax() {
        Quote quote = quote("0", "0", false, "16");
        quote.addItem(item("100", 60, "0", 1, null));

        service.recalculate(quote);

        assertMoney(quote.getTaxAmount(), "0.00");
        assertMoney(quote.getTotal(), quote.getFinalSubtotal().toPlainString());
    }

    @Test
    void catalogChangesDoNotAlterStoredSnapshotsOrHistoricalCalculation() {
        Material material = new Material("PLA", new BigDecimal("350.00"), true);
        Printer printer = new Printer("K1C", "K1C", new BigDecimal("10.00"), true);
        QuoteItem item = new QuoteItem("Part", 1, material, printer, new BigDecimal("100"),
                60, BigDecimal.ZERO, null);
        Quote quote = quote("0", "0", false, "0");
        quote.addItem(item);

        material.setPricePerKg(new BigDecimal("420.00"));
        printer.setCostPerHour(new BigDecimal("15.00"));
        service.recalculate(quote);

        assertMoney(item.getMaterialPricePerKgSnapshot(), "350.00");
        assertMoney(item.getPrinterCostPerHourSnapshot(), "10.00");
        assertMoney(item.getMaterialCostUnit(), "35.00");
        assertMoney(item.getMachineCostUnit(), "10.00");
        assertMoney(item.getInternalCostUnit(), "45.00");
    }

    @Test
    void zeroSubtotalProducesZeroMarginWithoutDivisionError() {
        Quote quote = quote("0", "0", false, "0");
        quote.addItem(item("0", 0, "0", 1, null));

        service.recalculate(quote);

        assertMoney(quote.getTotal(), "0.00");
        assertThat(quote.getRealMarginPercentage()).isEqualByComparingTo("0.0000");
    }

    private static Quote quote(String markup, String discount, boolean taxEnabled, String tax) {
        Customer customer = new Customer("Customer", "555-0100", null, null);
        return new Quote("PV-TEST", customer, LocalDate.now().plusDays(15), null, null, null,
                new BigDecimal(markup), new BigDecimal(discount), taxEnabled, new BigDecimal(tax));
    }

    private static QuoteItem item(String weight, int minutes, String risk, int quantity, String manualPrice) {
        Material material = new Material("PLA", new BigDecimal("350.00"), true);
        Printer printer = new Printer("K1C", "K1C", new BigDecimal("10.00"), true);
        return new QuoteItem("Part", quantity, material, printer, new BigDecimal(weight), minutes,
                new BigDecimal(risk), manualPrice == null ? null : new BigDecimal(manualPrice));
    }

    private static void assertMoney(BigDecimal actual, String expected) {
        assertThat(actual).isEqualByComparingTo(expected);
    }
}
