package com.github.orenaidaucac.royItems.lore;

import org.bukkit.configuration.file.FileConfiguration;

import java.util.ArrayList;
import java.util.List;

public class LoreFormat {
    private String id;
    private List<String> format = new ArrayList<>();
    private String bar = "&8&m------------------------";
    private String posStat = "&7#name#: &a+#value#";
    private String negStat = "&7#name#: &c-#value#";
    private String posPctStat = "&7#name#: &a+#value#%";
    private String negPctStat = "&7#name#: &c-#value#%";

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

    public static LoreFormat fromConfig(String id, FileConfiguration config) {
        LoreFormat lf = new LoreFormat();
        lf.setId(id);
        lf.setFormat(config.getStringList("format"));
        lf.setBar(config.getString("bar", "&8&m------------------------"));
        lf.setPosStat(config.getString("stat-format.positive", "&7#name#: &a+#value#"));
        lf.setNegStat(config.getString("stat-format.negative", "&7#name#: &c-#value#"));
        lf.setPosPctStat(config.getString("stat-format.percentage-positive", "&7#name#: &a+#value#%"));
        lf.setNegPctStat(config.getString("stat-format.percentage-negative", "&7#name#: &c-#value#%"));
        return lf;
    }
}
