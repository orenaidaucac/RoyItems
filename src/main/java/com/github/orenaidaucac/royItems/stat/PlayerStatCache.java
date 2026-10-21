package com.github.orenaidaucac.royItems.stat;

import java.util.EnumMap;
import java.util.Map;

/**
 * Cached calculated stats for a player to provide O(1) combat lookups.
 */
public class PlayerStatCache {

    private final Map<StatType, StatModifier> stats = new EnumMap<>(StatType.class);

    public PlayerStatCache() {
        for (StatType type : StatType.values()) {
            stats.put(type, new StatModifier(0, 0));
        }
    }

    public Map<StatType, StatModifier> getStats() {
        return stats;
    }

    public void reset() {
        for (StatType type : StatType.values()) {
            stats.get(type).setFlat(0);
            stats.get(type).setPercent(0);
        }
    }

    public void addStat(StatType type, StatModifier modifier) {
        if (type == null || modifier == null) return;
        StatModifier current = stats.computeIfAbsent(type, k -> new StatModifier(0, 0));
        current.add(modifier);
    }

    public StatModifier getModifier(StatType type) {
        return stats.getOrDefault(type, new StatModifier(0, 0));
    }

    public double getFlat(StatType type) {
        return getModifier(type).getFlat();
    }

    public double getPercent(StatType type) {
        return getModifier(type).getPercent();
    }

    /**
     * Calculates total stat using formula: Total = (Base + Flat) * (1 + Percent).
     * If base and flat are 0 but a percentage is specified (e.g. crit-rate: 20%),
     * returns the direct percentage points (e.g. 20.0).
     */
    public double calculateTotal(StatType type, double baseValue) {
        StatModifier mod = getModifier(type);
        double flatSum = baseValue + mod.getFlat();
        if (Math.abs(flatSum) > 0.00001) {
            double total = flatSum * (1.0 + mod.getPercent());
            return Math.max(0, total);
        }
        if (Math.abs(mod.getPercent()) > 0.00001) {
            return Math.max(0, mod.getPercent() * 100.0);
        }
        return 0.0;
    }
}
