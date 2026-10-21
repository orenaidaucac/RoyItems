package com.github.orenaidaucac.royItems;

import com.github.orenaidaucac.royItems.command.RoyItemsCommand;
import com.github.orenaidaucac.royItems.core.config.ConfigMigrator;
import com.github.orenaidaucac.royItems.core.config.ItemConfigManager;
import com.github.orenaidaucac.royItems.core.message.MessageManager;
import com.github.orenaidaucac.royItems.core.platform.PlatformScheduler;
import com.github.orenaidaucac.royItems.core.update.UpdateChecker;
import com.github.orenaidaucac.royItems.core.update.UpdateListener;
import com.github.orenaidaucac.royItems.gui.EditorGUIManager;
import com.github.orenaidaucac.royItems.integration.IntegrationManager;
import com.github.orenaidaucac.royItems.item.*;
import com.github.orenaidaucac.royItems.lore.DynamicLoreEngine;
import com.github.orenaidaucac.royItems.mechanics.*;
import com.github.orenaidaucac.royItems.metrics.RoyMetricsManager;
import com.github.orenaidaucac.royItems.socket.GemSocketListener;
import com.github.orenaidaucac.royItems.socket.GemSocketManager;
import com.github.orenaidaucac.royItems.stat.CombatListener;
import com.github.orenaidaucac.royItems.stat.DefenseManager;
import com.github.orenaidaucac.royItems.stat.PlayerEquipmentListener;
import com.github.orenaidaucac.royItems.stat.StatManager;
import com.github.orenaidaucac.royItems.tier_set.ItemSetManager;
import com.github.orenaidaucac.royItems.tier_set.ItemTierManager;
import org.bukkit.command.PluginCommand;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * RoyItems — Advanced MMORPG Custom Items Engine for Minecraft 26.2+
 * Compatible with Paper, Spigot, and Folia regionized multithreading.
 */
public final class RoyItems extends JavaPlugin {

    private static RoyItems instance;

    private PlatformScheduler platformScheduler;
    private ItemPDCManager itemPDCManager;
    private DynamicLoreEngine dynamicLoreEngine;
    private ItemRegistry itemRegistry;
    private ItemBuilder itemBuilder;
    private ItemConfigManager itemConfigManager;
    private LiveUpdateService liveUpdateService;

    private ItemTierManager itemTierManager;
    private ItemSetManager itemSetManager;
    private StatManager statManager;
    private DefenseManager defenseManager;

    private RequirementChecker requirementChecker;
    private ExpirationManager expirationManager;
    private CooldownManager cooldownManager;
    private PotionEffectManager potionEffectManager;

    private GemSocketManager gemSocketManager;
    private IntegrationManager integrationManager;
    private EditorGUIManager editorGUIManager;
    private RoyMetricsManager metricsManager;
    private MessageManager messageManager;
    private ConfigMigrator configMigrator;
    private UpdateChecker updateChecker;

    public static RoyItems getInstance() {
        return instance;
    }

    public PlatformScheduler getPlatformScheduler() { return platformScheduler; }
    public ItemPDCManager getItemPDCManager() { return itemPDCManager; }
    public DynamicLoreEngine getDynamicLoreEngine() { return dynamicLoreEngine; }
    public ItemRegistry getItemRegistry() { return itemRegistry; }
    public ItemBuilder getItemBuilder() { return itemBuilder; }
    public ItemConfigManager getItemConfigManager() { return itemConfigManager; }
    public LiveUpdateService getLiveUpdateService() { return liveUpdateService; }
    public ItemTierManager getItemTierManager() { return itemTierManager; }
    public ItemSetManager getItemSetManager() { return itemSetManager; }
    public StatManager getStatManager() { return statManager; }
    public DefenseManager getDefenseManager() { return defenseManager; }
    public RequirementChecker getRequirementChecker() { return requirementChecker; }
    public ExpirationManager getExpirationManager() { return expirationManager; }
    public CooldownManager getCooldownManager() { return cooldownManager; }
    public PotionEffectManager getPotionEffectManager() { return potionEffectManager; }
    public GemSocketManager getGemSocketManager() { return gemSocketManager; }
    public IntegrationManager getIntegrationManager() { return integrationManager; }
    public EditorGUIManager getEditorGUIManager() { return editorGUIManager; }
    public RoyMetricsManager getMetricsManager() { return metricsManager; }
    public MessageManager getMessageManager() { return messageManager; }
    public ConfigMigrator getConfigMigrator() { return configMigrator; }
    public UpdateChecker getUpdateChecker() { return updateChecker; }

