package com.github.orenaidaucac.royItems.stat;

import org.bukkit.Bukkit;
import org.bukkit.NamespacedKey;
import org.bukkit.Registry;
import org.bukkit.attribute.Attribute;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.permissions.PermissionAttachmentInfo;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Logger;

/**
 * High-performance Permission Stats Engine.
 * Dynamically resolves player permissions in the formats:
 * - Flat value:       royitems.player.stats.<stat>.<value>    (e.g. .attack-damage.10, .defense.15)
 * - Percentage value: royitems.player.stats.<stat>.<value>%   (e.g. .crit-rate.15%, .movement-speed.10%)
 *
 * Supports all 17 RoyItems StatTypes and modern Minecraft Vanilla Attributes.
 * Distinguishes flat and percentage modifiers, resolves multi-permission conflicts (HIGHEST or SUM),
 * and caches parsed player permissions for O(1) attribute and combat evaluation.
 */
public class PermissionStatManager {

    public enum ConflictResolution {
        HIGHEST,
        SUM
    }

    public static class ParsedNode {
        private final String rawStatKey;
        private final StatType statType;
        private final Attribute attribute;
        private final double value;
        private final boolean isPercent;

        public ParsedNode(String rawStatKey, StatType statType, Attribute attribute, double value, boolean isPercent) {
            this.rawStatKey = rawStatKey;
            this.statType = statType;
            this.attribute = attribute;
            this.value = value;
            this.isPercent = isPercent;
        }

        public String getRawStatKey() { return rawStatKey; }
        public StatType getStatType() { return statType; }
        public Attribute getAttribute() { return attribute; }
        public double getValue() { return value; }
        public boolean isPercent() { return isPercent; }
    }

    public static class ParsedPermissionStats {
        private final Map<StatType, StatModifier> statModifiers = new EnumMap<>(StatType.class);
        private final Map<Attribute, StatModifier> attributeModifiers = new HashMap<>();

        public Map<StatType, StatModifier> getStatModifiers() {
            return statModifiers;
        }

        public Map<Attribute, StatModifier> getAttributeModifiers() {
            return attributeModifiers;
        }

        public boolean isEmpty() {
            return statModifiers.isEmpty() && attributeModifiers.isEmpty();
        }
    }

    public static final String PERMISSION_PREFIX = "royitems.player.stats.";

    private boolean enabled = true;
    private ConflictResolution conflictResolution = ConflictResolution.HIGHEST;
    private final Map<String, Double> statCaps = new HashMap<>();
    private final Map<UUID, ParsedPermissionStats> playerCache = new ConcurrentHashMap<>();
    private Logger logger;

    public PermissionStatManager() {
        this(null);
    }

    public PermissionStatManager(Logger logger) {
        this.logger = logger;
    }

    public void setLogger(Logger logger) {
        this.logger = logger;
    }

    /**
     * Loads settings and stat caps from config.yml.
     */
    public void loadConfig(FileConfiguration config) {
        if (config == null) return;

        this.enabled = config.getBoolean("permission-stats.enabled", true);

        String modeStr = config.getString("permission-stats.conflict-resolution", "HIGHEST").trim().toUpperCase();
        try {
            this.conflictResolution = ConflictResolution.valueOf(modeStr);
        } catch (IllegalArgumentException e) {
            this.conflictResolution = ConflictResolution.HIGHEST;
            if (logger != null) {
                logger.warning("[RoyItems] Unknown conflict-resolution '" + modeStr + "', defaulting to HIGHEST.");
            }
        }

        this.statCaps.clear();
        ConfigurationSection capsSection = config.getConfigurationSection("permission-stats.caps");
        if (capsSection != null) {
            for (String key : capsSection.getKeys(false)) {
                double cap = capsSection.getDouble(key, 0.0);
                if (cap > 0) {
                    this.statCaps.put(key.toLowerCase().replace('_', '-'), cap);
                }
            }
        }

        // Invalidate all cached player permissions on configuration reload
        playerCache.clear();
    }

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public ConflictResolution getConflictResolution() {
        return conflictResolution;
    }

    public void setConflictResolution(ConflictResolution conflictResolution) {
        this.conflictResolution = conflictResolution;
    }

    public Map<String, Double> getStatCaps() {
        return statCaps;
    }

    public void setStatCap(String statKey, double cap) {
        if (statKey != null) {
            statCaps.put(statKey.toLowerCase().replace('_', '-'), cap);
        }
    }

    /**
     * Invalidates a player's cached permission stats (e.g. on group/permission change or quit).
     */
    public void invalidate(UUID uuid) {
        if (uuid != null) {
            playerCache.remove(uuid);
        }
    }

