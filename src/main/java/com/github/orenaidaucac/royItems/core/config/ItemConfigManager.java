package com.github.orenaidaucac.royItems.core.config;

import com.github.orenaidaucac.royItems.item.ItemDefinition;
import com.github.orenaidaucac.royItems.item.ItemRegistry;
import com.github.orenaidaucac.royItems.item.ItemType;
import com.github.orenaidaucac.royItems.mechanics.PotionEffectData;
import com.github.orenaidaucac.royItems.stat.StatModifier;
import com.github.orenaidaucac.royItems.stat.StatType;
import org.bukkit.NamespacedKey;
import org.bukkit.Registry;
import org.bukkit.attribute.Attribute;
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
        String loreFormat = sec.getString("lore-format", "default");
        def.setLoreFormat(loreFormat);
        if (loreFormat != null && (loreFormat.equalsIgnoreCase("_example") || loreFormat.equalsIgnoreCase("_example.yml"))) {
            plugin.getLogger().severe("======================================================================");
            plugin.getLogger().severe("[RoyItems] [CONFIGURATION ERROR] Item '" + id + "' in " + type.name() + ".yml");
            plugin.getLogger().severe("is configured with reserved lore-format: '" + loreFormat + "'!");
            plugin.getLogger().severe("The file 'lore-format/_example.yml' is a WIKI / DOCUMENTATION guide only,");
            plugin.getLogger().severe("and CANNOT be used as an active item lore template!");
            plugin.getLogger().severe("Lore display has been suppressed (hidden) for this item.");
            plugin.getLogger().severe("Please change 'lore-format' to 'default', 'weapon', 'armor', or a custom template.");
            plugin.getLogger().severe("======================================================================");
        }
        def.setUnbreakable(sec.getBoolean("unbreakable", false));
        def.setGlow(sec.getBoolean("glow", false));
        def.setItemModel(sec.getString("item-model", null));

        // Stats
        ConfigurationSection statSec = sec.getConfigurationSection("stats");
        if (statSec != null) {
            for (String statKey : statSec.getKeys(false)) {
                StatType statType = StatType.fromKey(statKey);
                StatModifier mod = StatModifier.parse(statSec.get(statKey));
                if (statType != null) {
                    def.setStat(statType, mod);
                } else {
                    org.bukkit.attribute.Attribute attr = com.github.orenaidaucac.royItems.lore.DynamicLoreEngine.findVanillaAttribute(statKey);
                    if (attr != null) {
                        def.getVanillaAttributes().put(attr, mod);
                    } else {
                        def.setCustomStat(statKey, mod);
                    }
                }
            }
        }

        // Vanilla Attributes
        ConfigurationSection attrSec = sec.getConfigurationSection("attributes");
        if (attrSec != null) {
            Map<Attribute, StatModifier> attrs = new HashMap<>();
            for (String attrKey : attrSec.getKeys(false)) {
                try {
                    NamespacedKey key = attrKey.contains(":") ? NamespacedKey.fromString(attrKey) : NamespacedKey.minecraft(attrKey.toLowerCase().replace('-', '_'));
                    Attribute attr = Registry.ATTRIBUTE.get(key);
                    if (attr == null && !attrKey.startsWith("generic.")) {
                        attr = Registry.ATTRIBUTE.get(NamespacedKey.minecraft("generic." + attrKey.toLowerCase().replace('-', '_')));
                    }
                    if (attr != null) {
                        attrs.put(attr, StatModifier.parse(attrSec.get(attrKey)));
                    }
                } catch (Throwable ignored) {}
            }
            def.setVanillaAttributes(attrs);
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
        def.setHideUnbreakable(sec.getBoolean("hide-unbreakable", false));

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
        String itemTier = sec.getString("item-tier");
        if (itemTier == null) itemTier = sec.getString("tier");
        if (itemTier == null) itemTier = sec.getString("item_tier");
        def.setItemTier(itemTier);
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

        // Restrictions & Protections
        def.setDisableOnUse(sec.getBoolean("disable-on-use", false));
        def.setDisableOnEnchantment(sec.getBoolean("disable-on-enchantment", false));
        def.setDisableOnCrafting(sec.getBoolean("disable-on-crafting", false));
        def.setDisableOnDrop(sec.getBoolean("disable-on-drop", false));
        if (sec.contains("prevent-anvil-rename")) {
            def.setPreventAnvilRename(sec.getBoolean("prevent-anvil-rename"));
        } else if (sec.contains("prevent-rename")) {
            def.setPreventAnvilRename(sec.getBoolean("prevent-rename"));
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
        config.set(path + ".lore", (def.getLore() == null || def.getLore().isEmpty()) ? new ArrayList<>() : def.getLore());
        config.set(path + ".lore-format", def.getLoreFormat());
        config.set(path + ".unbreakable", def.isUnbreakable());
        config.set(path + ".glow", def.isGlow());
        config.set(path + ".item-model", (def.getItemModel() != null && !def.getItemModel().trim().isEmpty()) ? def.getItemModel().trim() : null);

        // Stats
        config.set(path + ".stats", null);
        if (def.getStats() != null && !def.getStats().isEmpty()) {
            for (Map.Entry<StatType, StatModifier> e : def.getStats().entrySet()) {
                StatModifier mod = e.getValue();
                if (mod == null || mod.isEmpty()) continue;
                boolean hasFlat = Math.abs(mod.getFlat()) > 0.00001;
                boolean hasPct = Math.abs(mod.getPercent()) > 0.00001;
                if (hasFlat && hasPct) {
                    Map<String, Object> map = new LinkedHashMap<>();
                    map.put("flat", mod.getFlat());
                    map.put("percent", formatPercent(mod.getPercentValue()) + "%");
                    config.set(path + ".stats." + e.getKey().getKey(), map);
                } else if (hasPct) {
                    config.set(path + ".stats." + e.getKey().getKey(), formatPercent(mod.getPercentValue()) + "%");
                } else {
                    config.set(path + ".stats." + e.getKey().getKey(), mod.getFlat());
                }
            }
        }
        if (def.getCustomStats() != null && !def.getCustomStats().isEmpty()) {
            for (Map.Entry<String, StatModifier> e : def.getCustomStats().entrySet()) {
                StatModifier mod = e.getValue();
                if (mod == null || mod.isEmpty()) continue;
                boolean hasFlat = Math.abs(mod.getFlat()) > 0.00001;
                boolean hasPct = Math.abs(mod.getPercent()) > 0.00001;
                if (hasFlat && hasPct) {
                    Map<String, Object> map = new LinkedHashMap<>();
                    map.put("flat", mod.getFlat());
                    map.put("percent", formatPercent(mod.getPercentValue()) + "%");
                    config.set(path + ".stats." + e.getKey(), map);
                } else if (hasPct) {
                    config.set(path + ".stats." + e.getKey(), formatPercent(mod.getPercentValue()) + "%");
                } else {
                    config.set(path + ".stats." + e.getKey(), mod.getFlat());
                }
            }
        }

        // Vanilla Attributes
        config.set(path + ".attributes", null);
        if (def.getVanillaAttributes() != null && !def.getVanillaAttributes().isEmpty()) {
            for (Map.Entry<Attribute, StatModifier> e : def.getVanillaAttributes().entrySet()) {
                StatModifier mod = e.getValue();
                if (mod == null || mod.isEmpty()) continue;
                String attrKey = e.getKey().getKey().getKey();
                boolean hasFlat = Math.abs(mod.getFlat()) > 0.00001;
                boolean hasPct = Math.abs(mod.getPercent()) > 0.00001;
                if (hasFlat && hasPct) {
                    Map<String, Object> map = new LinkedHashMap<>();
                    map.put("flat", mod.getFlat());
                    map.put("percent", formatPercent(mod.getPercentValue()) + "%");
                    config.set(path + ".attributes." + attrKey, map);
                } else if (hasPct) {
                    config.set(path + ".attributes." + attrKey, formatPercent(mod.getPercentValue()) + "%");
                } else {
                    config.set(path + ".attributes." + attrKey, mod.getFlat());
                }
            }
        }

        if (def.getItemTier() != null && !def.getItemTier().trim().isEmpty()) {
            config.set(path + ".item-tier", def.getItemTier().trim());
        } else {
            config.set(path + ".item-tier", null);
        }
        config.set(path + ".tier", null);
        config.set(path + ".item_tier", null);

        config.set(path + ".item-set", (def.getItemSet() != null && !def.getItemSet().trim().isEmpty()) ? def.getItemSet().trim() : null);

        // Enchantments
        config.set(path + ".enchantments", null);
        if (def.getEnchantments() != null && !def.getEnchantments().isEmpty()) {
            for (Map.Entry<Enchantment, Integer> e : def.getEnchantments().entrySet()) {
                if (e.getKey() != null && e.getValue() != null && e.getValue() > 0) {
                    NamespacedKey nk = e.getKey().getKey();
                    String enchKey = "minecraft".equalsIgnoreCase(nk.getNamespace()) ? nk.getKey() : nk.toString();
                    config.set(path + ".enchantments." + enchKey, e.getValue());
                }
            }
        }
        config.set(path + ".hide-enchantments", def.isHideEnchantments() ? true : null);
        config.set(path + ".hide-attributes", def.isHideAttributes() ? true : null);
        config.set(path + ".hide-unbreakable", def.isHideUnbreakable() ? true : null);

        // Requirements & Permissions
        config.set(path + ".permission", (def.getPermission() != null && !def.getPermission().trim().isEmpty()) ? def.getPermission().trim() : null);
        config.set(path + ".drop", def.isDrop() ? null : false);
        config.set(path + ".level-required", def.getLevelRequired() > 0 ? def.getLevelRequired() : null);

        boolean hasMmoClass = def.getMmocoreClass() != null && !def.getMmocoreClass().trim().isEmpty();
        boolean hasMmoLevel = def.getMmocoreLevel() > 0;
        if (hasMmoClass || hasMmoLevel) {
            config.set(path + ".mmocore-required.class", hasMmoClass ? def.getMmocoreClass().trim() : null);
            config.set(path + ".mmocore-required.level", hasMmoLevel ? def.getMmocoreLevel() : null);
        } else {
            config.set(path + ".mmocore-required", null);
        }

        boolean hasAuraSkill = def.getAuraSkill() != null && !def.getAuraSkill().trim().isEmpty();
        boolean hasAuraLevel = def.getAuraSkillLevel() > 0;
        if (hasAuraSkill || hasAuraLevel) {
            config.set(path + ".auraskill-required.skill", hasAuraSkill ? def.getAuraSkill().trim() : null);
            config.set(path + ".auraskill-required.level", hasAuraLevel ? def.getAuraSkillLevel() : null);
        } else {
            config.set(path + ".auraskill-required", null);
        }

        config.set(path + ".world-required", (def.getWorldRequired() != null && !def.getWorldRequired().isEmpty()) ? def.getWorldRequired() : null);
        config.set(path + ".biome-required", (def.getBiomeRequired() != null && !def.getBiomeRequired().isEmpty()) ? def.getBiomeRequired() : null);

        // Mechanics & Effects
        config.set(path + ".cooldown", def.getCooldown() > 0 ? def.getCooldown() : null);
        config.set(path + ".expired", (def.getExpired() != null && !def.getExpired().trim().isEmpty()) ? def.getExpired().trim() : null);
        config.set(path + ".gem-socket", (def.getGemSockets() != null && !def.getGemSockets().isEmpty()) ? def.getGemSockets() : null);

        config.set(path + ".effects", null);
        if (def.getEffects() != null && !def.getEffects().isEmpty()) {
            for (PotionEffectData eff : def.getEffects()) {
                if (eff != null && eff.getType() != null) {
                    NamespacedKey nk = eff.getType().getKey();
                    String effKey = "minecraft".equalsIgnoreCase(nk.getNamespace()) ? nk.getKey() : nk.toString();
                    config.set(path + ".effects." + effKey + ".level", eff.getAmplifier() + 1);
                    config.set(path + ".effects." + effKey + ".slot", (eff.getSlot() != null && !eff.getSlot().isEmpty()) ? eff.getSlot() : "MAIN_HAND");
                }
            }
        }

        // Armor Trims & Leather Dye
        config.set(path + ".trim-material", (def.getTrimMaterial() != null && !def.getTrimMaterial().trim().isEmpty()) ? def.getTrimMaterial().trim() : null);
        config.set(path + ".trim-pattern", (def.getTrimPattern() != null && !def.getTrimPattern().trim().isEmpty()) ? def.getTrimPattern().trim() : null);
        config.set(path + ".hide-armor-trim", def.isHideArmorTrim() ? true : null);
        config.set(path + ".dye-color", (def.getDyeColor() != null && !def.getDyeColor().trim().isEmpty()) ? def.getDyeColor().trim() : null);
        config.set(path + ".hide-dye", def.isHideDye() ? true : null);

        // Gem Stone properties
        if (def.getType() == ItemType.GEM_STONE) {
            config.set(path + ".gem-id", (def.getGemId() != null && !def.getGemId().trim().isEmpty()) ? def.getGemId().trim() : null);
            config.set(path + ".gem-stats", null);
            if (def.getGemStats() != null && !def.getGemStats().isEmpty()) {
                for (Map.Entry<StatType, StatModifier> e : def.getGemStats().entrySet()) {
                    StatModifier mod = e.getValue();
                    if (mod == null || mod.isEmpty()) continue;
                    boolean hasFlat = Math.abs(mod.getFlat()) > 0.00001;
                    boolean hasPct = Math.abs(mod.getPercent()) > 0.00001;
                    if (hasFlat && hasPct) {
                        Map<String, Object> map = new LinkedHashMap<>();
                        map.put("flat", mod.getFlat());
                        map.put("percent", formatPercent(mod.getPercentValue()) + "%");
                        config.set(path + ".gem-stats." + e.getKey().getKey(), map);
                    } else if (hasPct) {
                        config.set(path + ".gem-stats." + e.getKey().getKey(), formatPercent(mod.getPercentValue()) + "%");
                    } else {
                        config.set(path + ".gem-stats." + e.getKey().getKey(), mod.getFlat());
                    }
                }
            }
        }

        // Restrictions & Protections
        config.set(path + ".disable-on-use", def.isDisableOnUse() ? true : null);
        config.set(path + ".disable-on-enchantment", def.isDisableOnEnchantment() ? true : null);
        config.set(path + ".disable-on-crafting", def.isDisableOnCrafting() ? true : null);
        config.set(path + ".disable-on-drop", def.isDisableOnDrop() ? true : null);
        config.set(path + ".prevent-anvil-rename", def.getPreventAnvilRename());

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

    private static final java.text.DecimalFormat PERCENT_DF = new java.text.DecimalFormat("#,##0.##");

    public static String formatPercent(double val) {
        return PERCENT_DF.format(val);
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

    /**
     * Retrieves all item keys from item/<TYPE>.yml in their exact top-to-bottom file order.
     */
    public List<String> getItemKeysInFileOrder(ItemType type) {
        if (type == null || plugin == null) return Collections.emptyList();
        File itemFolder = new File(plugin.getDataFolder(), "item");
        File file = new File(itemFolder, type.name() + ".yml");
        if (!file.exists()) return Collections.emptyList();

        try {
            FileConfiguration config = YamlConfiguration.loadConfiguration(file);
            return new ArrayList<>(config.getKeys(false));
        } catch (Exception e) {
            return Collections.emptyList();
        }
    }
}
