package com.printverse.service;

import com.printverse.domain.AdditionalCharge;
import com.printverse.domain.Quote;
import com.printverse.domain.QuoteItem;
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
}