    /**
     * Clears all cached permission stats across all players.
     */
    public void clearCache() {
        playerCache.clear();
    }

    /**
     * Retrieves or calculates the parsed permission stats for an online player.
     */
    public ParsedPermissionStats getOrCompute(Player player) {
        if (player == null || !enabled) {
            return new ParsedPermissionStats();
        }
        return playerCache.computeIfAbsent(player.getUniqueId(), uuid -> computePlayerPermissions(player));
    }

    /**
     * Applies the player's permission bonuses directly into the PlayerStatCache.
     */
    public void applyToCache(Player player, PlayerStatCache targetCache) {
        if (!enabled || player == null || targetCache == null) return;

        ParsedPermissionStats parsed = getOrCompute(player);
        if (parsed == null || parsed.isEmpty()) return;

        for (Map.Entry<StatType, StatModifier> entry : parsed.getStatModifiers().entrySet()) {
            targetCache.addStat(entry.getKey(), entry.getValue());
        }

        for (Map.Entry<Attribute, StatModifier> entry : parsed.getAttributeModifiers().entrySet()) {
            targetCache.addVanillaAttribute(entry.getKey(), entry.getValue());
        }
    }

    /**
     * Scans and compiles all effective permission nodes matching royitems.player.stats.<stat>.<value>[%]
     */
    public ParsedPermissionStats computePlayerPermissions(Player player) {
        ParsedPermissionStats result = new ParsedPermissionStats();
        if (player == null) return result;

        Map<StatType, List<Double>> flatStatMap = new EnumMap<>(StatType.class);
        Map<StatType, List<Double>> percentStatMap = new EnumMap<>(StatType.class);

        Map<Attribute, List<Double>> flatAttrMap = new HashMap<>();
        Map<Attribute, List<Double>> percentAttrMap = new HashMap<>();

        for (PermissionAttachmentInfo info : player.getEffectivePermissions()) {
            if (info == null || !info.getValue()) continue; // Skip negated permissions

            String perm = info.getPermission();
            if (perm == null) continue;

            ParsedNode node = parsePermissionNode(perm);
            if (node == null) continue;

            if (node.getStatType() != null) {
                StatType st = node.getStatType();
                if (node.isPercent()) {
                    percentStatMap.computeIfAbsent(st, k -> new ArrayList<>()).add(node.getValue());
                } else {
                    flatStatMap.computeIfAbsent(st, k -> new ArrayList<>()).add(node.getValue());
                }
            } else if (node.getAttribute() != null) {
                Attribute attr = node.getAttribute();
                if (node.isPercent()) {
                    percentAttrMap.computeIfAbsent(attr, k -> new ArrayList<>()).add(node.getValue());
                } else {
                    flatAttrMap.computeIfAbsent(attr, k -> new ArrayList<>()).add(node.getValue());
                }
            }
        }

        // Resolve StatType modifiers
        Set<StatType> allStatTypes = new HashSet<>();
        allStatTypes.addAll(flatStatMap.keySet());
        allStatTypes.addAll(percentStatMap.keySet());

        for (StatType st : allStatTypes) {
            double resolvedFlat = resolveValues(flatStatMap.get(st), false, st.getKey());
            double resolvedPercent = resolveValues(percentStatMap.get(st), true, st.getKey());

            if (Math.abs(resolvedFlat) > 0.0001 || Math.abs(resolvedPercent) > 0.0001) {
                result.getStatModifiers().put(st, new StatModifier(resolvedFlat, resolvedPercent));
            }
        }

        // Resolve Vanilla Attribute modifiers
        Set<Attribute> allAttrs = new HashSet<>();
        allAttrs.addAll(flatAttrMap.keySet());
        allAttrs.addAll(percentAttrMap.keySet());

        for (Attribute attr : allAttrs) {
            String attrKey = attr.getKey().getKey();
            double resolvedFlat = resolveValues(flatAttrMap.get(attr), false, attrKey);
            double resolvedPercent = resolveValues(percentAttrMap.get(attr), true, attrKey);

            if (Math.abs(resolvedFlat) > 0.0001 || Math.abs(resolvedPercent) > 0.0001) {
                result.getAttributeModifiers().put(attr, new StatModifier(resolvedFlat, resolvedPercent));
            }
        }

        return result;
    }

