package com.github.orenaidaucac.royItems.lore;

import com.github.orenaidaucac.royItems.RoyItems;
import com.github.orenaidaucac.royItems.core.text.TextUtility;
import com.github.orenaidaucac.royItems.integration.EcoEnchantsHook;
import com.github.orenaidaucac.royItems.item.ItemBuilder;
import com.github.orenaidaucac.royItems.item.ItemDefinition;
import com.github.orenaidaucac.royItems.item.ItemType;
import com.github.orenaidaucac.royItems.stat.StatModifier;
import com.github.orenaidaucac.royItems.stat.StatType;
import com.github.orenaidaucac.royItems.tier_set.ItemSet;
import com.github.orenaidaucac.royItems.tier_set.ItemSetManager;
import com.github.orenaidaucac.royItems.tier_set.ItemTier;
import com.github.orenaidaucac.royItems.tier_set.ItemTierManager;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.NamespacedKey;
import org.bukkit.Registry;
import org.bukkit.attribute.Attribute;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
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
    private static final Pattern LEADING_COLOR_PATTERN = Pattern.compile(
            "^(?:[&§]x(?:[&§][0-9a-fA-F]){6}|[&§]#[0-9a-fA-F]{6}|[&§][0-9a-fk-or]|<[a-zA-Z0-9_#/-]+>|\\{#[0-9a-fA-F]{6}\\})",
            Pattern.CASE_INSENSITIVE
    );
    private static final Pattern LEADING_COLOR_BEFORE_NAME = Pattern.compile(
            "^(?:(?:[&§]x(?:[&§][0-9a-fA-F]){6}|[&§]#[0-9a-fA-F]{6}|[&§][0-9a-fk-or]|<[a-zA-Z0-9_#/-]+>|\\{#[0-9a-fA-F]{6}\\})\\s*)+(?=[%#]name[%#])",
            Pattern.CASE_INSENSITIVE
    );

    private static final List<String> KNOWN_ENCHANTMENT_NAMES = List.of(
            // Vanilla English
            "protection", "fire protection", "feather falling", "blast protection", "projectile protection",
            "respiration", "aqua affinity", "thorns", "depth strider", "frost walker", "soul speed", "swift sneak",
            "sharpness", "smite", "bane of arthropods", "knockback", "fire aspect", "looting", "sweeping edge",
            "efficiency", "silk touch", "unbreaking", "fortune", "power", "punch", "flame", "infinity",
            "luck of the sea", "lure", "loyalty", "impaling", "riptide", "channeling", "multishot", "quick charge",
            "piercing", "mending", "curse of vanishing", "curse of binding", "density", "breach", "wind burst",
            // Common Vietnamese localization
            "bảo vệ", "bảo vệ khỏi lửa", "rơi chậm", "thở dưới nước", "gai", "sải bước nhanh", "đi trên băng",
            "tốc độ linh hồn", "lén lút nhanh", "sắc bén", "hại thây ma", "hại chân đốt", "bật lùi", "nhặt lượm",
            "càn quét", "hiệu suất", "đập mềm", "bền bỉ", "gia tài", "sức mạnh", "lửa", "vô hạn", "may mắn của biển",
            "nhử mồi", "lòng trung thành", "xiên khích", "thủy triều", "triệu hồi sấm sét", "đa cung", "nạp nhanh",
            "xuyên thấu", "sửa chữa", "lời nguyền biến mất", "lời nguyền trói buộc"
    );

    private static boolean startsWithColorCode(String str) {
        if (str == null || str.isEmpty()) return false;
        return LEADING_COLOR_PATTERN.matcher(str).find();
    }

    private final Plugin plugin;
    private ItemTierManager itemTierManager;
    private ItemSetManager itemSetManager;
    private com.github.orenaidaucac.royItems.stat.StatManager statManager;
    private com.github.orenaidaucac.royItems.integration.EcoEnchantsHook ecoEnchantsHook;
    private com.github.orenaidaucac.royItems.item.ItemPDCManager pdcManager;
    private com.github.orenaidaucac.royItems.item.ItemRegistry registry;
    private final Map<String, LoreFormat> formats = new ConcurrentHashMap<>();
    private final DecimalFormat df = new DecimalFormat("#,##0.##");
    private LoreFormat globalEnchantmentFormat = new LoreFormat();

    public DynamicLoreEngine(Plugin plugin) {
        this(plugin, null, null);
    }

    public DynamicLoreEngine(Plugin plugin, ItemTierManager itemTierManager, ItemSetManager itemSetManager) {
        this.plugin = plugin;
        this.itemTierManager = itemTierManager;
        this.itemSetManager = itemSetManager;
    }

    public Plugin getPlugin() {
        return plugin;
    }

    public LoreFormat getGlobalEnchantmentFormat() {
        return globalEnchantmentFormat;
    }

    public void setGlobalEnchantmentFormat(LoreFormat globalEnchantmentFormat) {
        this.globalEnchantmentFormat = (globalEnchantmentFormat != null) ? globalEnchantmentFormat : new LoreFormat();
    }

    public void setItemTierManager(ItemTierManager itemTierManager) {
        this.itemTierManager = itemTierManager;
    }

    public void setItemSetManager(ItemSetManager itemSetManager) {
        this.itemSetManager = itemSetManager;
    }

    public void setStatManager(com.github.orenaidaucac.royItems.stat.StatManager statManager) {
        this.statManager = statManager;
    }

    public com.github.orenaidaucac.royItems.stat.StatManager getStatManager() {
        return statManager;
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

    /**
     * Loads global enchantment defaults from config.yml into globalEnchantmentFormat.
     */
    public void loadConfig(FileConfiguration config) {
        if (config == null) return;
        ConfigurationSection sec = config.getConfigurationSection("enchantments");
        if (sec == null) sec = config.getConfigurationSection("enchantments-format");
        if (sec == null) sec = config.getConfigurationSection("enchantment-format");
        if (sec != null) {
            LoreFormat global = new LoreFormat();
            String fmt = sec.getString("format");
            if (fmt == null) fmt = sec.getString("default-format");
            if (fmt != null && !fmt.isBlank()) {
                global.setEnchantmentFormat(fmt);
            }
            if (sec.contains("curse-format")) {
                global.setEnchantmentCurseFormat(sec.getString("curse-format"));
            }
            if (sec.contains("symbol")) {
                global.setEnchantmentSymbol(sec.getString("symbol"));
            }
            if (sec.contains("level-style")) {
                global.setEnchantmentLevelStyle(sec.getString("level-style"));
            }
            if (sec.contains("order")) {
                global.setEnchantmentOrder(sec.getString("order"));
            }
            ConfigurationSection customNamesSec = sec.getConfigurationSection("custom-names");
            if (customNamesSec != null) {
                Map<String, String> map = new HashMap<>();
                for (String k : customNamesSec.getKeys(false)) {
                    map.put(k.toLowerCase().replace('-', '_'), customNamesSec.getString(k));
                }
                global.setEnchantmentCustomNames(map);
            }
            this.globalEnchantmentFormat = global;
        }
    }

    public void loadFormats() {
        formats.clear();
        if (plugin != null) {
            loadConfig(plugin.getConfig());
        }

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
                LoreFormat format = LoreFormat.fromConfig(id, config, globalEnchantmentFormat);
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
        return buildLore(def, slottedSockets, expireTimestamp, null, false, null);
    }

    /**
     * Builds dynamic lore for an item according to its definition, active runtime data,
     * and actual runtime enchantments (Vanilla and EcoEnchants).
     */
    public List<String> buildLore(ItemDefinition def, List<String> slottedSockets, long expireTimestamp, Map<org.bukkit.enchantments.Enchantment, Integer> activeEnchants) {
        return buildLore(def, slottedSockets, expireTimestamp, activeEnchants, false, null);
    }

    /**
     * Builds dynamic lore for an item according to its definition, runtime data,
     * active enchantments, and active set bonus state.
     */
    public List<String> buildLore(ItemDefinition def, List<String> slottedSockets, long expireTimestamp,
                                  Map<org.bukkit.enchantments.Enchantment, Integer> activeEnchants,
                                  boolean isSetBonusActive, Player player) {
        if (def == null) return new ArrayList<>();

        // If an item purposefully attempts to use the reserved wiki file, suppress its lore completely
        if (def.getLoreFormat() != null && (def.getLoreFormat().equalsIgnoreCase("_example") || def.getLoreFormat().equalsIgnoreCase("_example.yml"))) {
            return Collections.emptyList();
        }

        String formatId = def.getLoreFormat() != null ? def.getLoreFormat().toLowerCase() : "default";
        LoreFormat format = formats.getOrDefault(formatId, formats.get("default"));
        if (format == null) {
            format = new LoreFormat(globalEnchantmentFormat);
        }

        List<String> template = (format.getFormat() != null && !format.getFormat().isEmpty())
                ? new ArrayList<>(format.getFormat())
                : new ArrayList<>(def.getLore());

        List<String> socketLines = generateSocketLines(def, slottedSockets, format);
        List<String> reqLines = generateRequirementLines(def, format);
        List<String> enchLines = generateEnchantmentLines(def, format, activeEnchants);
        List<String> setLines = generateSetLoreLines(def, isSetBonusActive, player);

        List<String> processed = new ArrayList<>();
        String barString = format.getBar() != null ? format.getBar() : "&8&m------------------------";
        boolean enchantmentsPlaced = false;

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
                enchantmentsPlaced = true;
            } else if (trimmed.equalsIgnoreCase("#set#") || trimmed.equalsIgnoreCase("%set%")) {
                if (def.getItemSet() != null && !def.getItemSet().isEmpty() && !setLines.isEmpty()) {
                    processed.addAll(setLines);
                }
            } else if (trimmed.equalsIgnoreCase("#set-name#") || trimmed.equalsIgnoreCase("%set-name%")
                    || trimmed.equalsIgnoreCase("#set-display#") || trimmed.equalsIgnoreCase("%set-display%")) {
                String setDisplay = resolveSetDisplayName(def);
                if (!setDisplay.isEmpty()) {
                    processed.add(setDisplay);
                }
            } else if (trimmed.equalsIgnoreCase("#set-id#") || trimmed.equalsIgnoreCase("%set-id%")) {
                if (def.getItemSet() != null && !def.getItemSet().isEmpty()) {
                    processed.add(def.getItemSet());
                }
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

                    boolean containsSet = rawLine.contains("#set#") || rawLine.contains("%set%");
                    processed.add(replaceGeneralPlaceholders(line, def, expireTimestamp, format, isSetBonusActive, player));
                    if (containsSet && def.getItemSet() != null && !def.getItemSet().isEmpty() && setLines.size() > 1) {
                        for (int i = 1; i < setLines.size(); i++) {
                            processed.add(setLines.get(i));
                        }
                    }
                } else {
                    // Standard line
                    boolean containsSet = rawLine.contains("#set#") || rawLine.contains("%set%");
                    processed.add(replaceGeneralPlaceholders(rawLine, def, expireTimestamp, format, isSetBonusActive, player));
                    if (containsSet && def.getItemSet() != null && !def.getItemSet().isEmpty() && setLines.size() > 1) {
                        for (int i = 1; i < setLines.size(); i++) {
                            processed.add(setLines.get(i));
                        }
                    }
                }
            }
        }

        if (!enchantmentsPlaced && !enchLines.isEmpty()) {
            boolean autoSync = (plugin == null || plugin.getConfig() == null || plugin.getConfig().getBoolean("enchantments.auto-sync-lore", true));
            if (autoSync) {
                int insertPos = -1;
                for (int i = 0; i < processed.size(); i++) {
                    String p = processed.get(i);
                    if (p != null && (p.contains("Socket") || p.contains("Requires") || p.contains("◆") || p.contains("◇"))) {
                        insertPos = i;
                        break;
                    }
                }
                if (insertPos != -1) {
                    processed.addAll(insertPos, enchLines);
                } else if (!processed.isEmpty()) {
                    processed.addAll(processed.size() - 1, enchLines);
                } else {
                    processed.addAll(enchLines);
                }
            }
        }

        // Clean up conditional #bar# separators
        return pruneBars(processed, barString);
    }

    public String replaceGeneralPlaceholders(String input, ItemDefinition def, long expireTimestamp) {
        return replaceGeneralPlaceholders(input, def, expireTimestamp, null, false, null);
    }

    public String replaceGeneralPlaceholders(String input, ItemDefinition def, long expireTimestamp, LoreFormat format) {
        return replaceGeneralPlaceholders(input, def, expireTimestamp, format, false, null);
    }

    public String replaceGeneralPlaceholders(String input, ItemDefinition def, long expireTimestamp, LoreFormat format,
                                            boolean isSetBonusActive, Player player) {
        if (input == null) return "";

        String tierDisplay = resolveTierDisplayName(def);
        String tierColor = resolveTierColor(def);
        String setDisplayLore = resolveSetString(def, isSetBonusActive, player);
        String setOfficialName = resolveSetDisplayName(def);
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
                // Set Lore (Dynamic active/inactive)
                .replace("#set#", setDisplayLore)
                .replace("%set%", setDisplayLore)
                // Set Official Display Name
                .replace("#set-name#", setOfficialName)
                .replace("%set-name%", setOfficialName)
                .replace("#set-display#", setOfficialName)
                .replace("%set-display%", setOfficialName)
                // Set ID
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

    /**
     * Resolves the primary string representation of the set for inline placement.
     * Uses the first line of active/inactive lore if configured, otherwise falls back to display name.
     */
    public String resolveSetString(ItemDefinition def, boolean isSetBonusActive, Player player) {
        if (def == null || def.getItemSet() == null || def.getItemSet().isEmpty()) {
            return "";
        }
        List<String> setLines = generateSetLoreLines(def, isSetBonusActive, player);
        if (!setLines.isEmpty()) {
            return setLines.get(0);
        }
        return resolveSetDisplayName(def);
    }

    /**
     * Generates active or inactive lore lines for an item belonging to an Item Set.
     */
    public List<String> generateSetLoreLines(ItemDefinition def, boolean isSetBonusActive, Player player) {
        if (def == null || def.getItemSet() == null || def.getItemSet().isEmpty()) {
            return Collections.emptyList();
        }
        String setId = def.getItemSet();
        ItemSetManager manager = this.itemSetManager;
        if (manager == null && plugin instanceof RoyItems royItems) {
            manager = royItems.getItemSetManager();
        }
        if (manager == null) return Collections.emptyList();

        ItemSet set = manager.get(setId);
        if (set == null) return Collections.emptyList();

        List<String> rawLines = set.getLore(isSetBonusActive);
        if (rawLines == null || rawLines.isEmpty()) {
            if (set.getDisplayName() != null && !set.getDisplayName().isEmpty()) {
                return List.of(set.getDisplayName());
            }
            return List.of(setId);
        }

        int pieces = 0;
        if (player != null && statManager != null) {
            pieces = statManager.getEquippedSetPieceCount(player, setId);
        } else if (isSetBonusActive) {
            pieces = set.getRequiredPieces();
        }
        int required = set.getRequiredPieces();

        List<String> result = new ArrayList<>(rawLines.size());
        for (String raw : rawLines) {
            if (raw == null) continue;
            String line = raw
                    .replace("#set#", set.getDisplayName())
                    .replace("%set%", set.getDisplayName())
                    .replace("#set-name#", set.getDisplayName())
                    .replace("%set-name%", set.getDisplayName())
                    .replace("#set-display#", set.getDisplayName())
                    .replace("%set-display%", set.getDisplayName())
                    .replace("#set-id#", set.getId())
                    .replace("%set-id%", set.getId())
                    .replace("#pieces#", String.valueOf(pieces))
                    .replace("%pieces%", String.valueOf(pieces))
                    .replace("#count#", String.valueOf(pieces))
                    .replace("%count%", String.valueOf(pieces))
                    .replace("#required#", String.valueOf(required))
                    .replace("%required%", String.valueOf(required))
                    .replace("#required-pieces#", String.valueOf(required))
                    .replace("%required-pieces%", String.valueOf(required));

            if (player != null) {
                try {
                    if (org.bukkit.Bukkit.getPluginManager().getPlugin("PlaceholderAPI") != null) {
                        line = me.clip.placeholderapi.PlaceholderAPI.setPlaceholders(player, line);
                    }
                } catch (Throwable ignored) {}
            }
            result.add(line);
        }
        return result;
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

        LoreFormat activeFormat = (format != null) ? format : (globalEnchantmentFormat != null ? globalEnchantmentFormat : new LoreFormat());

        List<Map.Entry<org.bukkit.enchantments.Enchantment, Integer>> sorted = sortEnchantments(enchants, activeFormat);

        String tpl = activeFormat.getEnchantmentFormat() != null ? activeFormat.getEnchantmentFormat() : "&7%name% %level%";
        String curseTpl = activeFormat.getEnchantmentCurseFormat() != null ? activeFormat.getEnchantmentCurseFormat() : "&c%name% %level%";
        String symbol = activeFormat.getEnchantmentSymbol() != null ? activeFormat.getEnchantmentSymbol() : "";
        String levelStyle = activeFormat.getEnchantmentLevelStyle() != null ? activeFormat.getEnchantmentLevelStyle() : "ROMAN";
        Map<String, String> customNames = activeFormat.getEnchantmentCustomNames() != null ? activeFormat.getEnchantmentCustomNames() : Collections.emptyMap();

        for (Map.Entry<org.bukkit.enchantments.Enchantment, Integer> entry : sorted) {
            org.bukkit.enchantments.Enchantment ench = entry.getKey();
            if (ench == null) continue;
            int level = entry.getValue();

            // Filter out dummy glow enchantment (LUCK_OF_THE_SEA level 1) if item is set to glow
            if (def.isGlow() && ench.equals(org.bukkit.enchantments.Enchantment.LUCK_OF_THE_SEA)
                    && (def.getEnchantments() == null || !def.getEnchantments().containsKey(org.bukkit.enchantments.Enchantment.LUCK_OF_THE_SEA))) {
                continue;
            }

            boolean isEco = (ecoEnchantsHook != null && ecoEnchantsHook.isEcoEnchant(ench));
            boolean isCurse = isCurseEnchantment(ench);
            String selectedTpl = isCurse ? curseTpl : tpl;

            // 1. Name resolution (custom-names check first, then EcoEnchants hook, then Vanilla formatter)
            String name = null;
            if (customNames != null && !customNames.isEmpty()) {
                String rawKey = ench.getKey().getKey().toLowerCase();
                String fullKey = ench.getKey().toString().toLowerCase();
                String underKey = rawKey.replace('-', '_');
                String dashKey = rawKey.replace('_', '-');
                String underFull = fullKey.replace('-', '_');
                String dashFull = fullKey.replace('_', '-');

                name = customNames.get(underKey);
                if (name == null) name = customNames.get(dashKey);
                if (name == null) name = customNames.get(rawKey);
                if (name == null) name = customNames.get(underFull);
                if (name == null) name = customNames.get(dashFull);
                if (name == null) name = customNames.get(fullKey);
            }

            if (name == null || name.isBlank()) {
                if (isEco) {
                    name = ecoEnchantsHook.getDisplayName(ench);
                    if (name == null || name.isBlank()) {
                        name = formatEnchantmentName(ench);
                    }
                } else {
                    name = formatEnchantmentName(ench);
                }
            }

            // 2. Max level & Level string resolution
            int maxLevel;
            if (isEco) {
                maxLevel = ecoEnchantsHook.getMaxLevel(ench);
            } else {
                maxLevel = ench.getMaxLevel();
            }

            String levelStr;
            if (maxLevel == 1 && level == 1) {
                levelStr = "";
            } else if ("NUMBER".equalsIgnoreCase(levelStyle)) {
                levelStr = String.valueOf(level);
            } else {
                levelStr = toRoman(level);
            }

            // 3. Rarity resolution
            String rarity = "";
            if (isEco) {
                String r = ecoEnchantsHook.getRarityDisplayName(ench);
                if (r != null) {
                    rarity = r;
                }
            }

            // 4. Line template substitution
            String line = selectedTpl;
            if (startsWithColorCode(name)) {
                line = LEADING_COLOR_BEFORE_NAME.matcher(line).replaceFirst("");
            }
            if (levelStr.isEmpty()) {
                line = line.replaceAll("(?i)(?:[&§][0-9a-fk-or]|<[^>]+>)?\\s*\\[\\s*[%#]level[%#]\\s*\\]", "");
                line = line.replaceAll("(?i)(?:[&§][0-9a-fk-or]|<[^>]+>)?\\s*\\(\\s*[%#]level[%#]\\s*\\)", "");
                line = line.replaceAll("(?i)(?:[&§][0-9a-fk-or]|<[^>]+>)?\\s*<\\s*[%#]level[%#]\\s*>", "");
                line = line.replaceAll("(?i)(?:[&§][0-9a-fk-or]|<[^>]+>)?\\s*[%#]level[%#]", "");
            } else {
                line = line.replace("%level%", levelStr).replace("#level#", levelStr);
            }

            line = line.replace("%name%", name).replace("#name#", name)
                    .replace("%raw-level%", String.valueOf(level)).replace("#raw-level#", String.valueOf(level))
                    .replace("%symbol%", symbol).replace("#symbol#", symbol)
                    .replace("%rarity%", rarity).replace("#rarity#", rarity);

            if (!symbol.isEmpty() && !selectedTpl.contains("%symbol%") && !selectedTpl.contains("#symbol#")) {
                if (!line.startsWith(symbol)) {
                    line = symbol + line;
                }
            }

            // Strip trailing color codes if any remain dangling at the end
            line = line.replaceAll("(?i)(?:[&§]x(?:[&§][0-9a-fA-F]){6}|[&§]#[0-9a-fA-F]{6}|[&§][0-9a-fk-or]|<[^>]+>|\\{#[0-9a-fA-F]{6}\\})+$", "");
            line = line.replaceAll(" {2,}", " ").stripTrailing();
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
        if (key.equalsIgnoreCase("binding_curse")) return "Curse of Binding";
        if (key.equalsIgnoreCase("vanishing_curse")) return "Curse of Vanishing";
        if (key.equalsIgnoreCase("sweeping")) return "Sweeping Edge";
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

    public LoreFormat getFormat(ItemDefinition def) {
        if (def == null) {
            return globalEnchantmentFormat != null ? globalEnchantmentFormat : new LoreFormat();
        }
        String formatId = def.getLoreFormat() != null ? def.getLoreFormat().toLowerCase(Locale.ROOT) : "default";
        LoreFormat format = formats.getOrDefault(formatId, formats.get("default"));
        if (format == null) {
            format = globalEnchantmentFormat != null ? globalEnchantmentFormat : new LoreFormat();
        }
        return format;
    }

    public int computeEnchantmentHash(ItemDefinition def, Map<org.bukkit.enchantments.Enchantment, Integer> activeEnchants) {
        LoreFormat format = getFormat(def);
        int enchantsHash = (activeEnchants != null) ? activeEnchants.hashCode() : 0;

        String tpl = format != null && format.getEnchantmentFormat() != null ? format.getEnchantmentFormat() : "";
        String curseTpl = format != null && format.getEnchantmentCurseFormat() != null ? format.getEnchantmentCurseFormat() : "";
        String symbol = format != null && format.getEnchantmentSymbol() != null ? format.getEnchantmentSymbol() : "";
        String levelStyle = format != null && format.getEnchantmentLevelStyle() != null ? format.getEnchantmentLevelStyle() : "";
        String order = format != null && format.getEnchantmentOrder() != null ? format.getEnchantmentOrder() : "";
        int customNamesHash = (format != null && format.getEnchantmentCustomNames() != null) ? format.getEnchantmentCustomNames().hashCode() : 0;

        return Objects.hash(enchantsHash, tpl, curseTpl, symbol, levelStyle, order, customNamesHash);
    }

    private static boolean matchesEnchantmentPattern(String line, String name) {
        if (line == null || line.isBlank() || name == null || name.isBlank()) {
            return false;
        }
        String lowerLine = line.toLowerCase(Locale.ROOT);
        String lowerName = name.toLowerCase(Locale.ROOT);

        int idx = lowerLine.indexOf(lowerName);
        if (idx == -1) {
            return false;
        }

        // Boundary check before name
        if (idx > 0) {
            char prev = lowerLine.charAt(idx - 1);
            if (Character.isLetterOrDigit(prev)) {
                return false;
            }
        }

        // Check after name
        int endIdx = idx + lowerName.length();
        if (endIdx >= lowerLine.length()) {
            return true;
        }

        char next = lowerLine.charAt(endIdx);
        if (Character.isLetterOrDigit(next)) {
            return false;
        }

        String rest = lowerLine.substring(endIdx).trim();
        if (rest.isEmpty()) {
            return true;
        }

        return rest.matches("(?i)^(?:[ivxlcdm]+|[0-9]+|\\([ivxlcdm0-9]+\\)|\\[[ivxlcdm0-9]+\\]|:.*|-.*)");
    }

    public boolean isEnchantmentLine(String plainLine, Map<org.bukkit.enchantments.Enchantment, Integer> activeEnchants, ItemDefinition def) {
        if (plainLine == null || plainLine.isBlank()) return false;
        String trimmed = plainLine.trim();

        // 1. Exclude bar separators
        if (trimmed.contains("----") || trimmed.contains("━━━━") || trimmed.equals("---")) {
            return false;
        }

        // 2. Exclude sockets
        if (trimmed.contains("Socket") || trimmed.contains("socket") || trimmed.contains("◆") || trimmed.contains("◇")) {
            return false;
        }

        // 3. Exclude requirements
        String lowerTrimmed = trimmed.toLowerCase(Locale.ROOT);
        if (lowerTrimmed.contains("requires") || lowerTrimmed.contains("yêu cầu")
                || lowerTrimmed.contains("class:") || lowerTrimmed.contains("auraskill:")) {
            return false;
        }

        // 4. Exclude set lore
        if (def != null && def.getItemSet() != null && !def.getItemSet().isEmpty()) {
            if (lowerTrimmed.contains("set:") || lowerTrimmed.contains("bộ:") || lowerTrimmed.contains(def.getItemSet().toLowerCase(Locale.ROOT))) {
                return false;
            }
        }

        // 5. Exclude definition lore lines
        if (def != null && def.getLore() != null) {
            for (String dLine : def.getLore()) {
                if (dLine != null && !dLine.isBlank()) {
                    String strippedDLine = TextUtility.stripColor(dLine).trim();
                    if (!strippedDLine.isEmpty() && strippedDLine.equalsIgnoreCase(trimmed)) {
                        return false;
                    }
                }
            }
        }

        // 6. Check against active enchants and known enchantment names
        LoreFormat format = getFormat(def);
        Map<String, String> customNames = (format != null && format.getEnchantmentCustomNames() != null)
                ? format.getEnchantmentCustomNames() : Collections.emptyMap();

        String cleanLine = trimmed.replaceAll("^[✦★◆◇■•\\s\\[\\]]+", "").trim();

        if (activeEnchants != null) {
            for (org.bukkit.enchantments.Enchantment ench : activeEnchants.keySet()) {
                if (ench == null) continue;
                String enchName = formatEnchantmentName(ench);
                if (matchesEnchantmentPattern(cleanLine, enchName)) return true;

                String rawKey = ench.getKey().getKey();
                if (matchesEnchantmentPattern(cleanLine, rawKey)) return true;

                if (ecoEnchantsHook != null && ecoEnchantsHook.isEcoEnchant(ench)) {
                    String ecoDisp = ecoEnchantsHook.getDisplayName(ench);
                    if (ecoDisp != null && matchesEnchantmentPattern(cleanLine, TextUtility.stripColor(ecoDisp).trim())) {
                        return true;
                    }
                }

                for (String customVal : customNames.values()) {
                    if (customVal != null && !customVal.isBlank()) {
                        String strippedCustom = TextUtility.stripColor(customVal).trim();
                        if (matchesEnchantmentPattern(cleanLine, strippedCustom)) return true;
                    }
                }
            }
        }

        for (String customVal : customNames.values()) {
            if (customVal != null && !customVal.isBlank()) {
                String strippedCustom = TextUtility.stripColor(customVal).trim();
                if (matchesEnchantmentPattern(cleanLine, strippedCustom)) return true;
            }
        }

        for (String known : KNOWN_ENCHANTMENT_NAMES) {
            if (matchesEnchantmentPattern(cleanLine, known)) return true;
        }

        if (formats != null) {
            for (LoreFormat lf : formats.values()) {
                if (lf != null && lf.getEnchantmentCustomNames() != null) {
                    for (String customVal : lf.getEnchantmentCustomNames().values()) {
                        if (customVal != null && !customVal.isBlank()) {
                            String strippedCustom = TextUtility.stripColor(customVal).trim();
                            if (matchesEnchantmentPattern(cleanLine, strippedCustom)) return true;
                        }
                    }
                }
            }
        }

        if (ecoEnchantsHook != null && ecoEnchantsHook.isAvailable()) {
            try {
                for (EcoEnchantsHook.EcoEnchantCandidate cand : ecoEnchantsHook.getAllCandidates()) {
                    if (cand == null) continue;
                    if (matchesEnchantmentPattern(cleanLine, cand.getId())) return true;
                    if (cand.getRawDisplayName() != null && matchesEnchantmentPattern(cleanLine, TextUtility.stripColor(cand.getRawDisplayName()).trim())) return true;
                }
            } catch (Throwable ignored) {}
        }

        try {
            for (org.bukkit.enchantments.Enchantment ench : org.bukkit.Registry.ENCHANTMENT) {
                if (ench == null) continue;
                String rawKey = ench.getKey().getKey();
                if (matchesEnchantmentPattern(cleanLine, rawKey)) return true;
                String enchName = formatEnchantmentName(ench);
                if (matchesEnchantmentPattern(cleanLine, enchName)) return true;
            }
        } catch (Throwable ignored) {
            try {
                for (org.bukkit.enchantments.Enchantment ench : org.bukkit.enchantments.Enchantment.values()) {
                    if (ench == null) continue;
                    String rawKey = ench.getKey().getKey();
                    if (matchesEnchantmentPattern(cleanLine, rawKey)) return true;
                    String enchName = formatEnchantmentName(ench);
                    if (matchesEnchantmentPattern(cleanLine, enchName)) return true;
                }
            } catch (Throwable ignored2) {}
        }

        // Generic pattern fallback: line ending with roman numeral or level digit
        if (cleanLine.matches("(?i)^[A-Za-zÀ-ỹ\\s'-]+\\s+(?:[IVXLCDMivxlcdm]+|[0-9]+|\\([IVXLCDM0-9ivxlcdm]+\\)|\\[[IVXLCDM0-9ivxlcdm]+\\])$")) {
            return true;
        }

        return false;
    }

    public List<Component> updateEnchantmentLinesInLore(List<Component> currentLore, ItemDefinition def,
                                                        Map<org.bukkit.enchantments.Enchantment, Integer> activeEnchants) {
        LoreFormat format = getFormat(def);
        List<String> rawNewEnch = generateEnchantmentLines(def, format, activeEnchants);
        List<Component> newComps = TextUtility.formatLore(rawNewEnch);

        if (currentLore == null || currentLore.isEmpty()) {
            return newComps;
        }

        int firstIdx = -1;
        int lastIdx = -1;
        for (int i = 0; i < currentLore.size(); i++) {
            String plain = TextUtility.stripColor(LegacyComponentSerializer.legacySection().serialize(currentLore.get(i)));
            if (isEnchantmentLine(plain, activeEnchants, def)) {
                if (firstIdx == -1) firstIdx = i;
                lastIdx = i;
            }
        }

        List<Component> updated = new ArrayList<>(currentLore);

        if (firstIdx != -1) {
            for (int i = lastIdx; i >= firstIdx; i--) {
                updated.remove(i);
            }
            updated.addAll(firstIdx, newComps);
            return updated;
        } else {
            if (newComps.isEmpty()) {
                return currentLore;
            }
            int insertIdx = -1;
            for (int i = 0; i < updated.size(); i++) {
                String plain = TextUtility.stripColor(LegacyComponentSerializer.legacySection().serialize(updated.get(i))).toLowerCase(Locale.ROOT);
                if (plain.contains("socket") || plain.contains("◆") || plain.contains("◇")
                        || plain.contains("requires") || plain.contains("yêu cầu")
                        || plain.contains("set:") || plain.contains("bộ:")) {
                    insertIdx = i;
                    break;
                }
            }

            if (insertIdx != -1) {
                updated.addAll(insertIdx, newComps);
            } else if (!updated.isEmpty()) {
                String lastPlain = TextUtility.stripColor(LegacyComponentSerializer.legacySection().serialize(updated.get(updated.size() - 1)));
                if (lastPlain.contains("----") || lastPlain.contains("━━━━")) {
                    updated.addAll(updated.size() - 1, newComps);
                } else {
                    updated.addAll(newComps);
                }
            } else {
                updated.addAll(newComps);
            }
            return updated;
        }
    }

    public List<Component> mergeCustomLore(List<Component> currentLore, List<Component> baseLore,
                                           ItemDefinition def, Map<org.bukkit.enchantments.Enchantment, Integer> activeEnchants) {
        if (currentLore == null || currentLore.isEmpty()) return baseLore != null ? baseLore : Collections.emptyList();
        if (baseLore == null || baseLore.isEmpty()) return currentLore;

        Set<String> baseSerialized = new HashSet<>();
        for (Component c : baseLore) {
            baseSerialized.add(TextUtility.stripColor(LegacyComponentSerializer.legacySection().serialize(c)).trim());
        }

        List<Component> customLines = new ArrayList<>();
        for (Component c : currentLore) {
            String plain = TextUtility.stripColor(LegacyComponentSerializer.legacySection().serialize(c)).trim();
            if (plain.isEmpty()) continue;
            if (plain.contains("----") || plain.contains("━━━━")) continue;
            if (isEnchantmentLine(plain, activeEnchants, def)) continue;
            if (baseSerialized.contains(plain)) continue;

            customLines.add(c);
        }

        if (customLines.isEmpty()) {
            return baseLore;
        }

        List<Component> merged = new ArrayList<>(baseLore);
        if (!merged.isEmpty()) {
            String lastPlain = TextUtility.stripColor(LegacyComponentSerializer.legacySection().serialize(merged.get(merged.size() - 1)));
            if (lastPlain.contains("----") || lastPlain.contains("━━━━")) {
                merged.addAll(merged.size() - 1, customLines);
                return merged;
            }
        }
        merged.addAll(customLines);
        return merged;
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

    /**
     * Resolves the effective enchantments for an item, filtering out cosmetic dummy glow
     * (e.g. LUCK_OF_THE_SEA 1 when def.isGlow() is true and item definition does not naturally possess it).
     */
    public Map<org.bukkit.enchantments.Enchantment, Integer> getEffectiveEnchantments(ItemMeta meta, ItemDefinition def) {
        if (meta == null) return Collections.emptyMap();
        Map<org.bukkit.enchantments.Enchantment, Integer> enchants = new LinkedHashMap<>(meta.getEnchants());
        if (meta instanceof org.bukkit.inventory.meta.EnchantmentStorageMeta storageMeta) {
            enchants.putAll(storageMeta.getStoredEnchants());
        }
        if (def != null && def.isGlow()) {
            boolean defHasLuck = def.getEnchantments() != null && def.getEnchantments().containsKey(org.bukkit.enchantments.Enchantment.LUCK_OF_THE_SEA);
            if (!defHasLuck && Integer.valueOf(1).equals(enchants.get(org.bukkit.enchantments.Enchantment.LUCK_OF_THE_SEA))) {
                enchants.remove(org.bukkit.enchantments.Enchantment.LUCK_OF_THE_SEA);
            }
        }
        return enchants;
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

        Map<org.bukkit.enchantments.Enchantment, Integer> activeEnchants = getEffectiveEnchantments(meta, def);

        List<Component> currentLore = meta.lore();
        if (hasEnchantmentsPlaceholder(def)) {
            String socketsRaw = pdcManager.getSocketsData(item);
            List<String> sockets = ItemBuilder.deserializeSockets(socketsRaw);
            long expire = pdcManager.getExpireTime(item);
            String oldSetState = pdcManager.getSetState(item);
            boolean isBonusActive = "active".equalsIgnoreCase(oldSetState);
            List<String> rawLore = buildLore(def, sockets, expire, activeEnchants, isBonusActive, null);
            List<Component> baseLore = TextUtility.formatLore(rawLore);
            meta.lore(mergeCustomLore(currentLore, baseLore, def, activeEnchants));
        } else if (currentLore != null && !currentLore.isEmpty()) {
            meta.lore(updateEnchantmentLinesInLore(currentLore, def, activeEnchants));
        } else {
            String socketsRaw = pdcManager.getSocketsData(item);
            List<String> sockets = ItemBuilder.deserializeSockets(socketsRaw);
            long expire = pdcManager.getExpireTime(item);
            String oldSetState = pdcManager.getSetState(item);
            boolean isBonusActive = "active".equalsIgnoreCase(oldSetState);
            List<String> rawLore = buildLore(def, sockets, expire, activeEnchants, isBonusActive, null);
            meta.lore(TextUtility.formatLore(rawLore));
        }

        // When lore displays enchantments, suppress vanilla client tooltip so client does not duplicate
        boolean hideTooltip = def.isHideEnchantments() || hasEnchantmentsPlaceholder(def);
        if (!hideTooltip && plugin != null && plugin.getConfig() != null) {
            hideTooltip = plugin.getConfig().getBoolean("enchantments.hide-vanilla-tooltip", true);
        }
        if (hideTooltip) {
            meta.addItemFlags(org.bukkit.inventory.ItemFlag.HIDE_ENCHANTS);
        }

        pdcManager.setEnchantHash(meta, computeEnchantmentHash(def, activeEnchants));
        item.setItemMeta(meta);
        return true;
    }

    public boolean checkAndSynchronizeEnchantments(ItemStack item) {
        if (item == null || item.getType().isAir()) return false;
        if (pdcManager == null || !pdcManager.isRoyItem(item)) return false;
        if (registry == null) return false;

        ItemMeta meta = item.getItemMeta();
        if (meta == null) return false;

        ItemType type = pdcManager.getType(item);
        String id = pdcManager.getId(item);
        ItemDefinition def = registry.get(type, id);
        if (def == null) return false;

        Map<org.bukkit.enchantments.Enchantment, Integer> activeEnchants = getEffectiveEnchantments(meta, def);
        int currentHash = computeEnchantmentHash(def, activeEnchants);
        int storedHash = pdcManager.getEnchantHash(meta);

        if (currentHash != storedHash) {
            return synchronizeItemEnchantments(item);
        }
        return false;
    }

    /**
     * Synchronizes dynamic lore for a set item according to the player's active set bonus status.
     * Updates active/inactive lore lines, tags new state in PDC, and applies changes.
     */
    public boolean synchronizeItemSetLore(ItemStack item, boolean isBonusActive, Player player) {
        if (item == null || item.getType().isAir()) return false;
        if (pdcManager == null || !pdcManager.isRoyItem(item)) return false;
        if (registry == null) return false;

        ItemType type = pdcManager.getType(item);
        String id = pdcManager.getId(item);
        ItemDefinition def = registry.get(type, id);
        if (def == null || def.getItemSet() == null || def.getItemSet().isEmpty()) return false;

        ItemMeta meta = item.getItemMeta();
        if (meta == null) return false;

        Map<org.bukkit.enchantments.Enchantment, Integer> activeEnchants = getEffectiveEnchantments(meta, def);

        String socketsRaw = pdcManager.getSocketsData(item);
        List<String> sockets = ItemBuilder.deserializeSockets(socketsRaw);
        long expire = pdcManager.getExpireTime(item);

        List<String> rawLore = buildLore(def, sockets, expire, activeEnchants, isBonusActive, player);
        meta.lore(TextUtility.formatLore(rawLore));

        boolean hideTooltip = def.isHideEnchantments() || hasEnchantmentsPlaceholder(def);
        if (!hideTooltip && plugin != null && plugin.getConfig() != null) {
            hideTooltip = plugin.getConfig().getBoolean("enchantments.hide-vanilla-tooltip", true);
        }
        if (hideTooltip) {
            meta.addItemFlags(org.bukkit.inventory.ItemFlag.HIDE_ENCHANTS);
        }

        pdcManager.setSetState(meta, isBonusActive ? "active" : "inactive");
        pdcManager.setEnchantHash(meta, computeEnchantmentHash(def, activeEnchants));
        item.setItemMeta(meta);
        return true;
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
