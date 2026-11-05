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
    MOVEMENT_SPEED("movement-speed", "👟 Movement Speed", "movement_speed"),
    MINE_RADIUS("mine-radius", "⛏ Mine Radius", null);

    private final String key;
    private final String defaultDisplayName;
    private final String vanillaAttributeKey;

    private static final Map<String, StatType> KEY_LOOKUP = new HashMap<>();

    static {
        for (StatType type : values()) {
            KEY_LOOKUP.put(type.key.toLowerCase(), type);
            KEY_LOOKUP.put(type.name().toLowerCase(), type);
            KEY_LOOKUP.put(type.key.toLowerCase().replace('-', '_'), type);
            if (type.vanillaAttributeKey != null) {
                KEY_LOOKUP.put("vanilla-" + type.key.toLowerCase(), type);
                KEY_LOOKUP.put("vanilla_" + type.key.toLowerCase().replace('-', '_'), type);
                KEY_LOOKUP.put(type.vanillaAttributeKey.toLowerCase(), type);
                KEY_LOOKUP.put("generic." + type.vanillaAttributeKey.toLowerCase(), type);
                KEY_LOOKUP.put("generic_" + type.vanillaAttributeKey.toLowerCase(), type);
            }
        }
        // Aliases
        KEY_LOOKUP.put("damage-redution", DAMAGE_REDUCTION);
        KEY_LOOKUP.put("critical-rate", CRIT_RATE);
        KEY_LOOKUP.put("critical_rate", CRIT_RATE);
        KEY_LOOKUP.put("critical-damage", CRIT_DAMAGE);
        KEY_LOOKUP.put("critical_damage", CRIT_DAMAGE);
        KEY_LOOKUP.put("hp", MAX_HEALTH);
        KEY_LOOKUP.put("health", MAX_HEALTH);
        KEY_LOOKUP.put("maxhealth", MAX_HEALTH);
        KEY_LOOKUP.put("speed", MOVEMENT_SPEED);
        KEY_LOOKUP.put("move-speed", MOVEMENT_SPEED);
        KEY_LOOKUP.put("movespeed", MOVEMENT_SPEED);
        KEY_LOOKUP.put("damage", ATTACK_DAMAGE);
        KEY_LOOKUP.put("dmg", ATTACK_DAMAGE);
        KEY_LOOKUP.put("def", DEFENSE);
        KEY_LOOKUP.put("crit", CRIT_RATE);
        KEY_LOOKUP.put("mineradius", MINE_RADIUS);
        KEY_LOOKUP.put("radius", MINE_RADIUS);
        KEY_LOOKUP.put("mine-radius", MINE_RADIUS);
        KEY_LOOKUP.put("mine_radius", MINE_RADIUS);
        KEY_LOOKUP.put("mine-area", MINE_RADIUS);
        KEY_LOOKUP.put("mine_area", MINE_RADIUS);
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
     * Resolves the official Vanilla Attribute dynamically from Bukkit's Registry or Enum.
     */
    public Attribute getVanillaAttribute() {
        try {
            return Attribute.valueOf(name());
        } catch (Throwable ignored) {}

        if (vanillaAttributeKey == null) return null;
        try {
            if (org.bukkit.Bukkit.getServer() == null) return null;
            Attribute attr = Registry.ATTRIBUTE.get(NamespacedKey.minecraft(vanillaAttributeKey));
            if (attr != null) return attr;
            attr = Registry.ATTRIBUTE.get(NamespacedKey.minecraft("generic." + vanillaAttributeKey));
            if (attr != null) return attr;
            attr = Registry.ATTRIBUTE.get(NamespacedKey.minecraft("player." + vanillaAttributeKey));
            return attr;
        } catch (Throwable t) {
            return null;
        }
    }

    public static StatType fromKey(String key) {
        if (key == null) return null;
        String normalized = key.trim().toLowerCase().replace('_', '-');
        return KEY_LOOKUP.get(normalized);
    }

    public static int getMineRadiusBlocks(double statValue) {
        int lvl = (int) Math.round(statValue);
        if (lvl <= 0) return 0;
        return lvl;
    }

    public static int getMineAreaSize(double statValue) {
        int r = getMineRadiusBlocks(statValue);
        return Math.max(1, 2 * r + 1);
    }

    public static String formatMineArea(double statValue) {
        int size = getMineAreaSize(statValue);
        return size + "×" + size;
    }
}
