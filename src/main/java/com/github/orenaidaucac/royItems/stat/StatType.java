package com.github.orenaidaucac.royItems.stat;

import org.bukkit.NamespacedKey;
import org.bukkit.Registry;
import org.bukkit.attribute.Attribute;

import java.util.HashMap;
import java.util.Map;

/**
 * 16 Core Custom Stats for RoyItems with lazy Vanilla Attribute mapping and legacy key migration.
 * Compatible with Minecraft 1.21.4+ / 26.2+ modern Attribute registry.
 */
public enum StatType {
    ATTACK_DAMAGE("attack-damage", "⚔ Attack Damage", "attack_damage"),
    ARROW_DAMAGE("arrow-damage", "🏹 Arrow Damage", null),
    MAX_HEALTH("max-health", "❤ Max Health", "max_health"),
    ATTACK_SPEED("attack-speed", "⚡ Attack Speed", "attack_speed"),
    CRIT_RATE("crit-rate", "🎯 Critical Rate", null),
    CRIT_DAMAGE("crit-damage", "💥 Critical Damage", null),
    DEFENSE("defense", "🛡 Defense", null),
    PVP_DAMAGE("pvp-damage", "⚔ PvP Damage", null),
    PVE_DAMAGE("pve-damage", "🗡 PvE Damage", null),
    PVP_DEFENSE("pvp-defense", "🛡 PvP Defense", null),
    PVE_DEFENSE("pve-defense", "🛡 PvE Defense", null),
    DAMAGE_REDUCTION("damage-reduction", "🔰 Damage Reduction", null),
    ARMOR("armor", "🛡 Armor", "armor"),
    ARMOR_TOUGHNESS("armor-toughness", "🛡 Armor Toughness", "armor_toughness"),
    KNOCKBACK_RESISTANCE("knockback-resistance", "🏋 Knockback Resistance", "knockback_resistance"),
    MOVEMENT_SPEED("movement-speed", "👟 Movement Speed", "movement_speed");

    private final String key;
    private final String defaultDisplayName;
    private final String vanillaAttributeKey;

    private static final Map<String, StatType> KEY_LOOKUP = new HashMap<>();

    static {
        for (StatType type : values()) {
            KEY_LOOKUP.put(type.key.toLowerCase(), type);
        }
        // Legacy alias migration: damage-redution -> DAMAGE_REDUCTION
        KEY_LOOKUP.put("damage-redution", DAMAGE_REDUCTION);
    }

    StatType(String key, String defaultDisplayName, String vanillaAttributeKey) {
        this.key = key;
        this.defaultDisplayName = defaultDisplayName;
        this.vanillaAttributeKey = vanillaAttributeKey;
    }

    public String getKey() {
        return key;
    }

    public String getDefaultDisplayName() {
        return defaultDisplayName;
    }

    public String getVanillaAttributeKey() {
        return vanillaAttributeKey;
    }

    public boolean hasVanillaAttribute() {
        return vanillaAttributeKey != null;
    }

    /**
     * Resolves the official Vanilla Attribute dynamically from Bukkit's Registry.
     */
    public Attribute getVanillaAttribute() {
        if (vanillaAttributeKey == null) return null;
        try {
            return Registry.ATTRIBUTE.get(NamespacedKey.minecraft(vanillaAttributeKey));
        } catch (Throwable t) {
            return null;
        }
    }

    public static StatType fromKey(String key) {
        if (key == null) return null;
        String normalized = key.trim().toLowerCase().replace('_', '-');
        return KEY_LOOKUP.get(normalized);
    }
}
