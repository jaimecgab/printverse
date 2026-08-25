package com.printverse.dto;

import jakarta.validation.Validation;
import jakarta.validation.Validator;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class DtoValidationTest {

    private static jakarta.validation.ValidatorFactory validatorFactory;
    private static Validator validator;

    @BeforeAll
    static void createValidator() {
        validatorFactory = Validation.buildDefaultValidatorFactory();
        validator = validatorFactory.getValidator();
    }

    @AfterAll
    static void closeValidator() {
        validatorFactory.close();
    }

    @Test
    void rejectsBlankCustomerDataAndMalformedEmail() {
        CustomerDtos.Request request = new CustomerDtos.Request(" ", "", "not-an-email", null);

        assertThat(validator.validate(request))
                .extracting(violation -> violation.getPropertyPath().toString())
                .contains("name", "phone", "email");
    }

    @Test
    void rejectsInvalidItemQuantityCostsAndPercentages() {
        QuoteDtos.ItemRequest request = new QuoteDtos.ItemRequest("Part", 0, 1L, 1L,
                new BigDecimal("-1"), -1, new BigDecimal("101"), new BigDecimal("-0.01"), null);

        assertThat(validator.validate(request))
                .extracting(violation -> violation.getPropertyPath().toString())
                .contains("quantity", "weightGrams", "printTimeMinutes", "failureRiskPercentage", "manualUnitPrice");
    }

    @Test
    void rejectsNegativeCatalogPrices() {
        MaterialDtos.Request material = new MaterialDtos.Request("PLA", new BigDecimal("-1"),
                null, null, null, new BigDecimal("-0.001"), new BigDecimal("-1"), null, true);
        PrinterDtos.Request printer = new PrinterDtos.Request("K1C", null, new BigDecimal("-1"),
                null, null, true);

        assertThat(validator.validate(material)).extracting(value -> value.getPropertyPath().toString())
                .contains("pricePerKg", "stockGrams", "lowStockThresholdGrams");
        assertThat(validator.validate(printer)).extracting(value -> value.getPropertyPath().toString())
                .contains("costPerHour", "operationalStatus");
    }

    @Test
    void rejectsCatalogAssetFieldsOutsideTheirLimits() {
        MaterialDtos.Request material = new MaterialDtos.Request("PLA", BigDecimal.ZERO,
                "x".repeat(61), "x".repeat(101), "x".repeat(81), new BigDecimal("1.0000"),
                new BigDecimal("100000000000.000"), "x".repeat(1001), true);
        PrinterDtos.Request printer = new PrinterDtos.Request("K1C", null, BigDecimal.ZERO,
                com.printverse.domain.PrinterOperationalStatus.AVAILABLE, "x".repeat(1001), true);

        assertThat(validator.validate(material)).extracting(value -> value.getPropertyPath().toString())
                .contains("materialType", "brand", "color", "stockGrams",
                        "lowStockThresholdGrams", "notes");
        assertThat(validator.validate(printer)).extracting(value -> value.getPropertyPath().toString())
                .contains("notes");
    }

    @Test
    void requiresPrintTimeAndRejectsUnsupportedWeightPrecision() {
        QuoteDtos.ItemRequest missingTime = new QuoteDtos.ItemRequest("Part", 1, 1L, 1L,
                new BigDecimal("10.000"), null, BigDecimal.ZERO, null, null);
        QuoteDtos.ItemRequest excessivePrecision = new QuoteDtos.ItemRequest("Part", 1, 1L, 1L,
                new BigDecimal("1.2345"), 10, BigDecimal.ZERO, null, null);

        assertThat(validator.validate(missingTime)).extracting(value -> value.getPropertyPath().toString())
                .contains("printTimeMinutes");
        assertThat(validator.validate(excessivePrecision)).extracting(value -> value.getPropertyPath().toString())
                .contains("weightGrams");
    }

    @Test
    void preservesOmittedChargesAndValidatesNestedChargesIdsAndCommercialLimits() {
        QuoteDtos.ItemRequest omitted = new QuoteDtos.ItemRequest("Part", 1, 1L, 1L,
                BigDecimal.ONE, 10, BigDecimal.ZERO, null, null);
        QuoteDtos.ItemRequest invalid = new QuoteDtos.ItemRequest("Part", 100001, 0L, -1L,
                BigDecimal.ONE, 525601, BigDecimal.ZERO, null,
                List.of(new QuoteDtos.ChargeRequest(" ", new BigDecimal("-1"))));

        assertThat(omitted.additionalCharges()).isNull();
        assertThat(validator.validate(invalid))
                .extracting(violation -> violation.getPropertyPath().toString())
                .contains("quantity", "materialId", "printerId", "printTimeMinutes",
                        "additionalCharges[0].description", "additionalCharges[0].amount");
    }

    @Test
    void validatesProductionItemPatchBounds() {
        ProductionOrderDtos.ItemUpdateRequest request = new ProductionOrderDtos.ItemUpdateRequest(
                0L, null, -1, "x".repeat(2001), null);

        assertThat(validator.validate(request))
                .extracting(violation -> violation.getPropertyPath().toString())
                .contains("assignedPrinterId", "completedQuantity", "notes", "expectedVersion");
    }
}
