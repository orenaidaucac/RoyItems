package com.github.orenaidaucac.royItems.tier_set;

import com.github.orenaidaucac.royItems.stat.PlayerStatCache;
import com.github.orenaidaucac.royItems.stat.StatModifier;
import com.github.orenaidaucac.royItems.stat.StatType;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.Plugin;

import java.io.File;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Manages item sets loaded from item-set.yml, multi-piece stat bonuses,
 * activation thresholds, and active/inactive lore formatting.
 */
public class ItemSetManager {

    private final Plugin plugin;
    private final Map<String, ItemSet> sets = new ConcurrentHashMap<>();

    public ItemSetManager(Plugin plugin) {
        this.plugin = plugin;
    }

    public void loadSets() {
        applyPreparedSets(prepareSets());
    }

    /**
     * Reads and parses item-set.yml into a new map cleanly (safe to invoke asynchronously).
     */
    public Map<String, ItemSet> prepareSets() {
        Map<String, ItemSet> loaded = new ConcurrentHashMap<>();
        if (plugin == null) return loaded;

        File file = new File(plugin.getDataFolder(), "item-set.yml");
        if (!file.exists()) {
            try {
                plugin.saveResource("item-set.yml", false);
            } catch (Exception ignored) {}
        }

        if (!file.exists()) return loaded;
        FileConfiguration config = YamlConfiguration.loadConfiguration(file);
        for (String key : config.getKeys(false)) {
            ConfigurationSection sec = config.getConfigurationSection(key);
            if (sec != null) {
                ItemSet set = parseSet(key, sec);
                if (set != null) {
                    loaded.put(key.toLowerCase(), set);
                }
            }
        }
        return loaded;
    }

    /**
     * Atomically swaps pre-parsed sets into memory.
     */
    public void applyPreparedSets(Map<String, ItemSet> loaded) {
        sets.clear();
        if (loaded != null) {
            sets.putAll(loaded);
        }
    }

    public ItemSet parseSet(String key, ConfigurationSection sec) {
        if (sec == null) return null;
        ItemSet set = new ItemSet();
        set.setId(key.toLowerCase());
        set.setDisplayName(sec.getString("display-name", key));
        set.setRequiredPieces(sec.getInt("required-pieces", 0));

        // Parse lore: active and inactive states
        ConfigurationSection loreSec = sec.getConfigurationSection("lore");
        if (loreSec != null) {
            set.setActiveLore(loadStringOrList(loreSec, "active"));
            set.setInactiveLore(loadStringOrList(loreSec, "inactive"));
        } else {
            // Fallbacks: lore-active / lore-inactive
            List<String> activeList = loadStringOrList(sec, "lore-active");
            if (activeList.isEmpty()) {
                activeList = loadStringOrList(sec, "active-lore");
            }
            set.setActiveLore(activeList);

            List<String> inactiveList = loadStringOrList(sec, "lore-inactive");
            if (inactiveList.isEmpty()) {
                inactiveList = loadStringOrList(sec, "inactive-lore");
            }
            set.setInactiveLore(inactiveList);
        }

        // Parse multi-piece bonuses
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

        return set;
    }

    private List<String> loadStringOrList(ConfigurationSection sec, String path) {
        if (sec == null || !sec.contains(path)) return Collections.emptyList();
        if (sec.isList(path)) {
            return sec.getStringList(path);
        }
        String s = sec.getString(path);
        if (s != null && !s.isEmpty()) {
            return List.of(s);
        }
        return Collections.emptyList();
    }

    public ItemSet get(String id) {
        if (id == null) return null;
        return sets.get(id.toLowerCase());
    }

    public void registerSet(ItemSet set) {
        if (set == null || set.getId() == null) return;
        sets.put(set.getId().toLowerCase(), set);
    }

    public Collection<ItemSet> getAll() {
        return Collections.unmodifiableCollection(sets.values());
    }

    /**
     * Checks if the equipped piece count satisfies the activation condition for a set.
     */
    public boolean isSetBonusActive(String setId, int pieceCount) {
        if (setId == null) return false;
        ItemSet set = get(setId);
        return set != null && set.isBonusActive(pieceCount);
    }

    /**
     * Resolves the set of active set IDs based on the player's equipped piece counts.
     */
    public Set<String> getActiveSetIds(Map<String, Integer> setCounts) {
        if (setCounts == null || setCounts.isEmpty()) return Collections.emptySet();
        Set<String> active = new HashSet<>();
        for (Map.Entry<String, Integer> entry : setCounts.entrySet()) {
            if (isSetBonusActive(entry.getKey(), entry.getValue())) {
                active.add(entry.getKey().toLowerCase());
            }
        }
        return active;
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
