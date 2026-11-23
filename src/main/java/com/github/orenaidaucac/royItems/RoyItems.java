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
import com.github.orenaidaucac.royItems.voucher.VoucherManager;
import com.github.orenaidaucac.royItems.metrics.RoyMetricsManager;
import com.github.orenaidaucac.royItems.socket.GemSocketListener;
import com.github.orenaidaucac.royItems.socket.GemSocketManager;
import com.github.orenaidaucac.royItems.stat.CombatListener;
import com.github.orenaidaucac.royItems.stat.DefenseManager;
import com.github.orenaidaucac.royItems.stat.PlayerEquipmentListener;
import com.github.orenaidaucac.royItems.stat.StatManager;
import com.github.orenaidaucac.royItems.tier_set.ItemSetManager;
import com.github.orenaidaucac.royItems.tier_set.ItemTierManager;
import com.github.orenaidaucac.royItems.tier_set.TierDropGlowListener;
import com.github.orenaidaucac.royItems.mechanics.DamageParticleFilterManager;
import com.github.orenaidaucac.royItems.mechanics.ItemProtectionListener;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
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
    private com.github.orenaidaucac.royItems.tier_set.SetBonusNotificationManager setBonusNotificationManager;
    private StatManager statManager;
    private DefenseManager defenseManager;
    private com.github.orenaidaucac.royItems.stat.PermissionStatManager permissionStatManager;

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
    private TierDropGlowListener tierDropGlowListener;
    private DamageParticleFilterManager particleFilterManager;
    private ItemProtectionListener itemProtectionListener;
    private MineRadiusListener mineRadiusListener;
    private VoucherManager voucherManager;
    private com.github.orenaidaucac.royItems.station.CraftingStationManager craftingStationManager;
    private com.github.orenaidaucac.royItems.dummy.DummyManager dummyManager;
    private com.github.orenaidaucac.royItems.extraction.ExtractionManager extractionManager;

    public static RoyItems getInstance() {
        return instance;
    }

    public PlatformScheduler getPlatformScheduler() { return platformScheduler; }
    public ItemPDCManager getItemPDCManager() { return itemPDCManager; }
    public DynamicLoreEngine getDynamicLoreEngine() { return dynamicLoreEngine; }
    public com.github.orenaidaucac.royItems.dummy.DummyManager getDummyManager() { return dummyManager; }
    public com.github.orenaidaucac.royItems.extraction.ExtractionManager getExtractionManager() { return extractionManager; }
    public ItemRegistry getItemRegistry() { return itemRegistry; }
    public ItemBuilder getItemBuilder() { return itemBuilder; }
    public ItemConfigManager getItemConfigManager() { return itemConfigManager; }
    public LiveUpdateService getLiveUpdateService() { return liveUpdateService; }
    public ItemTierManager getItemTierManager() { return itemTierManager; }
    public ItemSetManager getItemSetManager() { return itemSetManager; }
    public com.github.orenaidaucac.royItems.tier_set.SetBonusNotificationManager getSetBonusNotificationManager() { return setBonusNotificationManager; }
    public StatManager getStatManager() { return statManager; }
    public com.github.orenaidaucac.royItems.stat.PermissionStatManager getPermissionStatManager() { return permissionStatManager; }
    public DefenseManager getDefenseManager() { return defenseManager; }
    public RequirementChecker getRequirementChecker() { return requirementChecker; }
    public ExpirationManager getExpirationManager() { return expirationManager; }
    public CooldownManager getCooldownManager() { return cooldownManager; }
    public PotionEffectManager getPotionEffectManager() { return potionEffectManager; }
    public GemSocketManager getGemSocketManager() { return gemSocketManager; }
    public IntegrationManager getIntegrationManager() { return integrationManager; }
    public EditorGUIManager getEditorGUIManager() { return editorGUIManager; }
    public com.github.orenaidaucac.royItems.station.CraftingStationManager getCraftingStationManager() { return craftingStationManager; }
    public RoyMetricsManager getMetricsManager() { return metricsManager; }
    public MessageManager getMessageManager() { return messageManager; }
    public ConfigMigrator getConfigMigrator() { return configMigrator; }
    public UpdateChecker getUpdateChecker() { return updateChecker; }
    public TierDropGlowListener getTierDropGlowListener() { return tierDropGlowListener; }
    public DamageParticleFilterManager getParticleFilterManager() { return particleFilterManager; }
    public ItemProtectionListener getItemProtectionListener() { return itemProtectionListener; }
    public MineRadiusListener getMineRadiusListener() { return mineRadiusListener; }
    public VoucherManager getVoucherManager() { return voucherManager; }

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

        this.dynamicLoreEngine.setItemTierManager(this.itemTierManager);
        this.dynamicLoreEngine.setItemSetManager(this.itemSetManager);

        // 5. Item Factory & Config Manager
        this.itemBuilder = new ItemBuilder(itemPDCManager, dynamicLoreEngine);
        this.itemConfigManager = new ItemConfigManager(this, itemRegistry);
        this.itemConfigManager.loadAll();

        // 6. LiveUpdate Engine
        this.liveUpdateService = new LiveUpdateService(itemRegistry, itemPDCManager, itemBuilder, platformScheduler);
        this.liveUpdateService.loadConfig(getConfig());

        // 7. Stat & Combat Engines
        this.permissionStatManager = new com.github.orenaidaucac.royItems.stat.PermissionStatManager(getLogger());
        this.permissionStatManager.loadConfig(getConfig());

        this.statManager = new StatManager(itemRegistry, itemPDCManager, itemSetManager);
        this.statManager.setPermissionStatManager(this.permissionStatManager);
        this.setBonusNotificationManager = new com.github.orenaidaucac.royItems.tier_set.SetBonusNotificationManager(
                this, itemSetManager, messageManager, platformScheduler);
        this.statManager.setNotificationManager(this.setBonusNotificationManager);
        this.statManager.setDynamicLoreEngine(this.dynamicLoreEngine);
        this.dynamicLoreEngine.setStatManager(this.statManager);
        this.liveUpdateService.setStatManager(this.statManager);
        this.defenseManager = new DefenseManager(this, statManager);

        // 8. Mechanics & Integrations
        this.integrationManager = new IntegrationManager(this);
        this.integrationManager.initialize(statManager, itemPDCManager, itemRegistry, defenseManager);

        this.dynamicLoreEngine.setEcoEnchantsHook(this.integrationManager.getEcoEnchantsHook());
        this.dynamicLoreEngine.setPdcManager(this.itemPDCManager);
        this.dynamicLoreEngine.setItemRegistry(this.itemRegistry);

        this.requirementChecker = new RequirementChecker(integrationManager);
        this.statManager.setRequirementChecker(this.requirementChecker);
        this.expirationManager = new ExpirationManager(itemPDCManager, platformScheduler);
        this.cooldownManager = new CooldownManager();
        this.potionEffectManager = new PotionEffectManager(itemRegistry, itemPDCManager, platformScheduler);
        this.voucherManager = new VoucherManager(this, platformScheduler, cooldownManager, requirementChecker, integrationManager, messageManager);
        this.voucherManager.setPdcManager(this.itemPDCManager);

        this.gemSocketManager = new GemSocketManager(itemRegistry, itemPDCManager, dynamicLoreEngine);

        // 9. Ingame GUI Editor
        this.editorGUIManager = new EditorGUIManager(this, itemRegistry, itemConfigManager, itemBuilder, platformScheduler);

        // 9.5. Crafting Station Engine
        this.craftingStationManager = new com.github.orenaidaucac.royItems.station.CraftingStationManager(
                this, itemRegistry, itemPDCManager, itemBuilder, integrationManager);
        this.craftingStationManager.initialize();

        // 9.6. Enchantment Extraction System
        this.extractionManager = new com.github.orenaidaucac.royItems.extraction.ExtractionManager(
                this, itemRegistry, itemPDCManager, dynamicLoreEngine, integrationManager, messageManager);

        // 10. Update Checker (Asynchronous & Admin-Only Notification)
        this.updateChecker = new UpdateChecker(this, platformScheduler);
        this.updateChecker.checkForUpdates(null);

        // 11. Tier Drop Glow System
        this.tierDropGlowListener = new TierDropGlowListener(this, platformScheduler, itemPDCManager, itemRegistry, itemTierManager);

        // 12. Damage Heart Particle Filter (PacketEvents / ProtocolLib)
        this.particleFilterManager = new DamageParticleFilterManager(this);
        this.particleFilterManager.initialize();

        // 12.5. Packet-based Training Dummy System
        this.dummyManager = new com.github.orenaidaucac.royItems.dummy.DummyManager(this);
        this.dummyManager.initialize();

        // 13. Register Event Listeners
        registerListeners();
        this.tierDropGlowListener.applyToAllGroundItems();

        // 14. Register Commands
        registerCommands();

        // 15. Start Background Mechanics
        this.expirationManager.startTask();
        this.potionEffectManager.startTask();

        // 16. bStats Metrics Telemetry
        this.metricsManager = new RoyMetricsManager(this);
        this.metricsManager.initialize();

        long elapsed = System.currentTimeMillis() - startTime;
        getLogger().info("RoyItems successfully enabled in " + elapsed + "ms! Fully operational.");
    }

    @Override
    public void onDisable() {
        getLogger().info("Disabling RoyItems...");

        // 1. Close all active GUI windows across all players to prevent dupes, ghost sessions, or errors
        closeAllActiveGuis();

        // 2. Cleanup Tier Drop Glow Teams
        if (tierDropGlowListener != null) {
            tierDropGlowListener.cleanup();
        }

        // 3. Disable Damage Particle Filter Hook
        if (particleFilterManager != null) {
            particleFilterManager.disable();
        }

        // 4. Cleanup Packet Training Dummies (destroys client packets to prevent ghost entities/holograms)
        if (dummyManager != null) {
            dummyManager.cleanup();
        }

        // 4.5. Cleanup Integrations (LuckPerms EventBus, etc.)
        if (integrationManager != null) {
            integrationManager.cleanup();
        }

        // 4.6. Cleanup Voucher Manager
        if (voucherManager != null) {
            voucherManager.cleanup();
        }

        // 4.7. Cleanup Crafting Station Engine (listeners, commands, GUIs)
        if (craftingStationManager != null) {
            craftingStationManager.cleanup();
        }

        // 4.8. Cleanup Enchantment Extraction System
        if (extractionManager != null) {
            extractionManager.closeAll();
        }

        // 5. Cleanup Player Stat Attribute Modifiers
        if (statManager != null) {
            for (Player player : Bukkit.getOnlinePlayers()) {
                statManager.cleanupPlayer(player);
            }
        }
        if (permissionStatManager != null) {
            permissionStatManager.clearCache();
        }

        // 6. Cancel all scheduled platform tasks
        if (platformScheduler != null) {
            platformScheduler.cancelAllTasks();
        }

        getLogger().info("RoyItems disabled cleanly.");
    }

    /**
     * Closes every single open GUI belonging to RoyItems for all players online.
     * Prevents duplication or crashes during reloads or plugin disable (e.g. via PlugmanX).
     */
    public void closeAllActiveGuis() {
        if (editorGUIManager != null) {
            editorGUIManager.closeAll();
        }
        if (craftingStationManager != null) {
            craftingStationManager.closeAllOpenGuis();
        }
        if (extractionManager != null) {
            extractionManager.closeAll();
        }
        for (Player player : Bukkit.getOnlinePlayers()) {
            try {
                if (player.getOpenInventory() != null && player.getOpenInventory().getTopInventory() != null) {
                    var holder = player.getOpenInventory().getTopInventory().getHolder();
                    if (holder != null && holder.getClass().getName().contains("triumphteam.gui")) {
                        player.closeInventory();
                    }
                }
            } catch (Throwable ignored) {}
        }
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

        // 1. Close active GUI editor sessions and station GUIs
        closeAllActiveGuis();
        if (editorGUIManager != null) {
            editorGUIManager.reloadConfig();
        }
        if (craftingStationManager != null) {
            craftingStationManager.loadAll();
        }
        if (extractionManager != null) {
            extractionManager.loadConfig();
        }

        // 2. Reload main config, combat defense engine, and update checker
        reloadConfig();
        if (integrationManager != null) {
            integrationManager.recheckHooks();
        }
        if (permissionStatManager != null) {
            permissionStatManager.loadConfig(getConfig());
        }
        if (defenseManager != null) {
            defenseManager.loadConfig();
        }
        if (itemProtectionListener != null) {
            itemProtectionListener.loadConfig(getConfig());
        }
        if (mineRadiusListener != null) {
            mineRadiusListener.loadConfig(getConfig());
        }
        if (voucherManager != null) {
            voucherManager.loadConfig(getConfig());
        }
        if (liveUpdateService != null) {
            liveUpdateService.loadConfig(getConfig());
        }
        if (particleFilterManager != null) {
            particleFilterManager.loadConfig();
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
        if (setBonusNotificationManager != null) {
            setBonusNotificationManager.loadConfig();
        }
        if (tierDropGlowListener != null) {
            tierDropGlowListener.applyToAllGroundItems();
        }

        // 6. Reload Items from item/<TYPE>.yml
        itemConfigManager.loadAll();

        // 7. Refresh Packet Training Dummies (cleans up & reloads client models)
        if (dummyManager != null) {
            dummyManager.cleanup();
            dummyManager.initialize();
        }

        // 7.5. Recalculate stats for all online players immediately (permissions & config reload)
        if (statManager != null) {
            for (Player p : Bukkit.getOnlinePlayers()) {
                if (p != null && p.isValid()) {
                    statManager.recalculate(p);
                }
            }
        }

        // 8. Trigger LiveUpdate across all online players and report statistics
        if (liveUpdateService != null && liveUpdateService.isEnabled() && liveUpdateService.isAutoUpdateOnReload()) {
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
        } else if (messageManager != null && sender != null) {
            messageManager.send(sender, "reload-success", "&aAll configurations, items, and lore templates reloaded successfully!");
        }

        // 8.5. Fire public RoyItemsReloadEvent for external integrations (such as RoyMC-VirtualStorage)
        try {
            getServer().getPluginManager().callEvent(new com.github.orenaidaucac.royItems.api.event.RoyItemsReloadEvent());
        } catch (Throwable t) {
            getLogger().log(java.util.logging.Level.WARNING, "Failed to dispatch RoyItemsReloadEvent", t);
        }
    }

    private void registerListeners() {
        var pm = getServer().getPluginManager();
        pm.registerEvents(new CombatListener(statManager, defenseManager, integrationManager.getMythicMobsHook()), this);
        pm.registerEvents(new com.github.orenaidaucac.royItems.lore.EnchantmentListener(this, itemPDCManager, dynamicLoreEngine, platformScheduler), this);
        pm.registerEvents(new PlayerEquipmentListener(statManager, platformScheduler, liveUpdateService, requirementChecker, itemPDCManager, itemRegistry), this);
        pm.registerEvents(new GemSocketListener(gemSocketManager, itemPDCManager, statManager), this);
        pm.registerEvents(new com.github.orenaidaucac.royItems.extraction.EnchantExtractionListener(extractionManager, itemPDCManager, itemRegistry), this);
        pm.registerEvents(voucherManager, this);
        pm.registerEvents(new SpecialItemListener(itemPDCManager, itemRegistry, cooldownManager, requirementChecker, integrationManager, voucherManager), this);
        this.itemProtectionListener = new ItemProtectionListener(itemPDCManager, itemRegistry, messageManager);
        this.itemProtectionListener.loadConfig(getConfig());
        pm.registerEvents(this.itemProtectionListener, this);
        this.mineRadiusListener = new MineRadiusListener(itemPDCManager, itemRegistry, statManager,
                integrationManager.getExtraStorageHook(), integrationManager.getAdvancedOreGenHook(), platformScheduler);
        this.mineRadiusListener.loadConfig(getConfig());
        pm.registerEvents(this.mineRadiusListener, this);
        pm.registerEvents(expirationManager, this);
        pm.registerEvents(editorGUIManager, this);
        pm.registerEvents(editorGUIManager.getChatInputManager(), this);
        pm.registerEvents(new UpdateListener(this, updateChecker, platformScheduler), this);
        if (tierDropGlowListener != null) {
            pm.registerEvents(tierDropGlowListener, this);
        }
        if (dummyManager != null) {
            pm.registerEvents(new com.github.orenaidaucac.royItems.dummy.DummyAttackListener(dummyManager), this);
        }
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
