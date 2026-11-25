package dev.memolabs.fec.core;

import java.math.BigDecimal;
import java.math.RoundingMode;

/** Amounts written the French way: two decimals and a decimal comma. */
public final class Amounts {

    private Amounts() {
    }

    public static String french(BigDecimal value) {
        return value.setScale(2, RoundingMode.HALF_UP).toPlainString().replace('.', ',');
    }
}
