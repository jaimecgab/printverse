package com.printverse.dto;

import jakarta.validation.Validation;
import jakarta.validation.Validator;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

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
                new BigDecimal("-1"), -1, new BigDecimal("101"), new BigDecimal("-0.01"));

        assertThat(validator.validate(request))
                .extracting(violation -> violation.getPropertyPath().toString())
                .contains("quantity", "weightGrams", "printTimeMinutes", "failureRiskPercentage", "manualUnitPrice");
    }

    @Test
    void rejectsNegativeCatalogPrices() {
        MaterialDtos.Request material = new MaterialDtos.Request("PLA", new BigDecimal("-1"), true);
        PrinterDtos.Request printer = new PrinterDtos.Request("K1C", null, new BigDecimal("-1"), true);

        assertThat(validator.validate(material)).extracting(value -> value.getPropertyPath().toString())
                .contains("pricePerKg");
        assertThat(validator.validate(printer)).extracting(value -> value.getPropertyPath().toString())
                .contains("costPerHour");
    }

    @Test
    void requiresPrintTimeAndRejectsUnsupportedWeightPrecision() {
        QuoteDtos.ItemRequest missingTime = new QuoteDtos.ItemRequest("Part", 1, 1L, 1L,
                new BigDecimal("10.000"), null, BigDecimal.ZERO, null);
        QuoteDtos.ItemRequest excessivePrecision = new QuoteDtos.ItemRequest("Part", 1, 1L, 1L,
                new BigDecimal("1.2345"), 10, BigDecimal.ZERO, null);

        assertThat(validator.validate(missingTime)).extracting(value -> value.getPropertyPath().toString())
                .contains("printTimeMinutes");
        assertThat(validator.validate(excessivePrecision)).extracting(value -> value.getPropertyPath().toString())
                .contains("weightGrams");
    }
}