    @Override
    public void onEnable() {
        instance = this;
        long startTime = System.currentTimeMillis();

        getLogger().info("Initializing RoyItems v" + getPluginMeta().getVersion() + "...");

        // -1. Automatic Configuration Migration System
        this.configMigrator = new ConfigMigrator(this);
        this.configMigrator.migrateAll();

        saveDefaultConfig();

        // 0. Messages & Localization
        this.messageManager = new MessageManager(this);

        // 1. Core Platform & Scheduling
        this.platformScheduler = new PlatformScheduler(this);
        getLogger().info("Platform mode: " + (platformScheduler.isFolia() ? "Folia (Regionized Multi-threading)" : "Paper/Spigot (Standard)"));

        // 2. Data & Models
        this.itemPDCManager = new ItemPDCManager(this);
        this.itemRegistry = new ItemRegistry();

        // 3. Lore & Formatting Engine
        this.dynamicLoreEngine = new DynamicLoreEngine(this);
        this.dynamicLoreEngine.loadFormats();

        // 4. Tier & Set Systems
        this.itemTierManager = new ItemTierManager(this);
        this.itemTierManager.loadTiers();

        this.itemSetManager = new ItemSetManager(this);
        this.itemSetManager.loadSets();

        // 5. Item Factory & Config Manager
        this.itemBuilder = new ItemBuilder(itemPDCManager, dynamicLoreEngine);
        this.itemConfigManager = new ItemConfigManager(this, itemRegistry);
        this.itemConfigManager.loadAll();

        // 6. LiveUpdate Engine
        this.liveUpdateService = new LiveUpdateService(itemRegistry, itemPDCManager, itemBuilder, platformScheduler);

        // 7. Stat & Combat Engines
        this.statManager = new StatManager(itemRegistry, itemPDCManager, itemSetManager);
        this.liveUpdateService.setStatManager(this.statManager);
        this.defenseManager = new DefenseManager(this, statManager);

        // 8. Mechanics & Integrations
        this.integrationManager = new IntegrationManager(this);
        this.integrationManager.initialize(statManager, itemPDCManager, itemRegistry);

        this.requirementChecker = new RequirementChecker(integrationManager);
        this.expirationManager = new ExpirationManager(itemPDCManager, platformScheduler);
        this.cooldownManager = new CooldownManager();
        this.potionEffectManager = new PotionEffectManager(itemRegistry, itemPDCManager, platformScheduler);

        this.gemSocketManager = new GemSocketManager(itemRegistry, itemPDCManager, dynamicLoreEngine);

        // 9. Ingame GUI Editor
        this.editorGUIManager = new EditorGUIManager(this, itemRegistry, itemConfigManager, itemBuilder, platformScheduler);

        // 10. Register Event Listeners
        registerListeners();

        // 11. Register Commands
        registerCommands();

        // 12. Start Background Mechanics
        this.expirationManager.startTask();
        this.potionEffectManager.startTask();

        // 13. bStats Metrics Telemetry
        this.metricsManager = new RoyMetricsManager(this);
        this.metricsManager.initialize();

        // 14. Update Checker (Asynchronous & Admin-Only Notification)
        this.updateChecker = new UpdateChecker(this, platformScheduler);
        this.updateChecker.checkForUpdates(null);

        long elapsed = System.currentTimeMillis() - startTime;
        getLogger().info("RoyItems successfully enabled in " + elapsed + "ms! Fully operational.");
    }

