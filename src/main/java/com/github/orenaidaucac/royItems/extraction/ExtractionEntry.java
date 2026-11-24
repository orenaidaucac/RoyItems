package com.github.orenaidaucac.royItems.extraction;

import com.github.orenaidaucac.royItems.lore.DynamicLoreEngine;
import org.bukkit.enchantments.Enchantment;

/**
 * Data model representing an eligible enchantment candidate on an item undergoing extraction.
 */
public class ExtractionEntry {

    private final Enchantment enchantment;
    private final int level;
    private final boolean eco;
    private final String id;
    private final String name;
    private final String romanLevel;
    private final String rarityDisplayName;
    private final String typeName;
    private final double chance;
    private final boolean downgrade;
    private final int targetLevel;
    private final int bookLevel;

    public ExtractionEntry(Enchantment enchantment, int level, boolean eco, String id, String name,
                           String romanLevel, String rarityDisplayName, String typeName, double chance) {
        this(enchantment, level, eco, id, name, romanLevel, rarityDisplayName, typeName, chance, false, 0, level);
    }

    public ExtractionEntry(Enchantment enchantment, int level, boolean eco, String id, String name,
                           String romanLevel, String rarityDisplayName, String typeName, double chance,
                           boolean downgrade, int targetLevel, int bookLevel) {
        this.enchantment = enchantment;
        this.level = level;
        this.eco = eco;
        this.id = id != null ? id : "";
        this.name = name != null ? name : this.id;
        this.romanLevel = romanLevel != null ? romanLevel : String.valueOf(level);
        this.rarityDisplayName = rarityDisplayName != null ? rarityDisplayName : "Vanilla";
        this.typeName = typeName != null ? typeName : (eco ? "EcoEnchants" : "Vanilla");
        this.chance = chance;
        this.downgrade = downgrade;
        this.targetLevel = Math.max(0, targetLevel);
        this.bookLevel = Math.max(1, bookLevel);
    }

    public Enchantment getEnchantment() {
        return enchantment;
    }

    public int getLevel() {
        return level;
    }

    public boolean isEco() {
        return eco;
    }

    public String getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public String getRomanLevel() {
        return romanLevel;
    }

    public String getRarityDisplayName() {
        return rarityDisplayName;
    }

    public String getTypeName() {
        return typeName;
    }

    public double getChance() {
        return chance;
    }

    public boolean isDowngrade() {
        return downgrade;
    }

    public int getTargetLevel() {
        return targetLevel;
    }

    public String getTargetRomanLevel() {
        return DynamicLoreEngine.toRoman(targetLevel);
    }

    public int getBookLevel() {
        return bookLevel;
    }

    public String getRomanBookLevel() {
        return DynamicLoreEngine.toRoman(bookLevel);
    }
}
