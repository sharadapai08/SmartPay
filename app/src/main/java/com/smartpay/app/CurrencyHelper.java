package com.smartpay.app;

import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.util.Locale;

/**
 * Helper class for currency calculations and Indian Rupee formatting.
 * Matches Project Brief requirement:
 * "Use paise-based (long) or BigDecimal money handling, or at least Math.round(x*100)/100.0,
 * to avoid floating point bugs. Format with NumberFormat for the en-IN locale and the ₹ symbol."
 */
public final class CurrencyHelper {

    private CurrencyHelper() {
    }

    /**
     * Formats a double amount into Indian Rupee format, e.g. "₹ 15,680.00".
     */
    public static String formatRupees(double amount) {
        double rounded = round(amount);
        DecimalFormatSymbols symbols = new DecimalFormatSymbols(new Locale("en", "IN"));
        symbols.setCurrencySymbol("₹ ");
        DecimalFormat formatter = new DecimalFormat("₹ #,##,##0.00", symbols);
        return formatter.format(rounded);
    }

    /**
     * Formats amount with a signed prefix:
     * Positive (Received): "+ ₹ 1,500.00"
     * Negative (Sent): "- ₹ 2,000.00"
     */
    public static String formatSignedRupees(double amount, boolean isCredit) {
        String base = formatRupees(Math.abs(amount));
        return isCredit ? "+ " + base : "- " + base;
    }

    /**
     * Rounds amount to 2 decimal places to prevent floating-point inaccuracies.
     */
    public static double round(double amount) {
        return Math.round(amount * 100.0) / 100.0;
    }
}