    private double resolveValues(List<Double> values, boolean isPercent, String statKey) {
        if (values == null || values.isEmpty()) return 0.0;

        double result;
        if (conflictResolution == ConflictResolution.HIGHEST) {
            // Find highest value
            result = values.stream().max(Double::compareTo).orElse(0.0);
        } else {
            // SUM
            result = values.stream().mapToDouble(Double::doubleValue).sum();
        }

        // Apply stat caps if configured
        if (statKey != null) {
            Double cap = statCaps.get(statKey.toLowerCase().replace('_', '-'));
            if (cap != null && cap > 0) {
                if (!isPercent && result > cap) {
                    result = cap;
                }
            }
        }

        return result;
    }

    /**
     * Parses an individual permission node into a ParsedNode structure.
     * Examples:
     * - royitems.player.stats.attack-damage.10      -> Flat 10.0
     * - royitems.player.stats.crit-rate.15%        -> Percent 0.15
     * - royitems.player.stats.movement-speed.0.05  -> Flat 0.05
     * - royitems.player.stats.generic.scale.1.2    -> Flat 1.2 on Attribute
     */
    public static ParsedNode parsePermissionNode(String permission) {
        if (permission == null) return null;
        String trimmed = permission.trim();
        String lower = trimmed.toLowerCase();

        if (!lower.startsWith(PERMISSION_PREFIX)) {
            return null;
        }

        String payload = trimmed.substring(PERMISSION_PREFIX.length()).trim();
        if (payload.isEmpty()) return null;

        boolean isPercent = false;
        if (payload.endsWith("%")) {
            isPercent = true;
            payload = payload.substring(0, payload.length() - 1).trim();
        }

        if (payload.isEmpty()) return null;

        // Split payload into <stat> and <value> from right to left
        int sepIndex = -1;
        int decimalCount = 0;

        for (int i = payload.length() - 1; i >= 0; i--) {
            char c = payload.charAt(i);
            if (Character.isDigit(c)) {
                continue;
            } else if (c == '.') {
                if (decimalCount == 0 && i < payload.length() - 1 && i > 0 && Character.isDigit(payload.charAt(i + 1))) {
                    char prev = payload.charAt(i - 1);
                    if (Character.isDigit(prev) || prev == '-' || prev == '+') {
                        decimalCount++;
                        continue;
                    }
                }
                sepIndex = i;
                break;
            } else if (c == '-' || c == '+') {
                if (i > 0 && payload.charAt(i - 1) == '.') {
                    sepIndex = i - 1;
                    break;
                }
            } else {
                break;
            }
        }

        if (sepIndex <= 0 || sepIndex >= payload.length() - 1) {
            return null;
        }

        String statPart = payload.substring(0, sepIndex).trim();
        String valuePart = payload.substring(sepIndex + 1).trim();

        double rawVal;
        try {
            rawVal = Double.parseDouble(valuePart);
        } catch (NumberFormatException e) {
            return null;
        }

        // Percentage values are converted to fraction multiplier (e.g. 15% -> 0.15)
        double finalVal = isPercent ? (rawVal / 100.0) : rawVal;

        // 1. Check RoyItems StatType lookup
        StatType statType = StatType.fromKey(statPart);

        // 2. If not a custom StatType, check Vanilla Attribute
        Attribute attribute = null;
        if (statType == null) {
            attribute = resolveVanillaAttribute(statPart);
        }

        if (statType == null && attribute == null) {
            return null;
        }

        return new ParsedNode(statPart, statType, attribute, finalVal, isPercent);
    }

    /**
     * Resolves a Vanilla Attribute by name from modern Registry or legacy Enum.
     */
    public static Attribute resolveVanillaAttribute(String key) {
        if (key == null || key.isEmpty()) return null;
        String normalized = key.trim().toLowerCase().replace('-', '_');

        // 1. Try Registry if server is running
        try {
            if (Bukkit.getServer() != null) {
                Attribute attr = Registry.ATTRIBUTE.get(NamespacedKey.minecraft(normalized));
                if (attr != null) return attr;

                if (!normalized.startsWith("generic.") && !normalized.startsWith("player.")) {
                    attr = Registry.ATTRIBUTE.get(NamespacedKey.minecraft("generic." + normalized));
                    if (attr != null) return attr;

                    attr = Registry.ATTRIBUTE.get(NamespacedKey.minecraft("player." + normalized));
                    if (attr != null) return attr;
                }
            }
        } catch (Throwable ignored) {}

        // 2. Try Attribute enum valueOf
        try {
            String enumName = normalized.toUpperCase().replace('.', '_');
            return Attribute.valueOf(enumName);
        } catch (Throwable ignored) {}

        try {
            if (!normalized.startsWith("generic_") && !normalized.startsWith("player_")) {
                return Attribute.valueOf("GENERIC_" + normalized.toUpperCase().replace('.', '_'));
            }
        } catch (Throwable ignored) {}

        return null;
    }
}
