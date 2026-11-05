package com.github.orenaidaucac.royItems.lore;

import com.github.orenaidaucac.royItems.stat.StatModifier;
import com.github.orenaidaucac.royItems.stat.StatType;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;

import java.text.DecimalFormat;
import java.util.*;

/**
 * Encapsulates dynamic lore formatting rules, templates, and per-stat overrides.
 * Fully configurable without hardcoded text or symbols.
 */
public class LoreFormat {
    private String id;
    private List<String> format = new ArrayList<>();
    private String bar = "&8&m------------------------";

    // Stat formatting templates
    private String posStat = "&7#name#: &a+#value#";
    private String negStat = "&7#name#: &c-#value#";
    private String posPctStat = "&7#name#: &a+#value#%";
    private String negPctStat = "&7#name#: &c-#value#%";

    // Sockets formatting
    private String socketEmpty = "&8[&7◇&8] &7Empty Socket (%type%)";
    private String socketFilled = "&8[&a◆&8] &f%gem%";

    // Requirements formatting
    private String reqLevel = "&cRequires Level: &f%level%";
    private String reqPermission = "&cRequires Permission: &f%permission%";
    private String reqClass = "&cClass: &f%class%";
    private String reqClassWithLevel = "&cClass: &f%class% (Lv.%level%)";
    private String reqAuraSkill = "&cAuraSkill: &f%skill% Lv.%level%";

    // Enchantments formatting
    private String enchantmentFormat = "&7%name% %level%";
    private String enchantmentCurseFormat = "&c%name% %level%";
    private String enchantmentSymbol = "";
    private String enchantmentLevelStyle = "ROMAN";
    private String enchantmentOrder = "VANILLA_FIRST";
    private Map<String, String> enchantmentCustomNames = new HashMap<>();

    // Durability formatting
    private String durabilityUnbreakable = "&aUnbreakable";
    private String durabilityFormat = "%current%/%max%";

    private final Map<String, StatDisplayConfig> customStats = new HashMap<>();

    public static class StatDisplayConfig {
        private final String name;
        private final String symbol;
        private final String positive;
        private final String negative;
        private final String percentagePositive;
        private final String percentageNegative;

        public StatDisplayConfig(String name, String symbol, String positive, String negative, String percentagePositive, String percentageNegative) {
            this.name = name;
            this.symbol = symbol != null ? symbol : "";
            this.positive = positive;
            this.negative = negative;
            this.percentagePositive = percentagePositive;
            this.percentageNegative = percentageNegative;
        }

        public StatDisplayConfig(String name, String positive, String negative, String percentagePositive, String percentageNegative) {
            this(name, "", positive, negative, percentagePositive, percentageNegative);
        }

        public String getName() { return name; }
        public String getSymbol() { return symbol; }
        public String getPositive() { return positive; }
        public String getNegative() { return negative; }
        public String getPercentagePositive() { return percentagePositive; }
        public String getPercentageNegative() { return percentageNegative; }
    }

    public LoreFormat() {}

    public LoreFormat(String id, List<String> format, String bar, String posStat, String negStat, String posPctStat, String negPctStat) {
        this.id = id;
        this.format = format;
        this.bar = bar;
        this.posStat = posStat;
        this.negStat = negStat;
        this.posPctStat = posPctStat;
        this.negPctStat = negPctStat;
    }

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public List<String> getFormat() { return format; }
    public void setFormat(List<String> format) { this.format = format; }

    public String getBar() { return bar; }
    public void setBar(String bar) { this.bar = bar; }

    public String getPosStat() { return posStat; }
    public void setPosStat(String posStat) { this.posStat = posStat; }

    public String getNegStat() { return negStat; }
    public void setNegStat(String negStat) { this.negStat = negStat; }

    public String getPosPctStat() { return posPctStat; }
    public void setPosPctStat(String posPctStat) { this.posPctStat = posPctStat; }

    public String getNegPctStat() { return negPctStat; }
    public void setNegPctStat(String negPctStat) { this.negPctStat = negPctStat; }

    public String getSocketEmpty() { return socketEmpty; }
    public void setSocketEmpty(String socketEmpty) { this.socketEmpty = socketEmpty; }

    public String getSocketFilled() { return socketFilled; }
    public void setSocketFilled(String socketFilled) { this.socketFilled = socketFilled; }

