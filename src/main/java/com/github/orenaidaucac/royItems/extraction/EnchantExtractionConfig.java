package com.github.orenaidaucac.royItems.extraction;

import com.github.orenaidaucac.royItems.integration.EcoEnchantsHook;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.enchantments.Enchantment;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

/**
 * Configuration model for Enchantment Extraction on CONSUMABLE items.
 * Configured per item definition under the 'remove-enchants' section:
 *
 * remove-enchants:
 *   enabled: false
 *   type: BOTH # VANILLA_ENCHANTS, ECOENCHANTS, BOTH
 *   ecoenchants:
 *     rarity: []
 *     type: []
 *   chance: 100 # 0 - 100 (%)
 */
public class EnchantExtractionConfig {

    public enum ExtractionType {
        VANILLA_ENCHANTS,
        ECOENCHANTS,
        BOTH;

        public static ExtractionType fromString(String str) {
            if (str == null || str.isBlank()) return BOTH;
            String normalized = str.trim().toUpperCase(Locale.ROOT).replace('-', '_');
            try {
                return ExtractionType.valueOf(normalized);
            } catch (Exception e) {
                if (normalized.contains("VANILLA")) return VANILLA_ENCHANTS;
                if (normalized.contains("ECO")) return ECOENCHANTS;
                return BOTH;
            }
        }
    }

    private boolean enabled = false;
    private ExtractionType type = ExtractionType.BOTH;
    private List<String> itemWhitelist = new ArrayList<>();
    private List<String> ecoRarities = new ArrayList<>();
    private List<String> ecoTypes = new ArrayList<>();
    private double chance = 100.0;

    public EnchantExtractionConfig() {}

    public EnchantExtractionConfig(boolean enabled, ExtractionType type, List<String> itemWhitelist,
                                   List<String> ecoRarities, List<String> ecoTypes, double chance) {
        this.enabled = enabled;
        this.type = type != null ? type : ExtractionType.BOTH;
        if (itemWhitelist != null) setItemWhitelist(itemWhitelist);
        if (ecoRarities != null) this.ecoRarities.addAll(ecoRarities);
        if (ecoTypes != null) this.ecoTypes.addAll(ecoTypes);
        this.chance = Math.max(0.0, Math.min(100.0, chance));
    }

    public EnchantExtractionConfig(boolean enabled, ExtractionType type, List<String> ecoRarities, List<String> ecoTypes, double chance) {
        this(enabled, type, Collections.emptyList(), ecoRarities, ecoTypes, chance);
    }

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public ExtractionType getType() {
        return type;
    }

    public void setType(ExtractionType type) {
        this.type = type != null ? type : ExtractionType.BOTH;
    }

    public List<String> getItemWhitelist() {
        return Collections.unmodifiableList(itemWhitelist);
    }

    public void setItemWhitelist(List<String> itemWhitelist) {
        this.itemWhitelist = new ArrayList<>();
        if (itemWhitelist != null) {
            for (String item : itemWhitelist) {
                if (item != null && !item.isBlank()) {
                    this.itemWhitelist.add(item.trim());
                }
            }
        }
    }

    public List<String> getEcoRarities() {
        return Collections.unmodifiableList(ecoRarities);
    }

    public void setEcoRarities(List<String> ecoRarities) {
        this.ecoRarities = new ArrayList<>();
        if (ecoRarities != null) {
            for (String r : ecoRarities) {
                if (r != null && !r.isBlank()) this.ecoRarities.add(r.trim());
            }
        }
    }

    public List<String> getEcoTypes() {
        return Collections.unmodifiableList(ecoTypes);
    }

    public void setEcoTypes(List<String> ecoTypes) {
        this.ecoTypes = new ArrayList<>();
        if (ecoTypes != null) {
            for (String t : ecoTypes) {
                if (t != null && !t.isBlank()) this.ecoTypes.add(t.trim());
            }
        }
    }

    public double getChance() {
        return chance;
    }

    public void setChance(double chance) {
        this.chance = Math.max(0.0, Math.min(100.0, chance));
    }

