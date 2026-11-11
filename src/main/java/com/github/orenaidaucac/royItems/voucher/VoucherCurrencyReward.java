package com.github.orenaidaucac.royItems.voucher;

import java.util.concurrent.ThreadLocalRandom;

/**
 * Represents a currency reward configuration for a VOUCHER item,
 * supporting both fixed amounts and random min-max ranges (eligible for Gacha animation).
 */
public class VoucherCurrencyReward {

    private String type;
    private Double fixedValue = null;
    private Double min = null;
    private Double max = null;

    public VoucherCurrencyReward(String type) {
        this.type = type != null ? type.trim() : "VAULT";
    }

    public VoucherCurrencyReward(String type, double fixedValue) {
        this.type = type != null ? type.trim() : "VAULT";
        this.fixedValue = fixedValue;
    }

    public VoucherCurrencyReward(String type, double min, double max) {
        this.type = type != null ? type.trim() : "VAULT";
        setRange(min, max);
    }

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type != null ? type.trim() : "VAULT";
    }

    public Double getFixedValue() {
        return fixedValue;
    }

    public void setFixedValue(Double fixedValue) {
        this.fixedValue = fixedValue;
        this.min = null;
        this.max = null;
    }

    public Double getMin() {
        return min;
    }

    public Double getMax() {
        return max;
    }

    public void setRange(double min, double max) {
        if (min <= max) {
            this.min = min;
            this.max = max;
        } else {
            this.min = max;
            this.max = min;
        }
        this.fixedValue = null;
    }

    public boolean isRandom() {
        return min != null && max != null && max >= min;
    }

    public boolean isValid() {
        if (type == null || type.isBlank()) return false;
        if (isRandom()) {
            return min >= 0 && max > 0;
        }
        return fixedValue != null && fixedValue > 0;
    }

    /**
     * Resolves the actual granted reward amount.
     * For random range, draws a uniform random value in [min, max].
     */
    public double calculateReward() {
        if (isRandom()) {
            if (min.equals(max)) {
                return min;
            }
            return ThreadLocalRandom.current().nextDouble(min, max);
        }
        return fixedValue != null ? fixedValue : 0.0;
    }
}