    public String getReqLevel() { return reqLevel; }
    public void setReqLevel(String reqLevel) { this.reqLevel = reqLevel; }

    public String getReqPermission() { return reqPermission; }
    public void setReqPermission(String reqPermission) { this.reqPermission = reqPermission; }

    public String getReqClass() { return reqClass; }
    public void setReqClass(String reqClass) { this.reqClass = reqClass; }

    public String getReqClassWithLevel() { return reqClassWithLevel; }
    public void setReqClassWithLevel(String reqClassWithLevel) { this.reqClassWithLevel = reqClassWithLevel; }

    public String getReqAuraSkill() { return reqAuraSkill; }
    public void setReqAuraSkill(String reqAuraSkill) { this.reqAuraSkill = reqAuraSkill; }

    public String getEnchantmentFormat() { return enchantmentFormat; }
    public void setEnchantmentFormat(String enchantmentFormat) { this.enchantmentFormat = enchantmentFormat; }

    public String getEnchantmentCurseFormat() { return enchantmentCurseFormat; }
    public void setEnchantmentCurseFormat(String enchantmentCurseFormat) { this.enchantmentCurseFormat = enchantmentCurseFormat; }

    public String getEnchantmentSymbol() { return enchantmentSymbol; }
    public void setEnchantmentSymbol(String enchantmentSymbol) { this.enchantmentSymbol = enchantmentSymbol; }

    public String getEnchantmentLevelStyle() { return enchantmentLevelStyle; }
    public void setEnchantmentLevelStyle(String enchantmentLevelStyle) { this.enchantmentLevelStyle = enchantmentLevelStyle; }

    public String getEnchantmentOrder() { return enchantmentOrder; }
    public void setEnchantmentOrder(String enchantmentOrder) { this.enchantmentOrder = enchantmentOrder; }

    public Map<String, String> getEnchantmentCustomNames() { return enchantmentCustomNames; }
    public void setEnchantmentCustomNames(Map<String, String> enchantmentCustomNames) { this.enchantmentCustomNames = enchantmentCustomNames; }

    public String getDurabilityUnbreakable() { return durabilityUnbreakable; }
    public void setDurabilityUnbreakable(String durabilityUnbreakable) { this.durabilityUnbreakable = durabilityUnbreakable; }

    public String getDurabilityFormat() { return durabilityFormat; }
    public void setDurabilityFormat(String durabilityFormat) { this.durabilityFormat = durabilityFormat; }

    public void addCustomStat(String key, StatDisplayConfig cfg) {
        if (key != null && cfg != null) {
            customStats.put(key.toLowerCase().replace('_', '-'), cfg);
        }
    }

    public StatDisplayConfig getCustomStat(String key) {
        if (key == null || key.isEmpty()) return null;

        Set<String> candidates = new LinkedHashSet<>();
        String normalized = key.toLowerCase().replace('_', '-').trim();
        candidates.add(normalized);
        candidates.add(normalized.replace('.', '-'));
        candidates.add(normalized.replace('-', '.'));
        candidates.add(key.toLowerCase().trim());

        // Prefix variations: vanilla-, generic., player.
        for (String c : new ArrayList<>(candidates)) {
            if (c.startsWith("vanilla-")) {
                candidates.add(c.substring("vanilla-".length()));
            } else {
                candidates.add("vanilla-" + c);
            }
            if (c.startsWith("generic.")) {
                candidates.add(c.substring("generic.".length()));
            } else if (c.startsWith("generic-")) {
                candidates.add(c.substring("generic-".length()));
            } else {
                candidates.add("generic." + c);
                candidates.add("generic-" + c);
            }
            if (c.startsWith("player.")) {
                candidates.add(c.substring("player.".length()));
            } else if (c.startsWith("player-")) {
                candidates.add(c.substring("player-".length()));
            }
        }

        // Mapped StatType
        StatType statType = StatType.fromKey(normalized);
        if (statType != null) {
            candidates.add(statType.getKey().toLowerCase());
            if (statType.getVanillaAttributeKey() != null) {
                String vk = statType.getVanillaAttributeKey().toLowerCase().replace('_', '-');
                candidates.add(vk);
                candidates.add("generic." + vk);
                candidates.add("generic-" + vk);
                candidates.add("vanilla-" + vk);
            }
        }

        // Direct lookup without recursion
        for (String candidate : candidates) {
            StatDisplayConfig cfg = customStats.get(candidate);
            if (cfg != null) {
                return cfg;
            }
        }

        return null;
    }