    /**
     * Determines whether a given enchantment on a target item is eligible for extraction
     * based on this configuration's type, rarity, and type filters.
     */
    public boolean isEnchantmentEligible(Enchantment ench, EcoEnchantsHook ecoHook) {
        if (ench == null) return false;

        boolean isEco = (ecoHook != null && ecoHook.isEcoEnchant(ench));

        // 1. Check primary category filter
        if (type == ExtractionType.VANILLA_ENCHANTS && isEco) {
            return false;
        }
        if (type == ExtractionType.ECOENCHANTS && !isEco) {
            return false;
        }

        // 2. If it's an EcoEnchant, apply EcoEnchants Rarity and Type filters
        if (isEco && ecoHook != null) {
            // Rarity filter
            if (!ecoRarities.isEmpty()) {
                String rId = ecoHook.getRarityId(ench);
                String rDisplay = ecoHook.getRarityDisplayName(ench);
                String cleanRDisplay = EcoEnchantsHook.stripColor(rDisplay);

                boolean matchedRarity = false;
                for (String allowed : ecoRarities) {
                    if (allowed == null || allowed.isBlank()) continue;
                    String cleanAllowed = EcoEnchantsHook.stripColor(allowed).trim();
                    if ((rId != null && rId.equalsIgnoreCase(cleanAllowed))
                            || (rDisplay != null && rDisplay.equalsIgnoreCase(allowed.trim()))
                            || (cleanRDisplay != null && cleanRDisplay.equalsIgnoreCase(cleanAllowed))) {
                        matchedRarity = true;
                        break;
                    }
                }
                if (!matchedRarity) {
                    return false;
                }
            }

            // Type filter
            if (!ecoTypes.isEmpty()) {
                String tId = ecoHook.getTypeId(ench);
                String cleanTId = EcoEnchantsHook.stripColor(tId);

                boolean matchedType = false;
                for (String allowed : ecoTypes) {
                    if (allowed == null || allowed.isBlank()) continue;
                    String cleanAllowed = EcoEnchantsHook.stripColor(allowed).trim();
                    if ((tId != null && tId.equalsIgnoreCase(cleanAllowed))
                            || (cleanTId != null && cleanTId.equalsIgnoreCase(cleanAllowed))) {
                        matchedType = true;
                        break;
                    }
                }
                if (!matchedType) {
                    return false;
                }
            }
        }

        // Vanilla enchantments are not affected by Eco filters when type is BOTH or VANILLA_ENCHANTS
        return true;
    }

    /**
     * Determines whether a given material and target item definition are whitelisted for extraction.
     * Backwards-compatible: If whitelist is empty or not configured, all items are allowed.
     */
    public boolean isItemWhitelisted(org.bukkit.Material material, com.github.orenaidaucac.royItems.item.ItemDefinition targetDef) {
        if (itemWhitelist == null || itemWhitelist.isEmpty()) {
            return true; // Empty whitelist allows all items (backwards compatibility)
        }
        if (material == null || material == org.bukkit.Material.AIR || material.name().contains("AIR")) {
            return false;
        }

        String matName = material.name();
        String typeName = (targetDef != null && targetDef.getType() != null) ? targetDef.getType().name() : null;

        for (String rawEntry : itemWhitelist) {
            if (rawEntry == null || rawEntry.isBlank()) continue;
            String entry = rawEntry.trim().toUpperCase(Locale.ROOT);

            // 1. Direct ItemType match if target is a RoyItem
            if (typeName != null && typeName.equalsIgnoreCase(entry)) {
                return true;
            }

            // 2. Exact Material match
            if (matName.equalsIgnoreCase(entry)) {
                return true;
            }

            // 3. Suffix match (e.g., SWORD -> DIAMOND_SWORD, AXE -> NETHERITE_AXE)
            if (matName.endsWith("_" + entry)) {
                return true;
            }

            // 4. Common group aliases
            if (matchesGroupAlias(entry, matName)) {
                return true;
            }
        }

        return false;
    }

    /**
     * Determines whether a given target item is whitelisted for extraction based on configured item-whitelist.
     * Backwards-compatible: If whitelist is empty or not configured, all items are allowed.
     */
    public boolean isItemWhitelisted(org.bukkit.inventory.ItemStack item, com.github.orenaidaucac.royItems.item.ItemDefinition targetDef) {
        if (itemWhitelist == null || itemWhitelist.isEmpty()) {
            return true; // Empty whitelist allows all items (backwards compatibility)
        }
        if (item == null) {
            return false;
        }
        try {
            return isItemWhitelisted(item.getType(), targetDef);
        } catch (Throwable t) {
            return targetDef != null && targetDef.getType() != null
                    && itemWhitelist.stream().anyMatch(e -> e.equalsIgnoreCase(targetDef.getType().name()));
        }
    }

