package com.github.orenaidaucac.royItems.tier_set;

import com.github.orenaidaucac.royItems.stat.PlayerStatCache;
import com.github.orenaidaucac.royItems.stat.StatModifier;
import com.github.orenaidaucac.royItems.stat.StatType;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.Plugin;

import java.io.File;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Manages item sets loaded from item-set.yml and applies multi-piece bonuses.
 */
public class ItemSetManager {

    private final Plugin plugin;
    private final Map<String, ItemSet> sets = new ConcurrentHashMap<>();

    public ItemSetManager(Plugin plugin) {
        this.plugin = plugin;
    }

    public void loadSets() {
        sets.clear();
        File file = new File(plugin.getDataFolder(), "item-set.yml");
        if (!file.exists()) {
            plugin.saveResource("item-set.yml", false);
        }

        FileConfiguration config = YamlConfiguration.loadConfiguration(file);
        for (String key : config.getKeys(false)) {
            ConfigurationSection sec = config.getConfigurationSection(key);
            if (sec != null) {
                ItemSet set = new ItemSet();
                set.setId(key.toLowerCase());
                set.setDisplayName(sec.getString("display-name", key));

                ConfigurationSection bonusSec = sec.getConfigurationSection("bonuses");
                if (bonusSec != null) {
                    for (String countStr : bonusSec.getKeys(false)) {
                        try {
                            int pieces = Integer.parseInt(countStr);
                            ConfigurationSection statSec = bonusSec.getConfigurationSection(countStr);
                            if (statSec != null) {
                                Map<StatType, StatModifier> stats = new HashMap<>();
                                for (String statKey : statSec.getKeys(false)) {
                                    StatType type = StatType.fromKey(statKey);
                                    if (type != null) {
                                        stats.put(type, StatModifier.parse(statSec.get(statKey)));
                                    }
                                }
                                set.getBonuses().put(pieces, stats);
                            }
                        } catch (NumberFormatException ignored) {}
                    }
                }
                sets.put(key.toLowerCase(), set);
            }
        }
    }

    public ItemSet get(String id) {
        if (id == null) return null;
        return sets.get(id.toLowerCase());
    }

    public Collection<ItemSet> getAll() {
        return Collections.unmodifiableCollection(sets.values());
    }

    /**
     * Applies set bonuses to a PlayerStatCache based on equipped set piece counts.
     */
    public void applySetBonuses(Map<String, Integer> setCounts, PlayerStatCache cache) {
        if (setCounts == null || cache == null) return;

        for (Map.Entry<String, Integer> entry : setCounts.entrySet()) {
            ItemSet set = get(entry.getKey());
            if (set == null) continue;

            int count = entry.getValue();
            for (Map.Entry<Integer, Map<StatType, StatModifier>> bonusEntry : set.getBonuses().entrySet()) {
                if (count >= bonusEntry.getKey()) {
                    for (Map.Entry<StatType, StatModifier> statEntry : bonusEntry.getValue().entrySet()) {
                        cache.addStat(statEntry.getKey(), statEntry.getValue());
                    }
                }
            }
        }
    }
}
