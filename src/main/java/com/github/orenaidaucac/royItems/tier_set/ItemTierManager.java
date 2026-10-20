package com.github.orenaidaucac.royItems.tier_set;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.Plugin;

import java.io.File;
import java.util.Collection;
import java.util.Collections;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Manages item quality tiers loaded from item-tier.yml.
 */
public class ItemTierManager {

    private final Plugin plugin;
    private final Map<String, ItemTier> tiers = new ConcurrentHashMap<>();

    public ItemTierManager(Plugin plugin) {
        this.plugin = plugin;
    }

    public void loadTiers() {
        tiers.clear();
        File file = new File(plugin.getDataFolder(), "item-tier.yml");
        if (!file.exists()) {
            plugin.saveResource("item-tier.yml", false);
        }

        FileConfiguration config = YamlConfiguration.loadConfiguration(file);
        for (String key : config.getKeys(false)) {
            ConfigurationSection sec = config.getConfigurationSection(key);
            if (sec != null) {
                ItemTier tier = new ItemTier(
                        key.toLowerCase(),
                        sec.getString("display-name", key),
                        sec.getString("color", "&f"),
                        sec.getInt("weight", 0)
                );
                tiers.put(key.toLowerCase(), tier);
            }
        }
    }

    public ItemTier get(String id) {
        if (id == null) return null;
        return tiers.get(id.toLowerCase());
    }

    public Collection<ItemTier> getAll() {
        return Collections.unmodifiableCollection(tiers.values());
    }
}
