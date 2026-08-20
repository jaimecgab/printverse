package com.printverse.service;

import java.math.BigDecimal;
import java.math.RoundingMode;

public final class MoneyUtils {

    public static final int MONEY_SCALE = 2;
    public static final int CALCULATION_SCALE = 10;
    public static final RoundingMode ROUNDING_MODE = RoundingMode.HALF_UP;
    public static final BigDecimal ONE_HUNDRED = new BigDecimal("100");

    private MoneyUtils() {
    }

    public static BigDecimal money(BigDecimal value) {
        return value.setScale(MONEY_SCALE, ROUNDING_MODE);
    }

    public static BigDecimal percentage(BigDecimal value) {
        return value.divide(ONE_HUNDRED, CALCULATION_SCALE, ROUNDING_MODE);
    }
}
