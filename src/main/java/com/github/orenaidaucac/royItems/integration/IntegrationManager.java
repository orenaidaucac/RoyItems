package com.github.orenaidaucac.royItems.integration;

import com.github.orenaidaucac.royItems.item.ItemPDCManager;
import com.github.orenaidaucac.royItems.item.ItemRegistry;
import com.github.orenaidaucac.royItems.stat.StatManager;
import org.bukkit.Bukkit;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.event.server.PluginDisableEvent;
import org.bukkit.event.server.PluginEnableEvent;
import org.bukkit.plugin.Plugin;

import java.util.logging.Logger;

/**
 * Manages detection and initialization of third-party plugin integrations.
 * Implements dynamic event-driven lifecycle synchronization to detect plugins
 * regardless of cold start order, circular soft-dependencies, or late loading.
 */
public class IntegrationManager implements Listener {

    private final Plugin plugin;
    private final Logger logger;

    private VaultHook vaultHook;
    private MMOCoreHook mmoCoreHook;
    private AuraSkillsHook auraSkillsHook;
    private MythicMobsHook mythicMobsHook;
    private WorldGuardHook worldGuardHook;
    private PlayerPointsHook playerPointsHook;
    private ExcellentEconomyHook excellentEconomyHook;
    private CitizensHook citizensHook;
    private FancyNpcsHook fancyNpcsHook;
    private ExtraStorageHook extraStorageHook;
    private EcoEnchantsHook ecoEnchantsHook;
    private AdvancedOreGenHook advancedOreGenHook;
    private LuckPermsHook luckPermsHook;

    public IntegrationManager(Plugin plugin) {
        this.plugin = plugin;
        this.logger = plugin != null ? plugin.getLogger() : java.util.logging.Logger.getLogger("RoyItems");
    }

    public void initialize(StatManager statManager, ItemPDCManager pdcManager, ItemRegistry registry) {
        initialize(statManager, pdcManager, registry, null);
    }

