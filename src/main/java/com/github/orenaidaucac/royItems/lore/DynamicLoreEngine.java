package com.github.orenaidaucac.royItems.lore;

import com.github.orenaidaucac.royItems.item.ItemDefinition;
import com.github.orenaidaucac.royItems.stat.StatModifier;
import com.github.orenaidaucac.royItems.stat.StatType;
import org.bukkit.NamespacedKey;
import org.bukkit.Registry;
import org.bukkit.attribute.Attribute;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.Plugin;

import java.io.File;
import java.text.DecimalFormat;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Dynamic Lore Formatting Engine supporting:
 * - Aggregate #stats# tag
 * - Independent per-stat placeholders (e.g. #attack-damage#, #defense#, #generic.attack_damage#)
 * - Automatic omission of non-existent or zero stats without dangling characters or blank lines
 * - Smart #bar# separator auto-pruning
 */
public class DynamicLoreEngine {

    private static final Pattern STAT_PLACEHOLDER_PATTERN = Pattern.compile("#([a-zA-Z0-9_.-]+)#");

    private final Plugin plugin;
    private final Map<String, LoreFormat> formats = new ConcurrentHashMap<>();
    private final DecimalFormat df = new DecimalFormat("#,##0.##");

    public DynamicLoreEngine(Plugin plugin) {
        this.plugin = plugin;
    }

    public void loadFormats() {
        formats.clear();
        File folder = new File(plugin.getDataFolder(), "lore-format");
        if (!folder.exists()) {
            folder.mkdirs();
        }

        // Always save and refresh the authoritative wiki file _example.yml
        saveWikiExampleFile();

        File[] files = folder.listFiles((dir, name) -> name.endsWith(".yml"));
        if (files == null || files.length == 0) {
            saveDefaultFormat("default.yml");
            saveDefaultFormat("weapon.yml");
            saveDefaultFormat("armor.yml");
            files = folder.listFiles((dir, name) -> name.endsWith(".yml"));
        }

        if (files != null) {
            for (File file : files) {
                String id = file.getName().replace(".yml", "").toLowerCase();
                // Reserved wiki file must never be loaded as an item lore format
                if (id.equalsIgnoreCase("_example")) {
                    continue;
                }
                FileConfiguration config = YamlConfiguration.loadConfiguration(file);
                LoreFormat format = LoreFormat.fromConfig(id, config);
                formats.put(id, format);
            }
        }
    }

    private void saveWikiExampleFile() {
        try {
            plugin.saveResource("lore-format/_example.yml", true);
        } catch (Exception e) {
            if (plugin != null && plugin.getLogger() != null) {
                plugin.getLogger().warning("Failed to refresh lore-format/_example.yml: " + e.getMessage());
            }
        }
    }

    private void saveDefaultFormat(String name) {
        try {
            File file = new File(plugin.getDataFolder(), "lore-format/" + name);
            if (!file.exists()) {
                file.getParentFile().mkdirs();
                plugin.saveResource("lore-format/" + name, false);
            }
        } catch (Exception ignored) {}
    }

    /**
     * Builds dynamic lore for an item according to its definition and active runtime data.
     */
    public List<String> buildLore(ItemDefinition def, List<String> slottedSockets, long expireTimestamp) {
        if (def == null) return new ArrayList<>();

        // If an item purposefully attempts to use the reserved wiki file, suppress its lore completely
        if (def.getLoreFormat() != null && (def.getLoreFormat().equalsIgnoreCase("_example") || def.getLoreFormat().equalsIgnoreCase("_example.yml"))) {
            return Collections.emptyList();
        }

        String formatId = def.getLoreFormat() != null ? def.getLoreFormat().toLowerCase() : "default";
        LoreFormat format = formats.getOrDefault(formatId, formats.get("default"));
        if (format == null) {
            format = new LoreFormat();
        }

        List<String> template = (format.getFormat() != null && !format.getFormat().isEmpty())
                ? new ArrayList<>(format.getFormat())
                : new ArrayList<>(def.getLore());

        List<String> socketLines = generateSocketLines(def, slottedSockets);
        List<String> reqLines = generateRequirementLines(def);

        List<String> processed = new ArrayList<>();
        String barString = format.getBar() != null ? format.getBar() : "&8&m------------------------";

        for (String rawLine : template) {
            if (rawLine == null) continue;
            String trimmed = rawLine.trim();

            if (trimmed.equalsIgnoreCase("#stats#")) {
                processed.addAll(generateStatLines(def, format));
            } else if (trimmed.equalsIgnoreCase("#sockets#")) {
                processed.addAll(socketLines);
            } else if (trimmed.equalsIgnoreCase("#requirements#")) {
                processed.addAll(reqLines);
            } else if (trimmed.equalsIgnoreCase("#description#") || trimmed.equalsIgnoreCase("#lore#")) {
                if (def.getLore() != null) {
                    processed.addAll(def.getLore());
                }
            } else if (trimmed.startsWith("#") && trimmed.endsWith("#") && isStatPlaceholder(trimmed)) {
                // Standalone stat placeholder: e.g. "#attack-damage#" or "#defense#"
                String key = trimmed.substring(1, trimmed.length() - 1);
                StatModifier mod = findStatModifier(def, key);
                if (mod != null && !mod.isEmpty()) {
                    String displayName = findStatDisplayName(key);
                    String statLine = format.formatStatLine(key, displayName, mod, df);
                    if (statLine != null && !statLine.isEmpty()) {
                        processed.add(statLine);
                    }
                }
                // If stat is null or empty, the entire line is skipped cleanly!
            } else {
                // Inline check: does this line contain any stat placeholders?
                List<String> statKeysInLine = extractStatPlaceholdersInLine(rawLine);
                if (!statKeysInLine.isEmpty()) {
                    // Check if ALL stat placeholders in this line are possessed by the item
                    boolean allStatsPresent = true;
                    for (String key : statKeysInLine) {
                        StatModifier mod = findStatModifier(def, key);
                        if (mod == null || mod.isEmpty()) {
                            allStatsPresent = false;
                            break;
                        }
                    }

                    if (!allStatsPresent) {
                        // The item is missing at least one stat on this line -> omit line cleanly
                        continue;
                    }

                    // Replace stat placeholders with their exact values
                    String line = rawLine;
                    for (String key : statKeysInLine) {
                        StatModifier mod = findStatModifier(def, key);
                        String val = format.formatStatValue(mod, df);
                        line = line.replace("#" + key + "#", val != null ? val : "");
                        line = line.replace("#" + key.toUpperCase() + "#", val != null ? val : "");
                        // Also replace #name# if present on this line
                        String dName = findStatDisplayName(key);
                        line = line.replace("#name#", dName != null ? dName : key);
                    }

                    processed.add(replaceGeneralPlaceholders(line, def, expireTimestamp));
                } else {
                    // Standard line
                    processed.add(replaceGeneralPlaceholders(rawLine, def, expireTimestamp));
                }
            }
        }

        // Clean up conditional #bar# separators
        return pruneBars(processed, barString);
    }

    private String replaceGeneralPlaceholders(String input, ItemDefinition def, long expireTimestamp) {
        if (input == null) return "";
        String s = input
                .replace("#type#", def.getType() != null ? def.getType().getDescription() : "")
                .replace("#tier#", def.getItemTier() != null ? def.getItemTier() : "")
                .replace("#set#", def.getItemSet() != null ? def.getItemSet() : "");

        if (expireTimestamp > 0) {
            long remainingSec = Math.max(0, (expireTimestamp - System.currentTimeMillis()) / 1000);
            s = s.replace("#expired#", formatDuration(remainingSec));
        } else {
            s = s.replace("#expired#", "");
        }

        if (def.getCooldown() > 0) {
            s = s.replace("#cooldown#", df.format(def.getCooldown()) + "s");
        } else {
            s = s.replace("#cooldown#", "");
        }

        return s;
    }

    private List<String> extractStatPlaceholdersInLine(String line) {
        List<String> list = new ArrayList<>();
        if (line == null) return list;
        Matcher m = STAT_PLACEHOLDER_PATTERN.matcher(line);
        while (m.find()) {
            String candidate = m.group(1);
            if (isStatKey(candidate)) {
                list.add(candidate);
            }
        }
        return list;
    }

    public boolean isStatPlaceholder(String token) {
        if (token == null || !token.startsWith("#") || !token.endsWith("#") || token.length() <= 2) {
            return false;
        }
        String clean = token.substring(1, token.length() - 1);
        return isStatKey(clean);
    }

    public boolean isStatKey(String key) {
        if (key == null || key.isEmpty()) return false;
        String normalized = key.toLowerCase().replace('_', '-');
        if (StatType.fromKey(normalized) != null) return true;
        return findVanillaAttribute(key) != null;
    }

    public StatModifier findStatModifier(ItemDefinition def, String key) {
        if (def == null || key == null) return null;
        String normalized = key.toLowerCase().replace('_', '-');

        // 1. Custom Stats
        StatType type = StatType.fromKey(normalized);
        if (type != null) {
            StatModifier mod = def.getStat(type);
            if (mod != null && !mod.isEmpty()) return mod;
        }

        // 2. Vanilla Attributes
        Attribute attr = findVanillaAttribute(key);
        if (attr != null && def.getVanillaAttributes() != null) {
            StatModifier mod = def.getVanillaAttributes().get(attr);
            if (mod != null && !mod.isEmpty()) return mod;
        }

        return null;
    }

    public String findStatDisplayName(String key) {
        if (key == null) return "";
        String normalized = key.toLowerCase().replace('_', '-');
        StatType type = StatType.fromKey(normalized);
        if (type != null) {
            return type.getDefaultDisplayName();
        }
        Attribute attr = findVanillaAttribute(key);
        if (attr != null) {
            return attr.getKey().getKey();
        }
        return key;
    }

    public static Attribute findVanillaAttribute(String key) {
        if (key == null || key.isEmpty()) return null;
        String normalized = key.toLowerCase().replace('-', '_');
        try {
            NamespacedKey nKey = normalized.contains(":") ? NamespacedKey.fromString(normalized) : NamespacedKey.minecraft(normalized);
            Attribute attr = Registry.ATTRIBUTE.get(nKey);
            if (attr != null) return attr;

            if (!normalized.startsWith("generic.") && !normalized.contains(":")) {
                attr = Registry.ATTRIBUTE.get(NamespacedKey.minecraft("generic." + normalized));
                if (attr != null) return attr;
            }
            if (!normalized.startsWith("player.") && !normalized.contains(":")) {
                attr = Registry.ATTRIBUTE.get(NamespacedKey.minecraft("player." + normalized));
                if (attr != null) return attr;
            }
        } catch (Throwable ignored) {}
        return null;
    }

    private List<String> generateStatLines(ItemDefinition def, LoreFormat format) {
        List<String> lines = new ArrayList<>();
        if (def == null) return lines;

        // Custom stats
        if (def.getStats() != null) {
            for (Map.Entry<StatType, StatModifier> entry : def.getStats().entrySet()) {
                StatType type = entry.getKey();
                StatModifier mod = entry.getValue();
                if (mod == null || mod.isEmpty()) continue;

                String line = format.formatStatLine(type.getKey(), type.getDefaultDisplayName(), mod, df);
                if (line != null && !line.isEmpty()) {
                    lines.add(line);
                }
            }
        }

        // Vanilla attributes
        if (def.getVanillaAttributes() != null) {
            for (Map.Entry<Attribute, StatModifier> entry : def.getVanillaAttributes().entrySet()) {
                Attribute attr = entry.getKey();
                StatModifier mod = entry.getValue();
                if (mod == null || mod.isEmpty()) continue;

                String line = format.formatStatLine(attr.getKey().getKey(), attr.getKey().getKey(), mod, df);
                if (line != null && !line.isEmpty()) {
                    lines.add(line);
                }
            }
        }

        return lines;
    }

    private List<String> generateSocketLines(ItemDefinition def, List<String> slottedSockets) {
        List<String> lines = new ArrayList<>();
        if (def.getGemSockets() == null || def.getGemSockets().isEmpty()) return lines;

        for (int i = 0; i < def.getGemSockets().size(); i++) {
            String socketType = def.getGemSockets().get(i);
            String slottedGem = (slottedSockets != null && i < slottedSockets.size()) ? slottedSockets.get(i) : null;
            if (slottedGem != null && !slottedGem.isEmpty()) {
                lines.add("&8[&a◆&8] &f" + slottedGem);
            } else {
                lines.add("&8[&7◇&8] &7Empty Socket (" + socketType + ")");
            }
        }
        return lines;
    }

    private List<String> generateRequirementLines(ItemDefinition def) {
        List<String> lines = new ArrayList<>();
        if (def.getLevelRequired() > 0) {
            lines.add("&cRequires Level: &f" + def.getLevelRequired());
        }
        if (def.getPermission() != null && !def.getPermission().isEmpty()) {
            lines.add("&cRequires Permission: &f" + def.getPermission());
        }
        if (def.getMmocoreClass() != null && !def.getMmocoreClass().isEmpty()) {
            lines.add("&cClass: &f" + def.getMmocoreClass() + (def.getMmocoreLevel() > 0 ? " (Lv." + def.getMmocoreLevel() + ")" : ""));
        }
        if (def.getAuraSkill() != null && !def.getAuraSkill().isEmpty()) {
            lines.add("&cAuraSkill: &f" + def.getAuraSkill() + " Lv." + def.getAuraSkillLevel());
        }
        return lines;
    }

    /**
     * Prunes #bar# separators:
     * - Does not show at top or bottom.
     * - Does not show consecutive bars.
     * - Removes #bar# if section above or below is completely empty.
     */
    public List<String> pruneBars(List<String> rawLines, String barDisplay) {
        List<String> filtered = new ArrayList<>();
        boolean previousWasBar = true; // Prevents leading bar

        for (int i = 0; i < rawLines.size(); i++) {
            String line = rawLines.get(i);
            if (line == null) continue;

            boolean isBar = line.equalsIgnoreCase("#bar#") || line.equals(barDisplay);

            if (isBar) {
                if (previousWasBar) {
                    continue;
                }
                boolean hasContentAhead = false;
                for (int j = i + 1; j < rawLines.size(); j++) {
                    String next = rawLines.get(j);
                    if (next != null && !next.equalsIgnoreCase("#bar#") && !next.equals(barDisplay) && !next.trim().isEmpty()) {
                        hasContentAhead = true;
                        break;
                    }
                }
                if (!hasContentAhead) {
                    continue; // Skip trailing bar
                }
                filtered.add(barDisplay);
                previousWasBar = true;
            } else {
                if (!line.trim().isEmpty()) {
                    previousWasBar = false;
                }
                filtered.add(line);
            }
        }

        // Final cleanup of trailing bar
        while (!filtered.isEmpty()) {
            String last = filtered.get(filtered.size() - 1);
            if (last.equals(barDisplay) || last.trim().isEmpty()) {
                filtered.remove(filtered.size() - 1);
            } else {
                break;
            }
        }

        return filtered;
    }

    public Map<String, LoreFormat> getFormats() {
        return Collections.unmodifiableMap(formats);
    }

    private String formatDuration(long seconds) {
        if (seconds <= 0) return "Expired";
        long days = seconds / 86400;
        long hours = (seconds % 86400) / 3600;
        long minutes = (seconds % 3600) / 60;
        long secs = seconds % 60;

        if (days > 0) return days + "d " + hours + "h";
        if (hours > 0) return hours + "h " + minutes + "m";
        if (minutes > 0) return minutes + "m " + secs + "s";
        return secs + "s";
    }
}
