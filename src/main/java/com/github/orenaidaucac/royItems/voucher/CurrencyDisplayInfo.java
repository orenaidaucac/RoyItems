package com.github.orenaidaucac.royItems.voucher;

import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.util.Locale;

/**
 * Metadata and formatting details for displaying currencies to players in a friendly manner.
 * Prevents raw identifiers (e.g. EXCELLENTECONOMY_gems) from ever leaking into chat, titles, or lore.
 */
public class CurrencyDisplayInfo {

    private final String id;
    private final String displayName;
    private final String symbol;
    private final String formatPattern;
    private final DecimalFormat decimalFormat;

    public CurrencyDisplayInfo(String id, String displayName, String symbol, String formatPattern) {
        this.id = id != null ? id : "UNKNOWN";
        this.displayName = displayName != null && !displayName.isBlank() ? displayName : id;
        this.symbol = symbol != null ? symbol : "";
        this.formatPattern = (formatPattern != null && !formatPattern.isBlank()) ? formatPattern : "#,##0.##";

        DecimalFormat df;
        try {
            DecimalFormatSymbols symbols = new DecimalFormatSymbols(Locale.US);
            df = new DecimalFormat(this.formatPattern, symbols);
        } catch (Throwable e) {
            df = new DecimalFormat("#,##0.##", new DecimalFormatSymbols(Locale.US));
        }
        this.decimalFormat = df;
    }

    public String getId() {
        return id;
    }

    public String getDisplayName() {
        return displayName;
    }

    public String getSymbol() {
        return symbol;
    }

    public String getFormatPattern() {
        return formatPattern;
    }

    /**
     * Formats the given amount according to the configured pattern.
     * If the amount is an exact integer, formats without trailing decimals unless forced by pattern.
     */
    public String format(double amount) {
        try {
            return decimalFormat.format(amount);
        } catch (Throwable e) {
            if (amount == (long) amount) {
                return String.format(Locale.US, "%,d", (long) amount);
            }
            return String.format(Locale.US, "%,.2f", amount);
        }
    }
}