    public void initialize(StatManager statManager, ItemPDCManager pdcManager, ItemRegistry registry, com.github.orenaidaucac.royItems.stat.DefenseManager defenseManager) {
        // Vault
        vaultHook = new VaultHook();
        if (vaultHook.setup()) {
            logger.info("Hooked into Vault Economy successfully.");
        }

        // PlayerPoints
        playerPointsHook = new PlayerPointsHook();
        if (playerPointsHook.setup()) {
            logger.info("Hooked into PlayerPoints successfully.");
        }

        // ExcellentEconomy
        excellentEconomyHook = new ExcellentEconomyHook();
        if (excellentEconomyHook.setup()) {
            logger.info("Hooked into ExcellentEconomy successfully.");
        }

        // Citizens
        citizensHook = new CitizensHook();
        if (citizensHook.isAvailable()) {
            logger.info("Hooked into Citizens successfully.");
        }

        // FancyNpcs
        fancyNpcsHook = new FancyNpcsHook();
        if (fancyNpcsHook.isAvailable()) {
            logger.info("Hooked into FancyNpcs successfully.");
        }

        // MMOCore
        mmoCoreHook = new MMOCoreHook();
        if (mmoCoreHook.setup()) {
            logger.info("Hooked into MMOCore successfully.");
        }

        // AuraSkills
        auraSkillsHook = new AuraSkillsHook();
        if (auraSkillsHook.setup()) {
            logger.info("Hooked into AuraSkills successfully.");
        }

        // MythicMobs
        mythicMobsHook = new MythicMobsHook();
        if (mythicMobsHook.setup()) {
            if (defenseManager != null) {
                mythicMobsHook.registerDamageListener(plugin, defenseManager);
            }
            logger.info("Hooked into MythicMobs successfully.");
        }

        // WorldGuard
        worldGuardHook = new WorldGuardHook();
        if (worldGuardHook.setup()) {
            logger.info("Hooked into WorldGuard successfully.");
        }

        // ExtraStorage
        extraStorageHook = new ExtraStorageHook();
        if (extraStorageHook.setup()) {
            logger.info("Hooked into ExtraStorage successfully.");
        }

        // EcoEnchants
        ecoEnchantsHook = new EcoEnchantsHook();
        if (ecoEnchantsHook.setup()) {
            logger.info("Hooked into EcoEnchants successfully.");
        }

        // AdvancedOreGen
        advancedOreGenHook = new AdvancedOreGenHook();
        if (advancedOreGenHook.setup()) {
            logger.info("Hooked into AdvancedOreGen successfully.");
        }

        // LuckPerms
        luckPermsHook = new LuckPermsHook();
        com.github.orenaidaucac.royItems.core.platform.PlatformScheduler sched = null;
        if (plugin instanceof com.github.orenaidaucac.royItems.RoyItems ri) {
            sched = ri.getPlatformScheduler();
        }
        if (luckPermsHook.setup(plugin, statManager, statManager.getPermissionStatManager(), sched)) {
            logger.info("Hooked into LuckPerms event bus successfully.");
        }

        // PlaceholderAPI
        if (Bukkit.getPluginManager().getPlugin("PlaceholderAPI") != null) {
            new RoyPlaceholderExpansion(statManager, pdcManager, registry).register();
            logger.info("Registered PlaceholderAPI expansion for RoyItems.");
        }

        // Register dynamic plugin lifecycle listener to safely detect plugins enabled late
        try {
            if (plugin != null) {
                Bukkit.getPluginManager().registerEvents(this, plugin);
            }
        } catch (Throwable ignored) {}
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onPluginEnable(PluginEnableEvent event) {
        String name = event.getPlugin().getName();
        if (name.equalsIgnoreCase("ExtraStorage")) {
            if (extraStorageHook != null && extraStorageHook.setup()) {
                logger.info("Detected ExtraStorage enabled dynamically. Hooked into ExtraStorage successfully!");
            }
        } else if (name.equalsIgnoreCase("EcoEnchants")) {
            if (ecoEnchantsHook != null && ecoEnchantsHook.setup()) {
                logger.info("Detected EcoEnchants enabled dynamically. Hooked into EcoEnchants successfully!");
            }
        } else if (name.equalsIgnoreCase("AdvancedOreGen")) {
            if (advancedOreGenHook != null && advancedOreGenHook.setup()) {
                logger.info("Detected AdvancedOreGen enabled dynamically. Hooked into AdvancedOreGen successfully!");
            }
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onPluginDisable(PluginDisableEvent event) {
        String name = event.getPlugin().getName();
        if (name.equalsIgnoreCase("ExtraStorage")) {
            if (extraStorageHook != null) {
                extraStorageHook.setAvailable(false);
                logger.info("ExtraStorage disabled. Storage hook unlinked.");
            }
        }
    }

    /**
     * Re-checks and refreshes all soft-dependencies on demand (e.g. during /ri reload).
     */
    public void recheckHooks() {
        if (extraStorageHook != null) extraStorageHook.setup();
        if (ecoEnchantsHook != null) ecoEnchantsHook.setup();
        if (advancedOreGenHook != null) advancedOreGenHook.setup();
        if (mythicMobsHook != null) mythicMobsHook.setup();
    }

    public boolean hasVault() {
        return vaultHook != null;
    }

    public VaultHook getVaultHook() {
        return vaultHook;
    }

    public boolean hasMMOCore() {
        return mmoCoreHook != null && mmoCoreHook.isAvailable();
    }

    public MMOCoreHook getMMOCoreHook() {
        return mmoCoreHook;
    }

    public boolean hasAuraSkills() {
        return auraSkillsHook != null && auraSkillsHook.isAvailable();
    }

    public AuraSkillsHook getAuraSkillsHook() {
        return auraSkillsHook;
    }

    public boolean hasMythicMobs() {
        return mythicMobsHook != null && mythicMobsHook.isAvailable();
    }

    public MythicMobsHook getMythicMobsHook() {
        return mythicMobsHook;
    }

    public boolean hasWorldGuard() {
        return worldGuardHook != null && worldGuardHook.isAvailable();
    }

    public boolean hasPlaceholderAPI() {
        return Bukkit.getPluginManager().isPluginEnabled("PlaceholderAPI");
    }

    public boolean hasPlayerPoints() {
        return playerPointsHook != null && playerPointsHook.isAvailable();
    }

    public PlayerPointsHook getPlayerPointsHook() {
        return playerPointsHook;
    }

    public boolean hasExcellentEconomy() {
        return excellentEconomyHook != null && excellentEconomyHook.isAvailable();
    }

    public ExcellentEconomyHook getExcellentEconomyHook() {
        return excellentEconomyHook;
    }

    public boolean hasCitizens() {
        return citizensHook != null && citizensHook.isAvailable();
    }

    public CitizensHook getCitizensHook() {
        return citizensHook;
    }

    public boolean hasFancyNpcs() {
        return fancyNpcsHook != null && fancyNpcsHook.isAvailable();
    }

    public FancyNpcsHook getFancyNpcsHook() {
        return fancyNpcsHook;
    }

    public WorldGuardHook getWorldGuardHook() {
        return worldGuardHook;
    }

    public boolean hasExtraStorage() {
        return extraStorageHook != null && extraStorageHook.isAvailable();
    }

    public ExtraStorageHook getExtraStorageHook() {
        return extraStorageHook;
    }

    public boolean hasEcoEnchants() {
        return ecoEnchantsHook != null && ecoEnchantsHook.isAvailable();
    }

    public EcoEnchantsHook getEcoEnchantsHook() {
        return ecoEnchantsHook;
    }

    public boolean hasAdvancedOreGen() {
        return advancedOreGenHook != null && advancedOreGenHook.isAvailable();
    }

    public AdvancedOreGenHook getAdvancedOreGenHook() {
        return advancedOreGenHook;
    }

    public boolean hasLuckPerms() {
        return luckPermsHook != null && luckPermsHook.isAvailable();
    }

    public LuckPermsHook getLuckPermsHook() {
        return luckPermsHook;
    }

    public void cleanup() {
        try {
            HandlerList.unregisterAll(this);
        } catch (Throwable ignored) {}
        if (luckPermsHook != null) {
            luckPermsHook.cleanup();
        }
    }
}
