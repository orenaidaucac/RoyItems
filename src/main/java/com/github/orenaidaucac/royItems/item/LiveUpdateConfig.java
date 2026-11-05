package com.github.orenaidaucac.royItems.item;

import org.bukkit.configuration.file.FileConfiguration;

/**
 * Configuration model for the RoyItems LiveUpdate system.
 * Allows server administrators to fine-tune container scanning and independent
 * item data preservation rules (enchantments, gems, lore, PDC, attributes, etc.).
 */
public class LiveUpdateConfig {

    private boolean enabled = true;
    private boolean autoUpdateOnReload = true;

    // Containers
    private boolean containerInventory = true;
    private boolean containerArmor = true;
    private boolean containerOffhand = true;
    private boolean containerEnderChest = true;

    // Preservation flags
    private boolean preserveEnchantments = true;
    private boolean preserveGems = true;
    private boolean preserveDurability = true;
    private boolean preserveDisplayName = false;
    private boolean preserveLore = false;
    private boolean preserveTier = false;
    private boolean preserveCustomModelData = false;
    private boolean preservePDC = true;
    private boolean preserveAttributes = false;

    public void load(FileConfiguration config) {
        if (config == null) return;

        this.enabled = config.getBoolean("live-update.enabled", true);
        this.autoUpdateOnReload = config.getBoolean("live-update.auto-update-on-reload", true);

        // Containers
        this.containerInventory = config.getBoolean("live-update.containers.inventory", true);
        this.containerArmor = config.getBoolean("live-update.containers.armor", true);
        this.containerOffhand = config.getBoolean("live-update.containers.offhand", true);
        this.containerEnderChest = config.getBoolean("live-update.containers.ender-chest", true);

        // Preservation flags
        this.preserveEnchantments = config.getBoolean("live-update.preserve.enchantments", true);
        this.preserveGems = config.getBoolean("live-update.preserve.gems", true);
        this.preserveDurability = config.getBoolean("live-update.preserve.durability", true);
        this.preserveDisplayName = config.getBoolean("live-update.preserve.display-name", false);
        this.preserveLore = config.getBoolean("live-update.preserve.lore", false);
        this.preserveTier = config.getBoolean("live-update.preserve.tier", false);
        this.preserveCustomModelData = config.getBoolean("live-update.preserve.custom-model-data", false);
        this.preservePDC = config.getBoolean("live-update.preserve.pdc", true);
        this.preserveAttributes = config.getBoolean("live-update.preserve.attributes", false);
    }

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public boolean isAutoUpdateOnReload() {
        return autoUpdateOnReload;
    }

    public void setAutoUpdateOnReload(boolean autoUpdateOnReload) {
        this.autoUpdateOnReload = autoUpdateOnReload;
    }

    public boolean isContainerInventory() {
        return containerInventory;
    }

    public void setContainerInventory(boolean containerInventory) {
        this.containerInventory = containerInventory;
    }

    public boolean isContainerArmor() {
        return containerArmor;
    }

    public void setContainerArmor(boolean containerArmor) {
        this.containerArmor = containerArmor;
    }

    public boolean isContainerOffhand() {
        return containerOffhand;
    }

    public void setContainerOffhand(boolean containerOffhand) {
        this.containerOffhand = containerOffhand;
    }

    public boolean isContainerEnderChest() {
        return containerEnderChest;
    }

    public void setContainerEnderChest(boolean containerEnderChest) {
        this.containerEnderChest = containerEnderChest;
    }

    public boolean isPreserveEnchantments() {
        return preserveEnchantments;
    }

    public void setPreserveEnchantments(boolean preserveEnchantments) {
        this.preserveEnchantments = preserveEnchantments;
    }

    public boolean isPreserveGems() {
        return preserveGems;
    }

    public void setPreserveGems(boolean preserveGems) {
        this.preserveGems = preserveGems;
    }

    public boolean isPreserveDurability() {
        return preserveDurability;
    }

    public void setPreserveDurability(boolean preserveDurability) {
        this.preserveDurability = preserveDurability;
    }

    public boolean isPreserveDisplayName() {
        return preserveDisplayName;
    }

    public void setPreserveDisplayName(boolean preserveDisplayName) {
        this.preserveDisplayName = preserveDisplayName;
    }

    public boolean isPreserveLore() {
        return preserveLore;
    }

    public void setPreserveLore(boolean preserveLore) {
        this.preserveLore = preserveLore;
    }

    public boolean isPreserveTier() {
        return preserveTier;
    }

    public void setPreserveTier(boolean preserveTier) {
        this.preserveTier = preserveTier;
    }

    public boolean isPreserveCustomModelData() {
        return preserveCustomModelData;
    }

    public void setPreserveCustomModelData(boolean preserveCustomModelData) {
        this.preserveCustomModelData = preserveCustomModelData;
    }

    public boolean isPreservePDC() {
        return preservePDC;
    }

    public void setPreservePDC(boolean preservePDC) {
        this.preservePDC = preservePDC;
    }

    public boolean isPreserveAttributes() {
        return preserveAttributes;
    }

    public void setPreserveAttributes(boolean preserveAttributes) {
        this.preserveAttributes = preserveAttributes;
    }
}
