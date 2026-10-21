package com.github.orenaidaucac.royItems.lore;

import com.github.orenaidaucac.royItems.stat.StatModifier;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;

import java.text.DecimalFormat;
import java.util.*;

/**
 * Encapsulates dynamic lore formatting rules, templates, and per-stat overrides.
 */
public class LoreFormat {
    private String id;
    private List<String> format = new ArrayList<>();
    private String bar = "&8&m------------------------";
    private String posStat = "&7#name#: &a+#value#";
    private String negStat = "&7#name#: &c-#value#";
    private String posPctStat = "&7#name#: &a+#value#%";
    private String negPctStat = "&7#name#: &c-#value#%";

    private final Map<String, StatDisplayConfig> customStats = new HashMap<>();

    public static class StatDisplayConfig {
        private final String name;
        private final String positive;
        private final String negative;
        private final String percentagePositive;
        private final String percentageNegative;

        public StatDisplayConfig(String name, String positive, String negative, String percentagePositive, String percentageNegative) {
            this.name = name;
            this.positive = positive;
            this.negative = negative;
            this.percentagePositive = percentagePositive;
            this.percentageNegative = percentageNegative;
        }

        public String getName() { return name; }
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

    public void addCustomStat(String key, StatDisplayConfig cfg) {
        if (key != null && cfg != null) {
            customStats.put(key.toLowerCase().replace('_', '-'), cfg);
        }
    }

    public StatDisplayConfig getCustomStat(String key) {
        if (key == null) return null;
        return customStats.get(key.toLowerCase().replace('_', '-'));
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

        String name = (custom != null && custom.getName() != null)
                ? custom.getName()
                : (defaultDisplayName != null ? defaultDisplayName : statKey);

        boolean hasFlat = Math.abs(mod.getFlat()) > 0.00001;
        boolean hasPct = Math.abs(mod.getPercent()) > 0.00001;

        if (hasFlat && !hasPct) {
            double val = mod.getFlat();
            String tpl;
            if (val >= 0) {
                tpl = (custom != null && custom.getPositive() != null) ? custom.getPositive() : posStat;
            } else {
                tpl = (custom != null && custom.getNegative() != null) ? custom.getNegative() : negStat;
            }
            return tpl.replace("#name#", name).replace("#value#", df.format(Math.abs(val)));
        } else if (!hasFlat && hasPct) {
            double pctVal = mod.getPercentValue();
            String tpl;
            if (pctVal >= 0) {
                tpl = (custom != null && custom.getPercentagePositive() != null) ? custom.getPercentagePositive() : posPctStat;
            } else {
                tpl = (custom != null && custom.getPercentageNegative() != null) ? custom.getPercentageNegative() : negPctStat;
            }
            return tpl.replace("#name#", name).replace("#value#", df.format(Math.abs(pctVal)));
        } else {
            // Both flat and percent present: e.g. +10 (+5%)
            double val = mod.getFlat();
            double pctVal = mod.getPercentValue();
            String tpl = val >= 0 ? posStat : negStat;
            String combinedVal = df.format(Math.abs(val)) + " (" + (pctVal >= 0 ? "+" : "") + df.format(pctVal) + "%)";
            return tpl.replace("#name#", name).replace("#value#", combinedVal);
        }
    }

    /**
     * Formats just the raw stat value with % if percentage (e.g. "15", "20%", "0.5%").
     */
    public String formatStatValue(StatModifier mod, DecimalFormat df) {
        if (mod == null || mod.isEmpty()) return null;

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

    public static LoreFormat fromConfig(String id, FileConfiguration config) {
        LoreFormat lf = new LoreFormat();
        lf.setId(id);
        lf.setFormat(config.getStringList("format"));
        lf.setBar(config.getString("bar", "&8&m------------------------"));

        ConfigurationSection statSec = config.getConfigurationSection("stat-format");
        if (statSec != null) {
            lf.setPosStat(statSec.getString("positive", "&7#name#: &a+#value#"));
            lf.setNegStat(statSec.getString("negative", "&7#name#: &c-#value#"));
            lf.setPosPctStat(statSec.getString("percentage-positive", "&7#name#: &a+#value#%"));
            lf.setNegPctStat(statSec.getString("percentage-negative", "&7#name#: &c-#value#%"));

            // Parse custom overrides under stat-format.custom
            ConfigurationSection customSec = statSec.getConfigurationSection("custom");
            if (customSec != null) {
                parseCustomStatsSection(lf, customSec);
            }

            // Also parse direct keys under stat-format (e.g. stat-format.attack-damage)
            for (String key : statSec.getKeys(false)) {
                if (key.equalsIgnoreCase("positive") || key.equalsIgnoreCase("negative")
                        || key.equalsIgnoreCase("percentage-positive") || key.equalsIgnoreCase("percentage-negative")
                        || key.equalsIgnoreCase("custom")) {
                    continue;
                }
                if (statSec.isConfigurationSection(key)) {
                    ConfigurationSection s = statSec.getConfigurationSection(key);
                    if (s != null) {
                        lf.addCustomStat(key, new StatDisplayConfig(
                                s.getString("name", null),
                                s.getString("positive", null),
                                s.getString("negative", null),
                                s.getString("percentage-positive", null),
                                s.getString("percentage-negative", null)
                        ));
                    }
                } else if (statSec.isString(key)) {
                    String val = statSec.getString(key);
                    lf.addCustomStat(key, new StatDisplayConfig(null, val, val, val, val));
                }
            }
        }

        // Also allow optional 'stats:' section at root of lore-format file
        ConfigurationSection rootCustomSec = config.getConfigurationSection("stats");
        if (rootCustomSec != null) {
            parseCustomStatsSection(lf, rootCustomSec);
        }

        return lf;
    }

    private static void parseCustomStatsSection(LoreFormat lf, ConfigurationSection sec) {
        for (String key : sec.getKeys(false)) {
            if (sec.isConfigurationSection(key)) {
                ConfigurationSection s = sec.getConfigurationSection(key);
                if (s != null) {
                    lf.addCustomStat(key, new StatDisplayConfig(
                            s.getString("name", null),
                            s.getString("positive", null),
                            s.getString("negative", null),
                            s.getString("percentage-positive", null),
                            s.getString("percentage-negative", null)
                    ));
                }
            } else if (sec.isString(key)) {
                String val = sec.getString(key);
                lf.addCustomStat(key, new StatDisplayConfig(null, val, val, val, val));
            }
        }
    }
}
