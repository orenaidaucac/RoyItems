package com.github.orenaidaucac.royItems.lore;

import com.github.orenaidaucac.royItems.RoyItems;
import com.github.orenaidaucac.royItems.core.text.TextUtility;
import com.github.orenaidaucac.royItems.item.ItemBuilder;
import com.github.orenaidaucac.royItems.item.ItemDefinition;
import com.github.orenaidaucac.royItems.item.ItemType;
import com.github.orenaidaucac.royItems.stat.StatModifier;
import com.github.orenaidaucac.royItems.stat.StatType;
import com.github.orenaidaucac.royItems.tier_set.ItemSet;
import com.github.orenaidaucac.royItems.tier_set.ItemSetManager;
import com.github.orenaidaucac.royItems.tier_set.ItemTier;
import com.github.orenaidaucac.royItems.tier_set.ItemTierManager;
import org.bukkit.NamespacedKey;
import org.bukkit.Registry;
import org.bukkit.attribute.Attribute;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
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

    private static final Pattern STAT_PLACEHOLDER_PATTERN = Pattern.compile("[#%]([a-zA-Z0-9_.-]+)[#%]");

    private final Plugin plugin;
    private ItemTierManager itemTierManager;
    private ItemSetManager itemSetManager;
    private com.github.orenaidaucac.royItems.integration.EcoEnchantsHook ecoEnchantsHook;
    private com.github.orenaidaucac.royItems.item.ItemPDCManager pdcManager;
    private com.github.orenaidaucac.royItems.item.ItemRegistry registry;
    private final Map<String, LoreFormat> formats = new ConcurrentHashMap<>();
    private final DecimalFormat df = new DecimalFormat("#,##0.##");

    public DynamicLoreEngine(Plugin plugin) {
        this(plugin, null, null);
    }

    public DynamicLoreEngine(Plugin plugin, ItemTierManager itemTierManager, ItemSetManager itemSetManager) {
        this.plugin = plugin;
        this.itemTierManager = itemTierManager;
        this.itemSetManager = itemSetManager;
    }

    public void setItemTierManager(ItemTierManager itemTierManager) {
        this.itemTierManager = itemTierManager;
    }

    public void setItemSetManager(ItemSetManager itemSetManager) {
        this.itemSetManager = itemSetManager;
    }

    public void setEcoEnchantsHook(com.github.orenaidaucac.royItems.integration.EcoEnchantsHook ecoEnchantsHook) {
        this.ecoEnchantsHook = ecoEnchantsHook;
    }

    public void setPdcManager(com.github.orenaidaucac.royItems.item.ItemPDCManager pdcManager) {
        this.pdcManager = pdcManager;
    }

    public void setItemRegistry(com.github.orenaidaucac.royItems.item.ItemRegistry registry) {
        this.registry = registry;
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
        return buildLore(def, slottedSockets, expireTimestamp, null);
    }

    /**
     * Builds dynamic lore for an item according to its definition, active runtime data,
     * and actual runtime enchantments (Vanilla and EcoEnchants).
     */
    public List<String> buildLore(ItemDefinition def, List<String> slottedSockets, long expireTimestamp, Map<org.bukkit.enchantments.Enchantment, Integer> activeEnchants) {
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

        List<String> socketLines = generateSocketLines(def, slottedSockets, format);
        List<String> reqLines = generateRequirementLines(def, format);
        List<String> enchLines = generateEnchantmentLines(def, format, activeEnchants);

        List<String> processed = new ArrayList<>();
        String barString = format.getBar() != null ? format.getBar() : "&8&m------------------------";

        for (String rawLine : template) {
            if (rawLine == null) continue;
            String trimmed = rawLine.trim();

            if (def.isUnbreakable() && def.isHideUnbreakable() &&
                    (trimmed.contains("#durability#") || trimmed.contains("%durability%")
                    || trimmed.contains("#unbreakable#") || trimmed.contains("%unbreakable%"))) {
                continue;
            }

            if (trimmed.equalsIgnoreCase("#stats#") || trimmed.equalsIgnoreCase("%stats%")) {
                processed.addAll(generateStatLines(def, format));
            } else if (trimmed.equalsIgnoreCase("#sockets#") || trimmed.equalsIgnoreCase("%sockets%")) {
                processed.addAll(socketLines);
            } else if (trimmed.equalsIgnoreCase("#requirements#") || trimmed.equalsIgnoreCase("%requirements%")) {
                processed.addAll(reqLines);
            } else if (trimmed.equalsIgnoreCase("#enchantments#") || trimmed.equalsIgnoreCase("%enchantments%")) {
                processed.addAll(enchLines);
            } else if (trimmed.equalsIgnoreCase("#description#") || trimmed.equalsIgnoreCase("%description%")
                    || trimmed.equalsIgnoreCase("#lore#") || trimmed.equalsIgnoreCase("%lore%")) {
                if (def.getLore() != null) {
                    processed.addAll(def.getLore());
                }
            } else if (((trimmed.startsWith("#") && trimmed.endsWith("#")) || (trimmed.startsWith("%") && trimmed.endsWith("%")))
                    && isStatPlaceholder(trimmed, format)) {
                // Standalone stat placeholder: e.g. "#attack-damage#" or "%attack-damage%"
                String key = trimmed.substring(1, trimmed.length() - 1);
                StatModifier mod = findStatModifier(def, key);
                if (mod != null && !mod.isEmpty()) {
                    String displayName = findStatDisplayName(key, format);
                    String statLine = format.formatStatLine(key, displayName, mod, df);
                    if (statLine != null && !statLine.isEmpty()) {
                        processed.add(statLine);
                    }
                }
                // If stat is null or empty, the entire line is skipped cleanly!
            } else {
                // Inline check: does this line contain any stat placeholders?
                List<String> statKeysInLine = extractStatPlaceholdersInLine(rawLine, format);
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
                        String val = format.formatStatValue(key, mod, df);
                        line = line.replace("#" + key + "#", val != null ? val : "");
                        line = line.replace("#" + key.toUpperCase() + "#", val != null ? val : "");
                        line = line.replace("%" + key + "%", val != null ? val : "");
                        line = line.replace("%" + key.toUpperCase() + "%", val != null ? val : "");
                        // Also replace #name# / %name% / #symbol# / %symbol% if present on this line
                        String dName = findStatDisplayName(key, format);
                        line = line.replace("#name#", dName != null ? dName : key);
                        line = line.replace("%name%", dName != null ? dName : key);
                        LoreFormat.StatDisplayConfig cfg = format.getCustomStat(key);
                        String sym = (cfg != null && cfg.getSymbol() != null) ? cfg.getSymbol() : "";
                        line = line.replace("#symbol#", sym).replace("%symbol%", sym);
                    }

                    processed.add(replaceGeneralPlaceholders(line, def, expireTimestamp, format));
                } else {
                    // Standard line
                    processed.add(replaceGeneralPlaceholders(rawLine, def, expireTimestamp, format));
                }
            }
        }

        // Clean up conditional #bar# separators
        return pruneBars(processed, barString);
    }

    public String replaceGeneralPlaceholders(String input, ItemDefinition def, long expireTimestamp, LoreFormat format) {
        if (input == null) return "";

        String tierDisplay = resolveTierDisplayName(def);
        String tierColor = resolveTierColor(def);
        String setDisplay = resolveSetDisplayName(def);
        String typeDesc = def.getType() != null ? def.getType().getDescription() : "";
        String tierId = def.getItemTier() != null ? def.getItemTier() : "";
        String setId = def.getItemSet() != null ? def.getItemSet() : "";
        String itemName = def.getName() != null ? def.getName() : "";

        String s = input
                // Name
                .replace("#name#", itemName)
                .replace("%name%", itemName)
                // Type
                .replace("#type#", typeDesc)
                .replace("%type%", typeDesc)
                // Tier
                .replace("#tier#", tierDisplay)
                .replace("%tier%", tierDisplay)
                .replace("#tier-name#", tierDisplay)
                .replace("%tier-name%", tierDisplay)
                .replace("#tier-display#", tierDisplay)
                .replace("%tier-display%", tierDisplay)
                .replace("#tier-id#", tierId)
                .replace("%tier-id%", tierId)
                .replace("#tier-color#", tierColor)
                .replace("%tier-color%", tierColor)
                .replace("#tier_color#", tierColor)
                .replace("%tier_color%", tierColor)
                // Set
                .replace("#set#", setDisplay)
                .replace("%set%", setDisplay)
                .replace("#set-name#", setDisplay)
                .replace("%set-name%", setDisplay)
                .replace("#set-display#", setDisplay)
                .replace("%set-display%", setDisplay)
                .replace("#set-id#", setId)
                .replace("%set-id%", setId);

        // Durability
        String durDisplay = "";
        if (def.isUnbreakable() && !def.isHideUnbreakable()) {
            durDisplay = (format != null && format.getDurabilityUnbreakable() != null)
                    ? format.getDurabilityUnbreakable() : "&aUnbreakable";
        }
        s = s.replace("#durability#", durDisplay).replace("%durability%", durDisplay)
                .replace("#unbreakable#", durDisplay).replace("%unbreakable%", durDisplay);

        // Expiration
        if (expireTimestamp > 0) {
            long remainingSec = Math.max(0, (expireTimestamp - System.currentTimeMillis()) / 1000);
            String dur = formatDuration(remainingSec);
            s = s.replace("#expired#", dur).replace("%expired%", dur)
                    .replace("#expiration#", dur).replace("%expiration%", dur);
        } else {
            s = s.replace("#expired#", "").replace("%expired%", "")
                    .replace("#expiration#", "").replace("%expiration%", "");
        }

        // Cooldown
        if (def.getCooldown() > 0) {
            String cd = df.format(def.getCooldown()) + "s";
            s = s.replace("#cooldown#", cd).replace("%cooldown%", cd);
        } else {
            s = s.replace("#cooldown#", "").replace("%cooldown%", "");
        }

        return s;
    }

    public String replaceGeneralPlaceholders(String input, ItemDefinition def, long expireTimestamp) {
        return replaceGeneralPlaceholders(input, def, expireTimestamp, null);
    }

    public String resolveTierDisplayName(ItemDefinition def) {
        if (def == null || def.getItemTier() == null || def.getItemTier().isEmpty()) {
            return "";
        }
        String tierId = def.getItemTier();
        ItemTierManager manager = this.itemTierManager;
        if (manager == null && plugin instanceof RoyItems royItems) {
            manager = royItems.getItemTierManager();
        }
        if (manager != null) {
            ItemTier tier = manager.get(tierId);
            if (tier != null && tier.getDisplayName() != null && !tier.getDisplayName().isEmpty()) {
                return tier.getDisplayName();
            }
        }
        return tierId;
    }

    public String resolveTierColor(ItemDefinition def) {
        if (def == null || def.getItemTier() == null || def.getItemTier().isEmpty()) {
            return "";
        }
        String tierId = def.getItemTier();
        ItemTierManager manager = this.itemTierManager;
        if (manager == null && plugin instanceof RoyItems royItems) {
            manager = royItems.getItemTierManager();
        }
        if (manager != null) {
            ItemTier tier = manager.get(tierId);
            if (tier != null && tier.getColor() != null && !tier.getColor().isEmpty()) {
                return tier.getColor();
            }
        }
        return "";
    }

    public String resolveSetDisplayName(ItemDefinition def) {
        if (def == null || def.getItemSet() == null || def.getItemSet().isEmpty()) {
            return "";
        }
        String setId = def.getItemSet();
        ItemSetManager manager = this.itemSetManager;
        if (manager == null && plugin instanceof RoyItems royItems) {
            manager = royItems.getItemSetManager();
        }
        if (manager != null) {
            ItemSet set = manager.get(setId);
            if (set != null && set.getDisplayName() != null && !set.getDisplayName().isEmpty()) {
                return set.getDisplayName();
            }
        }
        return setId;
    }

    private List<String> extractStatPlaceholdersInLine(String line, LoreFormat format) {
        List<String> list = new ArrayList<>();
        if (line == null) return list;
        Matcher m = STAT_PLACEHOLDER_PATTERN.matcher(line);
        while (m.find()) {
            String candidate = m.group(1);
            if (isStatKey(candidate, format)) {
                list.add(candidate);
            }
        }
        return list;
    }

    private List<String> extractStatPlaceholdersInLine(String line) {
        return extractStatPlaceholdersInLine(line, null);
    }

    public boolean isStatPlaceholder(String token, LoreFormat format) {
        if (token == null || token.length() <= 2) {
            return false;
        }
        if ((token.startsWith("#") && token.endsWith("#")) || (token.startsWith("%") && token.endsWith("%"))) {
            String clean = token.substring(1, token.length() - 1);
            return isStatKey(clean, format);
        }
        return false;
    }

    public boolean isStatPlaceholder(String token) {
        return isStatPlaceholder(token, null);
    }

    public boolean isStatKey(String key, LoreFormat format) {
        if (key == null || key.isEmpty()) return false;
        String normalized = key.toLowerCase().replace('_', '-');
        if (StatType.fromKey(normalized) != null) return true;
        if (findVanillaAttribute(key) != null) return true;
        if (format != null && format.getCustomStat(normalized) != null) return true;
        return false;
    }

    public boolean isStatKey(String key) {
        return isStatKey(key, null);
    }

    public StatModifier findStatModifier(ItemDefinition def, String key) {
        if (def == null || key == null) return null;
        String normalized = key.toLowerCase().replace('_', '-');

        // 1. Custom Stats (StatType enum)
        StatType type = StatType.fromKey(normalized);
        if (type != null) {
            StatModifier mod = def.getStat(type);
            if (mod != null && !mod.isEmpty()) return mod;
            if (type.hasVanillaAttribute() && def.getVanillaAttributes() != null) {
                mod = def.getVanillaAttributes().get(type.getVanillaAttribute());
                if (mod != null && !mod.isEmpty()) return mod;
            }
        }

        // 2. Vanilla Attributes
        Attribute attr = findVanillaAttribute(key);
        if (attr != null && def.getVanillaAttributes() != null) {
            StatModifier mod = def.getVanillaAttributes().get(attr);
            if (mod != null && !mod.isEmpty()) return mod;
        }

        // 3. Dynamic / Arbitrary custom stats
        if (def.getCustomStats() != null) {
            StatModifier mod = def.getCustomStat(normalized);
            if (mod != null && !mod.isEmpty()) return mod;
            if (normalized.startsWith("vanilla-")) {
                mod = def.getCustomStat(normalized.substring("vanilla-".length()));
                if (mod != null && !mod.isEmpty()) return mod;
            }
        }

        return null;
    }

    public String findStatDisplayName(String key, LoreFormat format) {
        if (key == null) return "";
        String normalized = key.toLowerCase().replace('_', '-');

        // 1. Custom override from LoreFormat
        if (format != null) {
            LoreFormat.StatDisplayConfig custom = format.getCustomStat(normalized);
            if (custom != null && custom.getName() != null && !custom.getName().isEmpty()) {
                return custom.getName();
            }
        }

        // 2. StatType default display name
        StatType type = StatType.fromKey(normalized);
        if (type != null) {
            return type.getDefaultDisplayName();
        }

        // 3. Vanilla Attribute formatted name
        Attribute attr = findVanillaAttribute(key);
        if (attr != null) {
            return formatAttributeName(attr);
        }

        return key;
    }

    public String findStatDisplayName(String key) {
        return findStatDisplayName(key, null);
    }

    public static String formatAttributeName(Attribute attr) {
        if (attr == null) return "";
        String key = attr.getKey().getKey();
        if (key.startsWith("generic.")) {
            key = key.substring("generic.".length());
        } else if (key.startsWith("player.")) {
            key = key.substring("player.".length());
        }
        String[] words = key.replace('_', ' ').split("\\s+");
        StringBuilder sb = new StringBuilder();
        for (String w : words) {
            if (w.isEmpty()) continue;
            if (sb.length() > 0) sb.append(" ");
            sb.append(Character.toUpperCase(w.charAt(0))).append(w.substring(1).toLowerCase());
        }
        return sb.toString();
    }

    public static Attribute findVanillaAttribute(String key) {
        if (key == null || key.isEmpty()) return null;
        String normalized = key.toLowerCase().replace('-', '_');
        if (normalized.startsWith("vanilla_")) {
            normalized = normalized.substring("vanilla_".length());
        }
        try {
            return Attribute.valueOf(normalized.toUpperCase());
        } catch (Throwable ignored) {}
        try {
            if (org.bukkit.Bukkit.getServer() == null) return null;
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

        // Custom stats (StatType enum)
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

                String attrKey = attr.getKey().getKey();
                String line = format.formatStatLine(attrKey, formatAttributeName(attr), mod, df);
                if (line != null && !line.isEmpty()) {
                    lines.add(line);
                }
            }
        }

        // Dynamic / Arbitrary custom stats
        if (def.getCustomStats() != null) {
            for (Map.Entry<String, StatModifier> entry : def.getCustomStats().entrySet()) {
                String cKey = entry.getKey();
                StatModifier mod = entry.getValue();
                if (mod == null || mod.isEmpty()) continue;

                String line = format.formatStatLine(cKey, cKey, mod, df);
                if (line != null && !line.isEmpty()) {
                    lines.add(line);
                }
            }
        }

        return lines;
    }

    private List<String> generateSocketLines(ItemDefinition def, List<String> slottedSockets, LoreFormat format) {
        List<String> lines = new ArrayList<>();
        if (def == null || def.getGemSockets() == null || def.getGemSockets().isEmpty()) return lines;

        String emptyTpl = format != null && format.getSocketEmpty() != null ? format.getSocketEmpty() : "&8[&7◇&8] &7Empty Socket (%type%)";
        String filledTpl = format != null && format.getSocketFilled() != null ? format.getSocketFilled() : "&8[&a◆&8] &f%gem%";

        for (int i = 0; i < def.getGemSockets().size(); i++) {
            String socketType = def.getGemSockets().get(i);
            String slottedGem = (slottedSockets != null && i < slottedSockets.size()) ? slottedSockets.get(i) : null;
            if (slottedGem != null && !slottedGem.isEmpty()) {
                lines.add(filledTpl.replace("%gem%", slottedGem).replace("#gem#", slottedGem)
                        .replace("%name%", slottedGem).replace("#name#", slottedGem)
                        .replace("%type%", socketType).replace("#type#", socketType));
            } else {
                lines.add(emptyTpl.replace("%type%", socketType).replace("#type#", socketType));
            }
        }
        return lines;
    }

    private List<String> generateRequirementLines(ItemDefinition def, LoreFormat format) {
        List<String> lines = new ArrayList<>();
        if (def == null) return lines;

        String lvlTpl = format != null && format.getReqLevel() != null ? format.getReqLevel() : "&cRequires Level: &f%level%";
        String permTpl = format != null && format.getReqPermission() != null ? format.getReqPermission() : "&cRequires Permission: &f%permission%";
        String classTpl = format != null && format.getReqClass() != null ? format.getReqClass() : "&cClass: &f%class%";
        String classLvlTpl = format != null && format.getReqClassWithLevel() != null ? format.getReqClassWithLevel() : "&cClass: &f%class% (Lv.%level%)";
        String auraTpl = format != null && format.getReqAuraSkill() != null ? format.getReqAuraSkill() : "&cAuraSkill: &f%skill% Lv.%level%";

        if (def.getLevelRequired() > 0) {
            lines.add(lvlTpl.replace("%level%", String.valueOf(def.getLevelRequired())).replace("#level#", String.valueOf(def.getLevelRequired())));
        }
        if (def.getPermission() != null && !def.getPermission().isEmpty()) {
            lines.add(permTpl.replace("%permission%", def.getPermission()).replace("#permission#", def.getPermission()));
        }
        if (def.getMmocoreClass() != null && !def.getMmocoreClass().isEmpty()) {
            if (def.getMmocoreLevel() > 0) {
                lines.add(classLvlTpl.replace("%class%", def.getMmocoreClass()).replace("#class#", def.getMmocoreClass())
                        .replace("%level%", String.valueOf(def.getMmocoreLevel())).replace("#level#", String.valueOf(def.getMmocoreLevel())));
            } else {
                lines.add(classTpl.replace("%class%", def.getMmocoreClass()).replace("#class#", def.getMmocoreClass()));
            }
        }
        if (def.getAuraSkill() != null && !def.getAuraSkill().isEmpty()) {
            lines.add(auraTpl.replace("%skill%", def.getAuraSkill()).replace("#skill#", def.getAuraSkill())
                    .replace("%level%", String.valueOf(def.getAuraSkillLevel())).replace("#level#", String.valueOf(def.getAuraSkillLevel())));
        }
        return lines;
    }

    public List<String> generateEnchantmentLines(ItemDefinition def, LoreFormat format) {
        return generateEnchantmentLines(def, format, null);
    }

    public List<String> generateEnchantmentLines(ItemDefinition def, LoreFormat format, Map<org.bukkit.enchantments.Enchantment, Integer> activeEnchants) {
        List<String> lines = new ArrayList<>();
        if (def == null || def.isHideEnchantments()) {
            return lines;
        }

        Map<org.bukkit.enchantments.Enchantment, Integer> enchants = new LinkedHashMap<>();
        if (activeEnchants != null && !activeEnchants.isEmpty()) {
            enchants.putAll(activeEnchants);
        } else if (def.getEnchantments() != null) {
            enchants.putAll(def.getEnchantments());
        }

        if (enchants.isEmpty()) {
            return lines;
        }

        List<Map.Entry<org.bukkit.enchantments.Enchantment, Integer>> sorted = sortEnchantments(enchants, format);

        String tpl = format != null && format.getEnchantmentFormat() != null ? format.getEnchantmentFormat() : "&7%name% %level%";
        String curseTpl = format != null && format.getEnchantmentCurseFormat() != null ? format.getEnchantmentCurseFormat() : "&c%name% %level%";
        String symbol = format != null && format.getEnchantmentSymbol() != null ? format.getEnchantmentSymbol() : "";
        String levelStyle = format != null && format.getEnchantmentLevelStyle() != null ? format.getEnchantmentLevelStyle() : "ROMAN";
        Map<String, String> customNames = format != null ? format.getEnchantmentCustomNames() : Collections.emptyMap();

        for (Map.Entry<org.bukkit.enchantments.Enchantment, Integer> entry : sorted) {
            org.bukkit.enchantments.Enchantment ench = entry.getKey();
            if (ench == null) continue;
            int level = entry.getValue();

            // Filter out dummy glow enchantment (LUCK_OF_THE_SEA level 1) if item is set to glow
            if (def.isGlow() && ench.equals(org.bukkit.enchantments.Enchantment.LUCK_OF_THE_SEA)
                    && (def.getEnchantments() == null || !def.getEnchantments().containsKey(org.bukkit.enchantments.Enchantment.LUCK_OF_THE_SEA))) {
                continue;
            }

            // 1. EcoEnchants integration: check if EcoEnchant
            if (ecoEnchantsHook != null && ecoEnchantsHook.isEcoEnchant(ench)) {
                String ecoFormatted = ecoEnchantsHook.getFormattedName(ench, level);
                if (ecoFormatted != null && !ecoFormatted.isEmpty()) {
                    String line = ecoFormatted;
                    if (!symbol.isEmpty() && !line.startsWith(symbol)) {
                        line = symbol + line;
                    }
                    lines.add(line);
                    continue;
                }
            }

            // 2. Curse enchantment check
            boolean isCurse = isCurseEnchantment(ench);
            String selectedTpl = isCurse ? curseTpl : tpl;

            // 3. Name resolution
            String keyName = ench.getKey().getKey().toLowerCase().replace('-', '_');
            String name;
            if (customNames != null && customNames.containsKey(keyName)) {
                name = customNames.get(keyName);
            } else {
                name = formatEnchantmentName(ench);
            }

            // 4. Level resolution
            String levelStr;
            if (ench.getMaxLevel() == 1 && level == 1) {
                // Vanilla standard: single-level enchantments (e.g. Mending, Silk Touch) do not display "I"
                levelStr = "";
            } else if ("NUMBER".equalsIgnoreCase(levelStyle)) {
                levelStr = String.valueOf(level);
            } else {
                levelStr = toRoman(level);
            }

            // 5. Line formatting
            String line = selectedTpl
                    .replace("%name%", name).replace("#name#", name)
                    .replace("%level%", levelStr).replace("#level#", levelStr)
                    .replace("%raw-level%", String.valueOf(level)).replace("#raw-level#", String.valueOf(level))
                    .replace("%symbol%", symbol).replace("#symbol#", symbol);

            // Clean up trailing space if levelStr was empty
            line = line.replaceAll("\\s+$", "");
            lines.add(line);
        }

        return lines;
    }

    private List<Map.Entry<org.bukkit.enchantments.Enchantment, Integer>> sortEnchantments(
            Map<org.bukkit.enchantments.Enchantment, Integer> enchants, LoreFormat format) {
        List<Map.Entry<org.bukkit.enchantments.Enchantment, Integer>> list = new ArrayList<>(enchants.entrySet());
        String order = format != null && format.getEnchantmentOrder() != null
                ? format.getEnchantmentOrder().toUpperCase() : "VANILLA_FIRST";

        Comparator<Map.Entry<org.bukkit.enchantments.Enchantment, Integer>> comparator;
        switch (order) {
            case "ECO_FIRST":
                comparator = (e1, e2) -> {
                    boolean eco1 = ecoEnchantsHook != null && ecoEnchantsHook.isEcoEnchant(e1.getKey());
                    boolean eco2 = ecoEnchantsHook != null && ecoEnchantsHook.isEcoEnchant(e2.getKey());
                    if (eco1 != eco2) return eco1 ? -1 : 1;
                    return e1.getKey().getKey().toString().compareToIgnoreCase(e2.getKey().getKey().toString());
                };
                break;
            case "ALPHABETICAL":
                comparator = (e1, e2) -> e1.getKey().getKey().toString().compareToIgnoreCase(e2.getKey().getKey().toString());
                break;
            case "LEVEL_DESC":
                comparator = (e1, e2) -> {
                    int c = Integer.compare(e2.getValue(), e1.getValue());
                    if (c != 0) return c;
                    return e1.getKey().getKey().toString().compareToIgnoreCase(e2.getKey().getKey().toString());
                };
                break;
            case "LEVEL_ASC":
                comparator = (e1, e2) -> {
                    int c = Integer.compare(e1.getValue(), e2.getValue());
                    if (c != 0) return c;
                    return e1.getKey().getKey().toString().compareToIgnoreCase(e2.getKey().getKey().toString());
                };
                break;
            case "VANILLA_FIRST":
            default:
                comparator = (e1, e2) -> {
                    boolean eco1 = ecoEnchantsHook != null && ecoEnchantsHook.isEcoEnchant(e1.getKey());
                    boolean eco2 = ecoEnchantsHook != null && ecoEnchantsHook.isEcoEnchant(e2.getKey());
                    if (eco1 != eco2) return eco1 ? 1 : -1;
                    boolean curse1 = isCurseEnchantment(e1.getKey());
                    boolean curse2 = isCurseEnchantment(e2.getKey());
                    if (curse1 != curse2) return curse1 ? 1 : -1;
                    return e1.getKey().getKey().toString().compareToIgnoreCase(e2.getKey().getKey().toString());
                };
                break;
        }

        list.sort(comparator);
        return list;
    }

    public static boolean isCurseEnchantment(org.bukkit.enchantments.Enchantment ench) {
        if (ench == null) return false;
        try {
            return ench.isCursed();
        } catch (Throwable ignored) {
            String key = ench.getKey().getKey().toLowerCase();
            return key.startsWith("curse_") || key.contains("curse");
        }
    }

    private static final int[] ROMAN_VALUES = {1000, 900, 500, 400, 100, 90, 50, 40, 10, 9, 5, 4, 1};
    private static final String[] ROMAN_SYMBOLS = {"M", "CM", "D", "CD", "C", "XC", "L", "XL", "X", "IX", "V", "IV", "I"};

    public static String toRoman(int level) {
        if (level <= 0) return String.valueOf(level);
        if (level > 3999) return String.valueOf(level);

        StringBuilder sb = new StringBuilder();
        int num = level;
        for (int i = 0; i < ROMAN_VALUES.length; i++) {
            while (num >= ROMAN_VALUES[i]) {
                sb.append(ROMAN_SYMBOLS[i]);
                num -= ROMAN_VALUES[i];
            }
        }
        return sb.toString();
    }

    public static String formatEnchantmentName(org.bukkit.enchantments.Enchantment ench) {
        if (ench == null) return "";
        String key = ench.getKey().getKey();
        String[] words = key.replace('_', ' ').split("\\s+");
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < words.length; i++) {
            String w = words[i];
            if (w.isEmpty()) continue;
            if (sb.length() > 0) sb.append(" ");
            if (i > 0 && (w.equalsIgnoreCase("of") || w.equalsIgnoreCase("the"))) {
                sb.append(w.toLowerCase());
            } else {
                sb.append(Character.toUpperCase(w.charAt(0))).append(w.substring(1).toLowerCase());
            }
        }
        return sb.toString();
    }

    public boolean hasEnchantmentsPlaceholder(ItemDefinition def) {
        if (def == null) return false;
        String formatId = def.getLoreFormat() != null ? def.getLoreFormat().toLowerCase() : "default";
        LoreFormat format = formats.getOrDefault(formatId, formats.get("default"));
        if (format == null || format.getFormat() == null) return false;
        for (String line : format.getFormat()) {
            if (line == null) continue;
            String trimmed = line.trim();
            if (trimmed.equalsIgnoreCase("#enchantments#") || trimmed.equalsIgnoreCase("%enchantments%")) {
                return true;
            }
        }
        return false;
    }

    public boolean synchronizeItemEnchantments(ItemStack item) {
        if (item == null || item.getType().isAir()) return false;
        if (pdcManager == null || !pdcManager.isRoyItem(item)) return false;
        if (registry == null) return false;

        ItemType type = pdcManager.getType(item);
        String id = pdcManager.getId(item);
        ItemDefinition def = registry.get(type, id);
        if (def == null) return false;

        ItemMeta meta = item.getItemMeta();
        if (meta == null) return false;

        Map<org.bukkit.enchantments.Enchantment, Integer> activeEnchants = new LinkedHashMap<>(meta.getEnchants());
        if (meta instanceof org.bukkit.inventory.meta.EnchantmentStorageMeta storageMeta) {
            activeEnchants.putAll(storageMeta.getStoredEnchants());
        }

        String socketsRaw = pdcManager.getSocketsData(item);
        List<String> sockets = ItemBuilder.deserializeSockets(socketsRaw);
        long expire = pdcManager.getExpireTime(item);

        List<String> rawLore = buildLore(def, sockets, expire, activeEnchants);
        meta.lore(TextUtility.formatLore(rawLore));

        // When lore displays enchantments, suppress vanilla client tooltip so client does not duplicate
        if (hasEnchantmentsPlaceholder(def)) {
            meta.addItemFlags(org.bukkit.inventory.ItemFlag.HIDE_ENCHANTS);
        }

        pdcManager.setEnchantHash(meta, activeEnchants.hashCode());
        item.setItemMeta(meta);
        return true;
    }

    public boolean checkAndSynchronizeEnchantments(ItemStack item) {
        if (item == null || item.getType().isAir()) return false;
        if (pdcManager == null || !pdcManager.isRoyItem(item)) return false;

        ItemMeta meta = item.getItemMeta();
        if (meta == null) return false;

        Map<org.bukkit.enchantments.Enchantment, Integer> activeEnchants = meta.getEnchants();
        int currentHash = activeEnchants.hashCode();
        int storedHash = pdcManager.getEnchantHash(meta);

        if (currentHash != storedHash) {
            return synchronizeItemEnchantments(item);
        }
        return false;
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
