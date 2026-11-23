package com.github.orenaidaucac.royItems.voucher;

import java.util.Objects;

/**
 * Configuration data model for EcoEnchants random book voucher rewards.
 * Supports filtering by Rarity ID or Type ID, along with minimum and maximum level bounds.
 */
public class EcoEnchantReward {

    private final String rarity; // May be null or empty
    private final String type;   // May be null or empty
    private final int minLevel;  // Defaults to 1
    private final int maxLevel;  // Defaults to Integer.MAX_VALUE

    public EcoEnchantReward(String rarity, String type, int minLevel, int maxLevel) {
        this.rarity = (rarity != null && !rarity.isBlank()) ? rarity.trim() : null;
        this.type = (type != null && !type.isBlank()) ? type.trim() : null;
        int rawMin = minLevel;
        int rawMax = maxLevel <= 0 ? Integer.MAX_VALUE : maxLevel;
        if (rawMax < rawMin) {
            int tmp = rawMin;
            rawMin = rawMax;
            rawMax = tmp;
        }
        this.minLevel = Math.max(1, rawMin);
        this.maxLevel = Math.max(this.minLevel, rawMax);
    }

    public boolean hasFilter() {
        return (rarity != null && !rarity.isBlank()) || (type != null && !type.isBlank());
    }

    public String getRarity() {
        return rarity;
    }

    public String getType() {
        return type;
    }

    public int getMinLevel() {
        return minLevel;
    }

    public int getMaxLevel() {
        return maxLevel;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        EcoEnchantReward that = (EcoEnchantReward) o;
        return minLevel == that.minLevel &&
                maxLevel == that.maxLevel &&
                Objects.equals(rarity, that.rarity) &&
                Objects.equals(type, that.type);
    }

    @Override
    public int hashCode() {
        return Objects.hash(rarity, type, minLevel, maxLevel);
    }

    @Override
    public String toString() {
        return "EcoEnchantReward{" +
                "rarity='" + rarity + '\'' +
                ", type='" + type + '\'' +
                ", minLevel=" + minLevel +
                ", maxLevel=" + maxLevel +
                '}';
    }
}
