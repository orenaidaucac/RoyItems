package com.github.orenaidaucac.royItems.core.config;

import com.github.orenaidaucac.royItems.item.ItemDefinition;
import com.github.orenaidaucac.royItems.item.ItemRegistry;
import com.github.orenaidaucac.royItems.item.ItemType;
import com.github.orenaidaucac.royItems.mechanics.PotionEffectData;
import com.github.orenaidaucac.royItems.stat.StatModifier;
import com.github.orenaidaucac.royItems.stat.StatType;
import org.bukkit.NamespacedKey;
import org.bukkit.Registry;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.plugin.Plugin;
import org.bukkit.potion.PotionEffectType;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.*;
import java.util.logging.Level;

/**
 * Manages atomic loading, parsing, saving, and deletion of custom item definitions across item/<TYPE>.yml.
 */
public class ItemConfigManager {

    private final Plugin plugin;
    private final ItemRegistry registry;

    public ItemConfigManager(Plugin plugin, ItemRegistry registry) {
        this.plugin = plugin;
        this.registry = registry;
    }

    public void loadAll() {
        registry.clear();
        File itemFolder = new File(plugin.getDataFolder(), "item");
        if (!itemFolder.exists()) {
            itemFolder.mkdirs();
        }

        // Save default sample files if empty
        saveDefaultItemFile("SWORD.yml");
        saveDefaultItemFile("ARMOR.yml");
        saveDefaultItemFile("BOW.yml");
        saveDefaultItemFile("STAFF.yml");
        saveDefaultItemFile("GEM_STONE.yml");
        saveDefaultItemFile("WING.yml");
        saveDefaultItemFile("TALISMAN.yml");
        saveDefaultItemFile("CONSUMABLE.yml");
        saveDefaultItemFile("VOUCHER.yml");

        for (ItemType type : ItemType.values()) {
            File file = new File(itemFolder, type.name() + ".yml");
            if (!file.exists()) continue;

            FileConfiguration config = YamlConfiguration.loadConfiguration(file);
            for (String id : config.getKeys(false)) {
                ConfigurationSection sec = config.getConfigurationSection(id);
                if (sec != null) {
                    try {
                        ItemDefinition def = parseItemDefinition(type, id, sec);
                        registry.register(def);
                    } catch (Exception e) {
                        plugin.getLogger().log(Level.SEVERE, "Failed to load item " + type.name() + ":" + id +
                                " from " + file.getName() + ": " + e.getMessage(), e);
                    }
                }
            }
        }
        plugin.getLogger().info("Successfully loaded " + registry.size() + " custom items into memory.");
    }

    private void saveDefaultItemFile(String name) {
        try {
            File file = new File(plugin.getDataFolder(), "item/" + name);
            if (!file.exists()) {
                plugin.saveResource("item/" + name, false);
            }
        } catch (Exception ignored) {}
    }

    public ItemDefinition parseItemDefinition(ItemType type, String id, ConfigurationSection sec) {
        ItemDefinition def = new ItemDefinition(type, id);

        def.setMaterial(sec.getString("material", "DIAMOND_SWORD"));
        def.setName(sec.getString("name", id));
        def.setLore(sec.getStringList("lore"));
        def.setLoreFormat(sec.getString("lore-format", "default"));
        def.setUnbreakable(sec.getBoolean("unbreakable", false));
        def.setGlow(sec.getBoolean("glow", false));
        def.setItemModel(sec.getString("item-model", null));

        // Stats
        ConfigurationSection statSec = sec.getConfigurationSection("stats");
        if (statSec != null) {
            for (String statKey : statSec.getKeys(false)) {
                StatType statType = StatType.fromKey(statKey);
                if (statType != null) {
                    def.setStat(statType, StatModifier.parse(statSec.get(statKey)));
                }
            }
        }

        // Enchantments
        ConfigurationSection enchSec = sec.getConfigurationSection("enchantments");
        if (enchSec != null) {
            Map<Enchantment, Integer> enchants = new HashMap<>();
            for (String enchKey : enchSec.getKeys(false)) {
                try {
                    NamespacedKey key = enchKey.contains(":") ? NamespacedKey.fromString(enchKey) : NamespacedKey.minecraft(enchKey.toLowerCase());
                    Enchantment ench = Registry.ENCHANTMENT.get(key);
                    if (ench != null) {
                        enchants.put(ench, enchSec.getInt(enchKey, 1));
                    }
                } catch (Throwable ignored) {}
            }
            def.setEnchantments(enchants);
        }

        def.setHideEnchantments(sec.getBoolean("hide-enchantments", false));
        def.setHideAttributes(sec.getBoolean("hide-attributes", false));

        // Requirements
        def.setPermission(sec.getString("permission", null));
        def.setDrop(sec.getBoolean("drop", true));
        def.setLevelRequired(sec.getInt("level-required", 0));
        def.setMmocoreClass(sec.getString("mmocore-required.class", null));
        def.setMmocoreLevel(sec.getInt("mmocore-required.level", 0));
        def.setAuraSkill(sec.getString("auraskill-required.skill", null));
        def.setAuraSkillLevel(sec.getInt("auraskill-required.level", 0));
        def.setWorldRequired(sec.getStringList("world-required"));
        def.setBiomeRequired(sec.getStringList("biome-required"));

        // Tier & Set
        def.setItemTier(sec.getString("item-tier", null));
        def.setItemSet(sec.getString("item-set", null));

        // Mechanics
        def.setCooldown(sec.getDouble("cooldown", 0.0));
        def.setExpired(sec.getString("expired", null));
        def.setGemSockets(sec.getStringList("gem-socket"));

        // Effects
        ConfigurationSection effectsSec = sec.getConfigurationSection("effects");
        if (effectsSec != null) {
            List<PotionEffectData> effectList = new ArrayList<>();
            for (String effKey : effectsSec.getKeys(false)) {
                try {
                    NamespacedKey key = effKey.contains(":") ? NamespacedKey.fromString(effKey) : NamespacedKey.minecraft(effKey.toLowerCase());
                    PotionEffectType pType = Registry.POTION_EFFECT_TYPE.get(key);
                    if (pType != null) {
                        int amp = effectsSec.getInt(effKey + ".level", 1) - 1;
                        String slot = effectsSec.getString(effKey + ".slot", "MAIN_HAND");
                        effectList.add(new PotionEffectData(pType, Math.max(0, amp), slot));
                    }
                } catch (Throwable ignored) {}
            }
            def.setEffects(effectList);
        }

        // Armor trim & Dye
        def.setTrimMaterial(sec.getString("trim-material", null));
        def.setTrimPattern(sec.getString("trim-pattern", null));
        def.setHideArmorTrim(sec.getBoolean("hide-armor-trim", false));
        def.setDyeColor(sec.getString("dye-color", null));
        def.setHideDye(sec.getBoolean("hide-dye", false));

        // Gem Stone specific
        if (type == ItemType.GEM_STONE) {
            def.setGemId(sec.getString("gem-id", id));
            ConfigurationSection gemStatSec = sec.getConfigurationSection("gem-stats");
            if (gemStatSec != null) {
                Map<StatType, StatModifier> gStats = new EnumMap<>(StatType.class);
                for (String gKey : gemStatSec.getKeys(false)) {
                    StatType st = StatType.fromKey(gKey);
                    if (st != null) {
                        gStats.put(st, StatModifier.parse(gemStatSec.get(gKey)));
                    }
                }
                def.setGemStats(gStats);
            }
        }

        def.setVersion(sec.getInt("version", 1));

        return def;
    }