    @Override
    public void onDisable() {
        getLogger().info("Disabling RoyItems...");

        // 1. Close all active GUI windows to prevent ghost sessions / memory leaks
        if (editorGUIManager != null) {
            editorGUIManager.closeAll();
        }

        // 2. Cancel all scheduled platform tasks
        if (platformScheduler != null) {
            platformScheduler.cancelAllTasks();
        }

        getLogger().info("RoyItems disabled cleanly.");
    }

    /**
     * Reloads all configurations, lore templates, tiers, sets, and triggers LiveUpdate on all players.
     */
    public void reloadAll() {
        reloadAll(null);
    }

    /**
     * Reloads all configurations, lore templates, tiers, sets, messages, and triggers LiveUpdate on all players,
     * sending a professional synchronization report to the sender and server console.
     */
    public void reloadAll(org.bukkit.command.CommandSender sender) {
        // 0. Automatically migrate updated configuration files
        if (configMigrator != null) {
            configMigrator.migrateAll();
        }

        // 1. Close active GUI editor sessions and reload GUI configs
        if (editorGUIManager != null) {
            editorGUIManager.closeAll();
            editorGUIManager.reloadConfig();
        }

        // 2. Reload main config, combat defense engine, and update checker
        reloadConfig();
        if (defenseManager != null) {
            defenseManager.loadConfig();
        }
        if (updateChecker != null) {
            updateChecker.loadConfig();
            updateChecker.checkForUpdates(null);
        }

        // 3. Reload Messages
        if (messageManager != null) {
            messageManager.loadMessages();
        }

        // 4. Reload Lore formats
        dynamicLoreEngine.loadFormats();

        // 5. Reload Tiers and Sets
        itemTierManager.loadTiers();
        itemSetManager.loadSets();

        // 6. Reload Items from item/<TYPE>.yml
        itemConfigManager.loadAll();

        // 7. Trigger LiveUpdate across all online players and report statistics
        liveUpdateService.updateAllOnlinePlayers(report -> {
            if (messageManager != null) {
                if (sender != null) {
                    messageManager.sendReloadReport(sender, report);
                    if (updateChecker != null && updateChecker.isUpdateAvailable() && (sender.hasPermission("royitems.admin") || sender.isOp())) {
                        messageManager.sendUpdateNotification(sender, updateChecker.getCurrentVersion(), updateChecker.getLatestVersion(), updateChecker.getDownloadUrl());
                    }
                }
                if (!(sender instanceof org.bukkit.command.ConsoleCommandSender)) {
                    messageManager.sendReloadReport(getServer().getConsoleSender(), report);
                    if (updateChecker != null && updateChecker.isUpdateAvailable()) {
                        messageManager.sendUpdateNotification(getServer().getConsoleSender(), updateChecker.getCurrentVersion(), updateChecker.getLatestVersion(), updateChecker.getDownloadUrl());
                    }
                }
            }
        });
    }

    private void registerListeners() {
        var pm = getServer().getPluginManager();
        pm.registerEvents(new CombatListener(statManager, defenseManager), this);
        pm.registerEvents(new PlayerEquipmentListener(statManager, platformScheduler, liveUpdateService, requirementChecker, itemPDCManager, itemRegistry), this);
        pm.registerEvents(new GemSocketListener(gemSocketManager, itemPDCManager, statManager), this);
        pm.registerEvents(new SpecialItemListener(itemPDCManager, itemRegistry, cooldownManager, requirementChecker, integrationManager), this);
        pm.registerEvents(expirationManager, this);
        pm.registerEvents(editorGUIManager, this);
        pm.registerEvents(editorGUIManager.getChatInputManager(), this);
        pm.registerEvents(new UpdateListener(this, updateChecker, platformScheduler), this);
    }

    private void registerCommands() {
        PluginCommand cmd = getCommand("royitems");
        if (cmd != null) {
            RoyItemsCommand handler = new RoyItemsCommand(this);
            cmd.setExecutor(handler);
            cmd.setTabCompleter(handler);
        }
    }
}
