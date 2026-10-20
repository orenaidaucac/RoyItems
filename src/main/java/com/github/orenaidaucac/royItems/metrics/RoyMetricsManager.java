package com.github.orenaidaucac.royItems.metrics;

import com.github.orenaidaucac.royItems.RoyItems;
import com.github.orenaidaucac.royItems.integration.IntegrationManager;
import com.github.orenaidaucac.royItems.item.ItemDefinition;
import com.github.orenaidaucac.royItems.item.ItemRegistry;
import org.bstats.bukkit.Metrics;
import org.bstats.charts.AdvancedPie;
import org.bstats.charts.SimplePie;
import org.bstats.charts.SingleLineChart;
import org.bukkit.Bukkit;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.logging.Level;

/**
 * Manages bStats metrics and anonymous custom telemetry for RoyItems.
 * Thread-safe and Folia/Paper/Spigot compatible.
 */
public class RoyMetricsManager {

    public static final int BSTATS_PLUGIN_ID = 34832;

    private final RoyItems plugin;
    private Metrics metrics;

    public RoyMetricsManager(RoyItems plugin) {
        this.plugin = plugin;
    }

    /**
     * Initializes bStats metrics and registers all custom telemetry charts.
     * Safe to call in onEnable; guaranteed to initialize only once.
     */
    public synchronized void initialize() {
        if (metrics != null) {
            return; // Already registered
        }

        try {
            this.metrics = new Metrics(plugin, BSTATS_PLUGIN_ID);
            registerCustomCharts();
            plugin.getLogger().info("bStats metrics initialized successfully (Plugin ID: " + BSTATS_PLUGIN_ID + ").");
        } catch (Throwable t) {
            plugin.getLogger().log(Level.WARNING, "Failed to initialize bStats metrics: " + t.getMessage(), t);
        }
    }

