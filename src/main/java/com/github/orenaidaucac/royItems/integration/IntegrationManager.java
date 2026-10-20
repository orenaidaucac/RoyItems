package com.github.orenaidaucac.royItems.integration;

import com.github.orenaidaucac.royItems.item.ItemPDCManager;
import com.github.orenaidaucac.royItems.item.ItemRegistry;
import com.github.orenaidaucac.royItems.stat.StatManager;
import org.bukkit.Bukkit;
import org.bukkit.plugin.Plugin;

import java.util.logging.Logger;

/**
 * Manages detection and initialization of third-party plugin integrations.
 */
public class IntegrationManager {

    private final Plugin plugin;
    private final Logger logger;

    private VaultHook vaultHook;
    private MMOCoreHook mmoCoreHook;
    private AuraSkillsHook auraSkillsHook;
    private MythicMobsHook mythicMobsHook;
    private WorldGuardHook worldGuardHook;

    public IntegrationManager(Plugin plugin) {
        this.plugin = plugin;
        this.logger = plugin.getLogger();
    }

    public void initialize(StatManager statManager, ItemPDCManager pdcManager, ItemRegistry registry) {
        // Vault
        vaultHook = new VaultHook();
        if (vaultHook.setup()) {
            logger.info("Hooked into Vault Economy successfully.");
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
            logger.info("Hooked into MythicMobs successfully.");
        }

        // WorldGuard
        worldGuardHook = new WorldGuardHook();
        if (worldGuardHook.setup()) {
            logger.info("Hooked into WorldGuard successfully.");
        }

        // PlaceholderAPI
        if (Bukkit.getPluginManager().getPlugin("PlaceholderAPI") != null) {
            new RoyPlaceholderExpansion(statManager, pdcManager, registry).register();
            logger.info("Registered PlaceholderAPI expansion for RoyItems.");
        }
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

    public WorldGuardHook getWorldGuardHook() {
        return worldGuardHook;
    }
}