    public Map<String, StatDisplayConfig> getCustomStats() {
        return Collections.unmodifiableMap(customStats);
    }

    /**
     * Formats an entire stat line (e.g. for #stats# or a standalone #attack-damage#).
     */
    public String formatStatLine(String statKey, String defaultDisplayName, StatModifier mod, DecimalFormat df) {
        if (mod == null || mod.isEmpty()) return null;

        String key = statKey != null ? statKey.toLowerCase().replace('_', '-') : "";
        StatDisplayConfig custom = getCustomStat(key);

        String name = (custom != null && custom.getName() != null && !custom.getName().isEmpty())
                ? custom.getName()
                : (defaultDisplayName != null && !defaultDisplayName.isEmpty() ? defaultDisplayName : statKey);

        String symbol = (custom != null && custom.getSymbol() != null) ? custom.getSymbol() : "";

        boolean hasFlat = Math.abs(mod.getFlat()) > 0.00001;
        boolean hasPct = Math.abs(mod.getPercent()) > 0.00001;

        if (hasFlat && !hasPct) {
            double val = mod.getFlat();
            String tpl;
            if (val >= 0) {
                tpl = (custom != null && custom.getPositive() != null) ? custom.getPositive() : posStat;
            } else {
                if (custom != null && custom.getNegative() != null) {
                    tpl = custom.getNegative();
                } else if (custom != null && custom.getPositive() != null) {
                    tpl = adaptToNegative(custom.getPositive());
                } else {
                    tpl = negStat;
                }
            }
            return applyStatPlaceholders(tpl, statKey, name, symbol, df.format(Math.abs(val)), val);
        } else if (!hasFlat && hasPct) {
            double pctVal = mod.getPercentValue();
            String tpl;
            if (pctVal >= 0) {
                if (custom != null && custom.getPercentagePositive() != null) {
                    tpl = custom.getPercentagePositive();
                } else if (custom != null && custom.getPositive() != null) {
                    tpl = adaptToPercentage(custom.getPositive());
                } else {
                    tpl = posPctStat;
                }
            } else {
                if (custom != null && custom.getPercentageNegative() != null) {
                    tpl = custom.getPercentageNegative();
                } else if (custom != null && custom.getNegative() != null) {
                    tpl = adaptToPercentage(custom.getNegative());
                } else if (custom != null && custom.getPositive() != null) {
                    tpl = adaptToNegative(adaptToPercentage(custom.getPositive()));
                } else {
                    tpl = negPctStat;
                }
            }
            return applyStatPlaceholders(tpl, statKey, name, symbol, df.format(Math.abs(pctVal)), pctVal);
        } else {
            // Both flat and percent present: e.g. +10 (+5%)
            double val = mod.getFlat();
            double pctVal = mod.getPercentValue();
            String tpl = (val >= 0)
                    ? (custom != null && custom.getPositive() != null ? custom.getPositive() : posStat)
                    : (custom != null && custom.getNegative() != null ? custom.getNegative() : (custom != null && custom.getPositive() != null ? adaptToNegative(custom.getPositive()) : negStat));
            String combinedVal = df.format(Math.abs(val)) + " (" + (pctVal >= 0 ? "+" : "") + df.format(pctVal) + "%)";
            return applyStatPlaceholders(tpl, statKey, name, symbol, combinedVal, val);
        }
    }

    /**
     * Formats just the raw stat value with % if percentage (e.g. "15", "20%", "0.5%").
     */
    public String formatStatValue(StatModifier mod, DecimalFormat df) {
        return formatStatValue(null, mod, df);
    }

    public String formatStatValue(String key, StatModifier mod, DecimalFormat df) {
        if (mod == null || mod.isEmpty()) return null;

        if (key != null && (key.equalsIgnoreCase("mine-radius") || key.equalsIgnoreCase("mine_radius") || key.equalsIgnoreCase("mineradius"))) {
            return StatType.formatMineArea(mod.getFlat());
        }

        boolean hasFlat = Math.abs(mod.getFlat()) > 0.00001;
        boolean hasPct = Math.abs(mod.getPercent()) > 0.00001;

        if (hasFlat && !hasPct) {
            return df.format(mod.getFlat());
        } else if (!hasFlat && hasPct) {
            return df.format(mod.getPercentValue()) + "%";
        } else {
            double pctVal = mod.getPercentValue();
            return df.format(mod.getFlat()) + " (" + (pctVal >= 0 ? "+" : "") + df.format(pctVal) + "%)";
        }
    }