    private void registerCustomCharts() {
        if (metrics == null) return;

        // 1. Server Software (Folia / Paper / Spigot / Custom)
        metrics.addCustomChart(new SimplePie("server_software", () -> {
            try {
                if (plugin.getPlatformScheduler() != null && plugin.getPlatformScheduler().isFolia()) {
                    return "Folia";
                }
                try {
                    Class.forName("io.papermc.paper.configuration.Configuration");
                    return "Paper";
                } catch (ClassNotFoundException ignored) {
                    if (Bukkit.getVersion().contains("Paper") || Bukkit.getName().equalsIgnoreCase("Paper")) {
                        return "Paper";
                    }
                    return Bukkit.getName();
                }
            } catch (Throwable t) {
                return "Unknown";
            }
        }));

        // 2. Minecraft Version
        metrics.addCustomChart(new SimplePie("minecraft_version", () -> {
            try {
                return Bukkit.getBukkitVersion();
            } catch (Throwable t) {
                return "Unknown";
            }
        }));

        // 3. Java Version
        metrics.addCustomChart(new SimplePie("java_version", () -> {
            try {
                return System.getProperty("java.version", "Unknown");
            } catch (Throwable t) {
                return "Unknown";
            }
        }));

        // 4. Total Custom Items (SingleLineChart)
        metrics.addCustomChart(new SingleLineChart("total_custom_items", () -> {
            try {
                ItemRegistry registry = plugin.getItemRegistry();
                return registry != null ? registry.size() : 0;
            } catch (Throwable t) {
                return 0;
            }
        }));

        // 5. Items by Type (AdvancedPie)
        metrics.addCustomChart(new AdvancedPie("items_by_type", () -> {
            Map<String, Integer> map = new HashMap<>();
            try {
                ItemRegistry registry = plugin.getItemRegistry();
                if (registry != null) {
                    for (ItemDefinition def : registry.getAll()) {
                        String typeName = def.getType() != null ? def.getType().name() : "UNKNOWN";
                        map.put(typeName, map.getOrDefault(typeName, 0) + 1);
                    }
                }
            } catch (Throwable ignored) {}
            return map;
        }));

        // 6. Enabled Integrations (AdvancedPie)
        metrics.addCustomChart(new AdvancedPie("enabled_integrations", () -> {
            Map<String, Integer> map = new HashMap<>();
            try {
                IntegrationManager im = plugin.getIntegrationManager();
                if (im != null) {
                    if (im.hasVault()) map.put("Vault", 1);
                    if (im.hasPlaceholderAPI()) map.put("PlaceholderAPI", 1);
                    if (im.hasMMOCore()) map.put("MMOCore", 1);
                    if (im.hasAuraSkills()) map.put("AuraSkills", 1);
                    if (im.hasMythicMobs()) map.put("MythicMobs", 1);
                    if (im.hasWorldGuard()) map.put("WorldGuard", 1);
                }
            } catch (Throwable ignored) {}
            return map;
        }));

        // 7. Defense Mode (SimplePie)
        metrics.addCustomChart(new SimplePie("defense_mode", () -> {
            try {
                if (plugin.getDefenseManager() != null && plugin.getDefenseManager().getMode() != null) {
                    return plugin.getDefenseManager().getMode().name();
                }
            } catch (Throwable ignored) {}
            return "MMORPG";
        }));

        // 8. GUI Editor Status (SimplePie)
        metrics.addCustomChart(new SimplePie("gui_editor_status", () -> {
            return plugin.getEditorGUIManager() != null ? "Enabled" : "Disabled";
        }));

        // 9. Item Features breakdown (AdvancedPie)
        metrics.addCustomChart(new AdvancedPie("item_features", () -> {
            Map<String, Integer> features = new HashMap<>();
            try {
                ItemRegistry registry = plugin.getItemRegistry();
                if (registry != null) {
                    Collection<ItemDefinition> items = registry.getAll();
                    int gemSockets = 0;
                    int itemSets = 0;
                    int itemTiers = 0;
                    int customStats = 0;
                    int vanillaAttrs = 0;
                    int enchants = 0;
                    int potionEffects = 0;
                    int cooldowns = 0;
                    int expirations = 0;
                    int armorTrims = 0;
                    int customModels = 0;

                    for (ItemDefinition def : items) {
                        if (def.getGemSockets() != null && !def.getGemSockets().isEmpty()) gemSockets++;
                        if (def.getItemSet() != null && !def.getItemSet().isEmpty()) itemSets++;
                        if (def.getItemTier() != null && !def.getItemTier().isEmpty()) itemTiers++;
                        if (def.getStats() != null && !def.getStats().isEmpty()) customStats++;
                        if (def.getVanillaAttributes() != null && !def.getVanillaAttributes().isEmpty()) vanillaAttrs++;
                        if (def.getEnchantments() != null && !def.getEnchantments().isEmpty()) enchants++;
                        if (def.getEffects() != null && !def.getEffects().isEmpty()) potionEffects++;
                        if (def.getCooldown() > 0) cooldowns++;
                        if (def.getExpired() != null && !def.getExpired().isEmpty()) expirations++;
                        if ((def.getTrimMaterial() != null && !def.getTrimMaterial().isEmpty())
                                || (def.getTrimPattern() != null && !def.getTrimPattern().isEmpty())) {
                            armorTrims++;
                        }
                        if (def.getItemModel() != null && !def.getItemModel().isEmpty()) customModels++;
                    }

                    if (gemSockets > 0) features.put("Gem Sockets", gemSockets);
                    if (itemSets > 0) features.put("Item Sets", itemSets);
                    if (itemTiers > 0) features.put("Item Tiers", itemTiers);
                    if (customStats > 0) features.put("Custom Stats", customStats);
                    if (vanillaAttrs > 0) features.put("Vanilla Attributes", vanillaAttrs);
                    if (enchants > 0) features.put("Enchantments", enchants);
                    if (potionEffects > 0) features.put("Potion Effects", potionEffects);
                    if (cooldowns > 0) features.put("Cooldowns", cooldowns);
                    if (expirations > 0) features.put("Expiration", expirations);
                    if (armorTrims > 0) features.put("Armor Trims", armorTrims);
                    if (customModels > 0) features.put("Custom Models", customModels);
                }
            } catch (Throwable ignored) {}
            return features;
        }));

        // 10. Lore Formats Used (AdvancedPie)
        metrics.addCustomChart(new AdvancedPie("lore_formats_used", () -> {
            Map<String, Integer> map = new HashMap<>();
            try {
                ItemRegistry registry = plugin.getItemRegistry();
                if (registry != null) {
                    for (ItemDefinition def : registry.getAll()) {
                        String format = def.getLoreFormat() != null ? def.getLoreFormat() : "default";
                        map.put(format, map.getOrDefault(format, 0) + 1);
                    }
                }
            } catch (Throwable ignored) {}
            return map;
        }));

        // 11. Registered Item Tiers (SingleLineChart)
        metrics.addCustomChart(new SingleLineChart("registered_item_tiers", () -> {
            try {
                return plugin.getItemTierManager() != null ? plugin.getItemTierManager().getAll().size() : 0;
            } catch (Throwable t) {
                return 0;
            }
        }));

        // 12. Registered Item Sets (SingleLineChart)
        metrics.addCustomChart(new SingleLineChart("registered_item_sets", () -> {
            try {
                return plugin.getItemSetManager() != null ? plugin.getItemSetManager().getAll().size() : 0;
            } catch (Throwable t) {
                return 0;
            }
        }));
    }

    public Metrics getMetrics() {
        return metrics;
    }
}
