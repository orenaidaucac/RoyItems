package com.github.orenaidaucac.royItems.lore;

import com.github.orenaidaucac.royItems.item.ItemDefinition;
import com.github.orenaidaucac.royItems.stat.StatModifier;
import com.github.orenaidaucac.royItems.stat.StatType;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.Plugin;

import java.io.File;
import java.text.DecimalFormat;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Dynamic Lore Formatting Engine with #stats# and conditional #bar# separator pruning.
 */
public class DynamicLoreEngine {

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

        File[] files = folder.listFiles((dir, name) -> name.endsWith(".yml"));
        if (files == null || files.length == 0) {
            // Save default lore formats if empty
            saveDefaultFormat("default.yml");
            saveDefaultFormat("weapon.yml");
            saveDefaultFormat("armor.yml");
            files = folder.listFiles((dir, name) -> name.endsWith(".yml"));
        }

        if (files != null) {
            for (File file : files) {
                String id = file.getName().replace(".yml", "").toLowerCase();
                FileConfiguration config = YamlConfiguration.loadConfiguration(file);
                LoreFormat format = LoreFormat.fromConfig(id, config);
                formats.put(id, format);
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

        String formatId = def.getLoreFormat() != null ? def.getLoreFormat().toLowerCase() : "default";
        LoreFormat format = formats.getOrDefault(formatId, formats.get("default"));

        // Generate lines for #stats#
        List<String> statLines = generateStatLines(def, format);

        // Generate lines for #sockets#
        List<String> socketLines = generateSocketLines(def, slottedSockets);

        // Generate lines for #requirements#
        List<String> reqLines = generateRequirementLines(def);

        // Raw template lines
        List<String> template = (format != null && format.getFormat() != null && !format.getFormat().isEmpty())
                ? new ArrayList<>(format.getFormat())
                : new ArrayList<>(def.getLore());

        // Replace placeholders in template
        List<String> processed = new ArrayList<>();
        String barString = format != null ? format.getBar() : "&8&m------------------------";

        for (String line : template) {
            if (line.equalsIgnoreCase("#stats#")) {
                processed.addAll(statLines);
            } else if (line.equalsIgnoreCase("#sockets#")) {
                processed.addAll(socketLines);
            } else if (line.equalsIgnoreCase("#requirements#")) {
                processed.addAll(reqLines);
            } else if (line.equalsIgnoreCase("#description#") || line.equalsIgnoreCase("#lore#")) {
                if (def.getLore() != null) {
                    processed.addAll(def.getLore());
                }
            } else {
                String replaced = line
                        .replace("#type#", def.getType().getDescription())
                        .replace("#tier#", def.getItemTier() != null ? def.getItemTier() : "")
                        .replace("#set#", def.getItemSet() != null ? def.getItemSet() : "");

                if (expireTimestamp > 0) {
                    long remainingSec = Math.max(0, (expireTimestamp - System.currentTimeMillis()) / 1000);
                    replaced = replaced.replace("#expired#", formatDuration(remainingSec));
                } else {
                    replaced = replaced.replace("#expired#", "");
                }

                if (def.getCooldown() > 0) {
                    replaced = replaced.replace("#cooldown#", df.format(def.getCooldown()) + "s");
                } else {
                    replaced = replaced.replace("#cooldown#", "");
                }

                processed.add(replaced);
            }
        }

        // Now prune conditional #bar# separators
        return pruneBars(processed, barString);
    }

    private List<String> generateStatLines(ItemDefinition def, LoreFormat format) {
        List<String> lines = new ArrayList<>();
        if (def.getStats() == null || def.getStats().isEmpty()) return lines;

        String posTpl = format != null ? format.getPosStat() : "&7#name#: &a+#value#";
        String negTpl = format != null ? format.getNegStat() : "&7#name#: &c-#value#";
        String posPctTpl = format != null ? format.getPosPctStat() : "&7#name#: &a+#value#%";
        String negPctTpl = format != null ? format.getNegPctStat() : "&7#name#: &c-#value#%";

        for (Map.Entry<StatType, StatModifier> entry : def.getStats().entrySet()) {
            StatType type = entry.getKey();
            StatModifier mod = entry.getValue();
            if (mod == null || mod.isEmpty()) continue;

            // Flat
            if (Math.abs(mod.getFlat()) > 0.0001) {
                String tpl = mod.getFlat() > 0 ? posTpl : negTpl;
                lines.add(tpl.replace("#name#", type.getDefaultDisplayName())
                        .replace("#value#", df.format(Math.abs(mod.getFlat()))));
            }

            // Percent
            if (Math.abs(mod.getPercent()) > 0.0001) {
                String tpl = mod.getPercent() > 0 ? posPctTpl : negPctTpl;
                lines.add(tpl.replace("#name#", type.getDefaultDisplayName())
                        .replace("#value#", df.format(Math.abs(mod.getPercent() * 100.0))));
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
    private List<String> pruneBars(List<String> rawLines, String barDisplay) {
        List<String> filtered = new ArrayList<>();
        boolean previousWasBar = true; // Prevents leading bar

        for (int i = 0; i < rawLines.size(); i++) {
            String line = rawLines.get(i);
            if (line == null) continue;

            boolean isBar = line.equalsIgnoreCase("#bar#") || line.equals(barDisplay);

            if (isBar) {
                if (previousWasBar) {
                    // Skip consecutive bars or bar at the beginning
                    continue;
                }
                // Check if there is any non-empty content remaining after this bar
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