    public static String applyStatPlaceholders(String template, String key, String name, String symbol, String formattedVal, double rawVal) {
        if (template == null) return "";
        String s = template;

        String areaStr = StatType.formatMineArea(rawVal);
        int radiusVal = StatType.getMineRadiusBlocks(rawVal);
        String finalVal = formattedVal;
        if (key != null && (key.equalsIgnoreCase("mine-radius") || key.equalsIgnoreCase("mine_radius") || key.equalsIgnoreCase("mineradius"))) {
            finalVal = areaStr;
        }

        if (symbol == null || symbol.isEmpty()) {
            s = s.replace("%symbol% ", "").replace("#symbol# ", "").replace("%symbol%", "").replace("#symbol#", "");
        } else {
            s = s.replace("%symbol%", symbol).replace("#symbol#", symbol);
        }

        s = s.replace("%name%", name != null ? name : "")
                .replace("#name#", name != null ? name : "")
                .replace("%value%", finalVal)
                .replace("#value#", finalVal)
                .replace("%area%", areaStr)
                .replace("#area#", areaStr)
                .replace("%radius%", String.valueOf(radiusVal))
                .replace("#radius#", String.valueOf(radiusVal))
                .replace("%raw-value%", String.valueOf(rawVal))
                .replace("#raw-value#", String.valueOf(rawVal))
                .replace("%key%", key != null ? key : "")
                .replace("#key#", key != null ? key : "");

        return s;
    }

    private static String adaptToPercentage(String tpl) {
        if (tpl == null) return null;
        if (tpl.contains("%value%%") || tpl.contains("#value#%") || tpl.contains("%")) {
            return tpl;
        }
        return tpl.replace("%value%", "%value%%").replace("#value#", "#value#%");
    }

    private static String adaptToNegative(String tpl) {
        if (tpl == null) return null;
        return tpl.replace("+%value%", "-%value%").replace("+#value#", "-#value#")
                .replace("+ %value%", "- %value%").replace("+ #value#", "- #value#");
    }