    /**
     * Atomically saves an ItemDefinition into item/<TYPE>.yml.
     */
    public synchronized boolean saveItem(ItemDefinition def) {
        if (def == null) return false;

        File itemFolder = new File(plugin.getDataFolder(), "item");
        if (!itemFolder.exists()) itemFolder.mkdirs();

        File targetFile = new File(itemFolder, def.getType().name() + ".yml");
        FileConfiguration config = targetFile.exists() ? YamlConfiguration.loadConfiguration(targetFile) : new YamlConfiguration();

        String path = def.getId();
        config.set(path + ".material", def.getMaterial());
        config.set(path + ".name", def.getName());
        config.set(path + ".lore", def.getLore());
        config.set(path + ".lore-format", def.getLoreFormat());
        config.set(path + ".unbreakable", def.isUnbreakable());
        config.set(path + ".glow", def.isGlow());
        config.set(path + ".item-model", def.getItemModel());

        // Stats
        if (def.getStats() != null && !def.getStats().isEmpty()) {
            for (Map.Entry<StatType, StatModifier> e : def.getStats().entrySet()) {
                StatModifier mod = e.getValue();
                if (mod.getPercent() != 0) {
                    config.set(path + ".stats." + e.getKey().getKey(), (int)(mod.getPercent() * 100) + "%");
                } else {
                    config.set(path + ".stats." + e.getKey().getKey(), mod.getFlat());
                }
            }
        }

        if (def.getItemTier() != null) config.set(path + ".item-tier", def.getItemTier());
        if (def.getItemSet() != null) config.set(path + ".item-set", def.getItemSet());
        if (def.getLevelRequired() > 0) config.set(path + ".level-required", def.getLevelRequired());
        if (def.getCooldown() > 0) config.set(path + ".cooldown", def.getCooldown());
        if (def.getExpired() != null) config.set(path + ".expired", def.getExpired());
        if (!def.getGemSockets().isEmpty()) config.set(path + ".gem-socket", def.getGemSockets());

        // Bump version for LiveUpdate
        int newVersion = def.getVersion() + 1;
        def.setVersion(newVersion);
        config.set(path + ".version", newVersion);

        // Atomic write
        try {
            File tempFile = new File(itemFolder, def.getType().name() + ".tmp");
            config.save(tempFile);
            Files.move(tempFile.toPath(), targetFile.toPath(), StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);

            registry.register(def);
            return true;
        } catch (IOException e) {
            plugin.getLogger().log(Level.SEVERE, "Failed to save item " + def.getId() + " atomically: " + e.getMessage(), e);
            return false;
        }
    }

    /**
     * Deletes an item from item/<TYPE>.yml.
     */
    public synchronized boolean deleteItem(ItemType type, String id) {
        File file = new File(plugin.getDataFolder(), "item/" + type.name() + ".yml");
        if (!file.exists()) return false;

        FileConfiguration config = YamlConfiguration.loadConfiguration(file);
        if (!config.contains(id)) return false;

        config.set(id, null);
        try {
            config.save(file);
            registry.unregister(type, id);
            return true;
        } catch (IOException e) {
            plugin.getLogger().log(Level.SEVERE, "Failed to delete item " + id + ": " + e.getMessage(), e);
            return false;
        }
    }
}