    private boolean matchesGroupAlias(String group, String matName) {
        switch (group) {
            case "SWORD":
                return matName.endsWith("_SWORD");
            case "AXE":
                return matName.endsWith("_AXE");
            case "PICKAXE":
                return matName.endsWith("_PICKAXE");
            case "SHOVEL":
            case "SPADE":
                return matName.endsWith("_SHOVEL");
            case "HOE":
                return matName.endsWith("_HOE");
            case "HELMET":
            case "HEAD":
                return matName.endsWith("_HELMET") || matName.equals("TURTLE_HELMET");
            case "CHESTPLATE":
            case "CHEST":
                return matName.endsWith("_CHESTPLATE") || matName.equals("ELYTRA");
            case "LEGGINGS":
            case "LEGS":
                return matName.endsWith("_LEGGINGS");
            case "BOOTS":
            case "FEET":
                return matName.endsWith("_BOOTS");
            case "ARMOR":
                return matName.endsWith("_HELMET") || matName.endsWith("_CHESTPLATE")
                        || matName.endsWith("_LEGGINGS") || matName.endsWith("_BOOTS")
                        || matName.equals("ELYTRA") || matName.equals("TURTLE_HELMET")
                        || matName.contains("WOLF_ARMOR");
            case "WEAPON":
                return matName.endsWith("_SWORD") || matName.endsWith("_AXE")
                        || matName.equals("BOW") || matName.equals("CROSSBOW")
                        || matName.equals("TRIDENT") || matName.equals("MACE");
            case "TOOL":
                return matName.endsWith("_PICKAXE") || matName.endsWith("_AXE")
                        || matName.endsWith("_SHOVEL") || matName.endsWith("_HOE")
                        || matName.equals("SHEARS") || matName.equals("FISHING_ROD")
                        || matName.equals("FLINT_AND_STEEL");
            case "BOW":
                return matName.equals("BOW") || matName.equals("CROSSBOW");
            case "CROSSBOW":
                return matName.equals("CROSSBOW");
            case "TRIDENT":
                return matName.equals("TRIDENT");
            case "MACE":
                return matName.equals("MACE");
            case "SHIELD":
                return matName.equals("SHIELD");
            case "FISHING_ROD":
                return matName.equals("FISHING_ROD");
            case "SHEARS":
                return matName.equals("SHEARS");
            case "FLINT_AND_STEEL":
                return matName.equals("FLINT_AND_STEEL");
            case "BOOK":
            case "ENCHANTED_BOOK":
                return matName.equals("BOOK") || matName.equals("ENCHANTED_BOOK");
            default:
                return false;
        }
    }

    /**
     * Parses an EnchantExtractionConfig from a YAML ConfigurationSection.
     */
    public static EnchantExtractionConfig fromConfig(ConfigurationSection sec) {
        if (sec == null) return new EnchantExtractionConfig();

        EnchantExtractionConfig config = new EnchantExtractionConfig();
        config.setEnabled(sec.getBoolean("enabled", false));
        config.setType(ExtractionType.fromString(sec.getString("type", "BOTH")));
        config.setChance(sec.getDouble("chance", 100.0));

        if (sec.contains("item-whitelist")) {
            if (sec.isList("item-whitelist")) {
                config.setItemWhitelist(sec.getStringList("item-whitelist"));
            } else if (sec.isString("item-whitelist")) {
                config.setItemWhitelist(List.of(sec.getString("item-whitelist")));
            }
        }

        if (sec.contains("ecoenchants")) {
            ConfigurationSection ecoSec = sec.getConfigurationSection("ecoenchants");
            if (ecoSec != null) {
                // Rarity
                if (ecoSec.isList("rarity")) {
                    config.setEcoRarities(ecoSec.getStringList("rarity"));
                } else if (ecoSec.isString("rarity")) {
                    config.setEcoRarities(List.of(ecoSec.getString("rarity")));
                }
                // Type
                if (ecoSec.isList("type")) {
                    config.setEcoTypes(ecoSec.getStringList("type"));
                } else if (ecoSec.isString("type")) {
                    config.setEcoTypes(List.of(ecoSec.getString("type")));
                }
            }
        }

        return config;
    }
}
