package com.printverse.service;

import com.printverse.domain.AdditionalCharge;
import com.printverse.domain.Quote;
import com.printverse.domain.QuoteItem;
import com.printverse.exception.BusinessRuleException;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;

import static com.printverse.service.MoneyUtils.CALCULATION_SCALE;
import static com.printverse.service.MoneyUtils.ROUNDING_MODE;
import static com.printverse.service.MoneyUtils.money;
import static com.printverse.service.MoneyUtils.percentage;

@Service
public class QuoteCalculationService {

    private static final BigDecimal GRAMS_PER_KILOGRAM = new BigDecimal("1000");
    private static final BigDecimal MINUTES_PER_HOUR = new BigDecimal("60");
    private static final BigDecimal MAX_ITEM_MONEY = new BigDecimal("999999999999.99");
    private static final BigDecimal MAX_QUOTE_MONEY = new BigDecimal("99999999999999.99");
    private static final BigDecimal MAX_MARGIN = new BigDecimal("999999.9999");

    public void recalculate(Quote quote) {
        BigDecimal internalCost = BigDecimal.ZERO;
        BigDecimal suggestedSubtotal = BigDecimal.ZERO;
        BigDecimal finalSubtotal = BigDecimal.ZERO;

        for (QuoteItem item : quote.getItems()) {
            calculateItem(item, quote.getMarkupPercentage());
            BigDecimal quantity = BigDecimal.valueOf(item.getQuantity());
            internalCost = internalCost.add(item.getInternalCostUnit().multiply(quantity));
            suggestedSubtotal = suggestedSubtotal.add(item.getSuggestedPriceUnit().multiply(quantity));
            finalSubtotal = finalSubtotal.add(item.getFinalUnitPrice().multiply(quantity));
        }

        internalCost = money(internalCost);
        suggestedSubtotal = money(suggestedSubtotal);
        finalSubtotal = money(finalSubtotal);
        BigDecimal discountAmount = money(finalSubtotal.multiply(percentage(quote.getDiscountPercentage())));
        BigDecimal subtotalAfterDiscount = money(finalSubtotal.subtract(discountAmount));
        BigDecimal taxAmount = quote.isTaxEnabled()
                ? money(subtotalAfterDiscount.multiply(percentage(quote.getTaxPercentage())))
                : money(BigDecimal.ZERO);
        BigDecimal total = money(subtotalAfterDiscount.add(taxAmount));
        BigDecimal estimatedProfit = money(subtotalAfterDiscount.subtract(internalCost));
        BigDecimal margin = subtotalAfterDiscount.signum() > 0
                ? estimatedProfit.divide(subtotalAfterDiscount, CALCULATION_SCALE, ROUNDING_MODE)
                    .multiply(MoneyUtils.ONE_HUNDRED).setScale(4, ROUNDING_MODE)
                : BigDecimal.ZERO.setScale(4, ROUNDING_MODE);

        requireQuoteMoney(internalCost, "Internal cost");
        requireQuoteMoney(suggestedSubtotal, "Suggested subtotal");
        requireQuoteMoney(finalSubtotal, "Final subtotal");
        requireQuoteMoney(discountAmount, "Discount amount");
        requireQuoteMoney(taxAmount, "Tax amount");
        requireQuoteMoney(total, "Quote total");
        requireQuoteMoney(estimatedProfit, "Estimated profit");
        requireWithin(margin, MAX_MARGIN, "Real margin percentage");

        quote.setInternalCost(internalCost);
        quote.setSuggestedSubtotal(suggestedSubtotal);
        quote.setFinalSubtotal(finalSubtotal);
        quote.setDiscountAmount(discountAmount);
        quote.setTaxAmount(taxAmount);
        quote.setTotal(total);
        quote.setEstimatedProfit(estimatedProfit);
        quote.setRealMarginPercentage(margin);
    }

    private void calculateItem(QuoteItem item, BigDecimal markupPercentage) {
        BigDecimal materialCost = money(item.getWeightGrams()
                .divide(GRAMS_PER_KILOGRAM, CALCULATION_SCALE, ROUNDING_MODE)
                .multiply(item.getMaterialPricePerKgSnapshot()));
        BigDecimal machineCost = money(BigDecimal.valueOf(item.getPrintTimeMinutes())
                .divide(MINUTES_PER_HOUR, CALCULATION_SCALE, ROUNDING_MODE)
                .multiply(item.getPrinterCostPerHourSnapshot()));
        BigDecimal technicalBase = materialCost.add(machineCost);
        BigDecimal failureCost = money(technicalBase.multiply(percentage(item.getFailureRiskPercentage())));
        BigDecimal charges = money(item.getAdditionalCharges().stream()
                .map(AdditionalCharge::getAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add));
        BigDecimal internalCost = money(technicalBase.add(failureCost).add(charges));
        BigDecimal suggestedPrice = money(internalCost.multiply(BigDecimal.ONE.add(percentage(markupPercentage))));

        requireItemMoney(materialCost, "Material cost per unit");
        requireItemMoney(machineCost, "Machine cost per unit");
        requireItemMoney(failureCost, "Failure risk cost per unit");
        requireItemMoney(charges, "Additional charges per unit");
        requireItemMoney(internalCost, "Internal cost per unit");
        requireItemMoney(suggestedPrice, "Suggested price per unit");
        requireItemMoney(item.getFinalUnitPrice(), "Final price per unit");

        item.setMaterialCostUnit(materialCost);
        item.setMachineCostUnit(machineCost);
        item.setFailureRiskCostUnit(failureCost);
        item.setAdditionalChargesUnit(charges);
        item.setInternalCostUnit(internalCost);
        item.setSuggestedPriceUnit(suggestedPrice);
        if (item.getManualUnitPrice() != null) {
            item.setManualUnitPrice(money(item.getManualUnitPrice()));
        }
    }

    private static void requireItemMoney(BigDecimal value, String field) {
        requireWithin(value, MAX_ITEM_MONEY, field);
    }

    private static void requireQuoteMoney(BigDecimal value, String field) {
        requireWithin(value, MAX_QUOTE_MONEY, field);
    }

    private static void requireWithin(BigDecimal value, BigDecimal maximum, String field) {
        if (value.abs().compareTo(maximum) > 0) {
            throw new BusinessRuleException(
                    field + " exceeds the maximum supported value of " + maximum.toPlainString());
        }
    }
}
