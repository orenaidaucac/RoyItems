package com.github.orenaidaucac.royItems.stat;

import java.util.Map;

/**
 * Represents a stat modifier holding both flat and percentage bonuses.
 */
public class StatModifier {
    private double flat = 0.0;
    private double percent = 0.0; // Stored as fraction (e.g. 0.15 for 15%)

    public StatModifier() {
    }

    public StatModifier(double flat, double percent) {
        this.flat = flat;
        this.percent = percent;
    }

    public double getFlat() {
        return flat;
    }

    public void setFlat(double flat) {
        this.flat = flat;
    }

    public double getPercent() {
        return percent;
    }

    public void setPercent(double percent) {
        this.percent = percent;
    }

    public boolean isEmpty() {
        return Math.abs(flat) < 0.0001 && Math.abs(percent) < 0.0001;
    }

    public void add(StatModifier other) {
        if (other != null) {
            this.flat += other.flat;
            this.percent += other.percent;
        }
    }

    public void add(double flatVal, double percentVal) {
        this.flat += flatVal;
        this.percent += percentVal;
    }

    /**
     * Parses an object from YAML into a StatModifier.
     * Can handle number (e.g. 15), string (e.g. "15%"), or map (e.g. {flat: 10, percent: 5%}).
     */
    public static StatModifier parse(Object raw) {
        if (raw == null) return new StatModifier(0, 0);

        if (raw instanceof Number num) {
            return new StatModifier(num.doubleValue(), 0.0);
        }

        if (raw instanceof String str) {
            str = str.trim();
            if (str.endsWith("%")) {
                try {
                    double val = Double.parseDouble(str.substring(0, str.length() - 1).trim());
                    return new StatModifier(0.0, val / 100.0);
                } catch (NumberFormatException e) {
                    return new StatModifier(0, 0);
                }
            } else {
                try {
                    double val = Double.parseDouble(str);
                    return new StatModifier(val, 0.0);
                } catch (NumberFormatException e) {
                    return new StatModifier(0, 0);
                }
            }
        }

        if (raw instanceof Map<?, ?> map) {
            double flatVal = 0.0;
            double percentVal = 0.0;
            if (map.containsKey("flat")) {
                flatVal = parseDouble(map.get("flat"));
            }
            if (map.containsKey("percent")) {
                Object pRaw = map.get("percent");
                if (pRaw instanceof String pStr && pStr.endsWith("%")) {
                    percentVal = parseDouble(pStr.substring(0, pStr.length() - 1)) / 100.0;
                } else {
                    percentVal = parseDouble(pRaw) / 100.0;
                }
            }
            return new StatModifier(flatVal, percentVal);
        }

        return new StatModifier(0, 0);
    }

    private static double parseDouble(Object val) {
        if (val instanceof Number n) return n.doubleValue();
        if (val != null) {
            try {
                return Double.parseDouble(val.toString().trim());
            } catch (NumberFormatException ignored) {}
        }
        return 0.0;
    }
}