    public static LoreFormat fromConfig(String id, FileConfiguration config) {
        LoreFormat lf = new LoreFormat();
        lf.setId(id);
        lf.setFormat(config.getStringList("format"));
        lf.setBar(config.getString("bar", "&8&m------------------------"));

        ConfigurationSection statSec = config.getConfigurationSection("stat-format");
        if (statSec != null) {
            lf.setPosStat(statSec.getString("positive", statSec.getString("format", statSec.getString("positive-format", "&7#name#: &a+#value#"))));
            lf.setNegStat(statSec.getString("negative", statSec.getString("negative-format", "&7#name#: &c-#value#")));
            lf.setPosPctStat(statSec.getString("percentage-positive", statSec.getString("percent-format", statSec.getString("percentage-format", statSec.getString("percent-positive", "&7#name#: &a+#value#%")))));
            lf.setNegPctStat(statSec.getString("percentage-negative", statSec.getString("percent-negative-format", statSec.getString("percent-negative", "&7#name#: &c-#value#%"))));

            // Parse custom overrides under stat-format.custom
            ConfigurationSection customSec = statSec.getConfigurationSection("custom");
            if (customSec != null) {
                parseCustomStatsSection(lf, customSec);
            }

            // Also parse direct keys under stat-format (e.g. stat-format.attack-damage)
            for (String key : statSec.getKeys(false)) {
                if (key.equalsIgnoreCase("positive") || key.equalsIgnoreCase("negative")
                        || key.equalsIgnoreCase("percentage-positive") || key.equalsIgnoreCase("percentage-negative")
                        || key.equalsIgnoreCase("format") || key.equalsIgnoreCase("positive-format")
                        || key.equalsIgnoreCase("negative-format") || key.equalsIgnoreCase("percent-format")
                        || key.equalsIgnoreCase("percentage-format") || key.equalsIgnoreCase("percent-negative-format")
                        || key.equalsIgnoreCase("percent-negative") || key.equalsIgnoreCase("percent-positive")
                        || key.equalsIgnoreCase("custom")) {
                    continue;
                }
                if (statSec.isConfigurationSection(key)) {
                    parseStatConfigSection(lf, key, statSec.getConfigurationSection(key));
                } else if (statSec.isString(key)) {
                    parseStatConfigString(lf, key, statSec.getString(key));
                }
            }
        }

        // Parse root 'stats:' section if present
        ConfigurationSection rootCustomSec = config.getConfigurationSection("stats");
        if (rootCustomSec != null) {
            parseCustomStatsSection(lf, rootCustomSec);
        }

        // Sockets format
        ConfigurationSection socketSec = config.getConfigurationSection("sockets-format");
        if (socketSec != null) {
            lf.setSocketEmpty(socketSec.getString("empty", "&8[&7◇&8] &7Empty Socket (%type%)"));
            lf.setSocketFilled(socketSec.getString("filled", "&8[&a◆&8] &f%gem%"));
        }

        // Requirements format
        ConfigurationSection reqSec = config.getConfigurationSection("requirements-format");
        if (reqSec != null) {
            lf.setReqLevel(reqSec.getString("level", "&cRequires Level: &f%level%"));
            lf.setReqPermission(reqSec.getString("permission", "&cRequires Permission: &f%permission%"));
            lf.setReqClass(reqSec.getString("class", "&cClass: &f%class%"));
            lf.setReqClassWithLevel(reqSec.getString("class-with-level", "&cClass: &f%class% (Lv.%level%)"));
            lf.setReqAuraSkill(reqSec.getString("auraskill", "&cAuraSkill: &f%skill% Lv.%level%"));
        }

        // Enchantments format
        ConfigurationSection enchSec = config.getConfigurationSection("enchantments-format");
        if (enchSec != null) {
            lf.setEnchantmentFormat(enchSec.getString("format", "&7%name% %level%"));
            lf.setEnchantmentCurseFormat(enchSec.getString("curse-format", "&c%name% %level%"));
            lf.setEnchantmentSymbol(enchSec.getString("symbol", ""));
            lf.setEnchantmentLevelStyle(enchSec.getString("level-style", "ROMAN"));
            lf.setEnchantmentOrder(enchSec.getString("order", "VANILLA_FIRST"));

            ConfigurationSection customNamesSec = enchSec.getConfigurationSection("custom-names");
            if (customNamesSec != null) {
                Map<String, String> customNames = new HashMap<>();
                for (String key : customNamesSec.getKeys(false)) {
                    customNames.put(key.toLowerCase().replace('-', '_'), customNamesSec.getString(key));
                }
                lf.setEnchantmentCustomNames(customNames);
            }
        }

        // Durability format
        ConfigurationSection durSec = config.getConfigurationSection("durability-format");
        if (durSec != null) {
            lf.setDurabilityUnbreakable(durSec.getString("unbreakable", "&aUnbreakable"));
            lf.setDurabilityFormat(durSec.getString("format", "%current%/%max%"));
        }

        return lf;
    }

    private static void parseStatConfigSection(LoreFormat lf, String key, ConfigurationSection s) {
        if (s == null) return;
        String name = s.getString("display-name", s.getString("name", s.getString("display_name", s.getString("display", s.getString("title", null)))));
        String symbol = s.getString("symbol", "");

        String pos = s.getString("format", s.getString("positive", s.getString("positive-format", s.getString("pos", null))));
        String neg = s.getString("negative", s.getString("negative-format", s.getString("neg", null)));

        String posPct = s.getString("percent-format", s.getString("percentage-format", s.getString("percentage-positive", s.getString("percent-positive", s.getString("percentage", null)))));
        String negPct = s.getString("percent-negative-format", s.getString("percentage-negative", s.getString("percent-negative", s.getString("percentage-neg", null))));

        lf.addCustomStat(key, new StatDisplayConfig(name, symbol, pos, neg, posPct, negPct));
    }

    private static void parseStatConfigString(LoreFormat lf, String key, String val) {
        lf.addCustomStat(key, new StatDisplayConfig(null, "", val, val, val, val));
    }

    private static void parseCustomStatsSection(LoreFormat lf, ConfigurationSection sec) {
        if (sec == null) return;
        for (String key : sec.getKeys(false)) {
            if (sec.isConfigurationSection(key)) {
                parseStatConfigSection(lf, key, sec.getConfigurationSection(key));
            } else if (sec.isString(key)) {
                parseStatConfigString(lf, key, sec.getString(key));
            }
        }
    }
}
