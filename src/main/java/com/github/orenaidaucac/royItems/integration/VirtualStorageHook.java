package com.github.orenaidaucac.royItems.integration;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.Plugin;

import java.lang.reflect.Method;
import java.util.Collection;
import java.util.Locale;
import java.util.UUID;
import java.util.logging.Level;

/**
 * High-performance, atomic integration hook into RoyMC-VirtualStorage.
 * Enables RoyItems Crafting-Station to directly inspect and consume ingredients
 * (both RoyItems MATERIALS and Vanilla items) from players' Virtual Storage
 * without requiring manual withdrawal into the player inventory.
 *
 * Implemented with dynamic reflection and resilient fallback so it is 100% decoupled
 * and safely tolerates varying plugin compilation orders.
 */
public class VirtualStorageHook {

    private volatile boolean available = false;
    private Plugin virtualStoragePlugin;

    public synchronized boolean setup() {
        try {
            if (Bukkit.getServer() == null || Bukkit.getPluginManager() == null) {
                this.available = false;
                this.virtualStoragePlugin = null;
                return false;
            }
            Plugin vPlugin = Bukkit.getPluginManager().getPlugin("RoyMC-VirtualStorage");
            if (vPlugin == null) {
                vPlugin = Bukkit.getPluginManager().getPlugin("VirtualStorage");
            }
            if (vPlugin == null || !vPlugin.isEnabled()) {
                this.available = false;
                this.virtualStoragePlugin = null;
                return false;
            }
            this.virtualStoragePlugin = vPlugin;
            this.available = true;
            return true;
        } catch (Throwable t) {
            this.available = false;
            this.virtualStoragePlugin = null;
            return false;
        }
    }

    public void setAvailable(boolean available) {
        this.available = available;
    }

    public boolean isAvailable() {
        try {
            if (Bukkit.getServer() == null || Bukkit.getPluginManager() == null) {
                return false;
            }
            Plugin vPlugin = Bukkit.getPluginManager().getPlugin("RoyMC-VirtualStorage");
            if (vPlugin == null) {
                vPlugin = Bukkit.getPluginManager().getPlugin("VirtualStorage");
            }
            if (vPlugin == null || !vPlugin.isEnabled()) {
                this.available = false;
                this.virtualStoragePlugin = null;
                return false;
            }
            this.virtualStoragePlugin = vPlugin;
            this.available = true;
            return true;
        } catch (Throwable t) {
            return false;
        }
    }

    /**
     * Resolves the PlayerStorage instance for an online player.
     */
    public Object getStorage(Player player) {
        if (player == null || !isAvailable()) return null;
        try {
            Method getInstanceMethod = virtualStoragePlugin.getClass().getMethod("getInstance");
            Object main = getInstanceMethod.invoke(null);
            if (main == null) return null;

            Method getStorageManagerMethod = main.getClass().getMethod("getStorageManager");
            Object storageManager = getStorageManagerMethod.invoke(main);
            if (storageManager == null) return null;

            try {
                Method getStorageMethod = storageManager.getClass().getMethod("getStorage", UUID.class, String.class);
                return getStorageMethod.invoke(storageManager, player.getUniqueId(), player.getName());
            } catch (NoSuchMethodException e) {
                Method getStorageMethod = storageManager.getClass().getMethod("getStorage", UUID.class);
                return getStorageMethod.invoke(storageManager, player.getUniqueId());
            }
        } catch (Throwable t) {
            Bukkit.getLogger().log(Level.FINE, "[RoyItems] Failed to get PlayerStorage for " + player.getName(), t);
            return null;
        }
    }

    /**
     * Returns the quantity of the specified ingredient stored in the player's Virtual Storage.
     * Supports both RoyItems key format (ROYITEMS:MATERIAL:<id>) and Vanilla material names (e.g. DIAMOND).
     *
     * @param player the player
     * @param storageKey identifier or material name
     * @return quantity available in storage, or 0 if absent/storage unavailable
     */
    public long getAmount(Player player, String storageKey) {
        if (player == null || storageKey == null || storageKey.isBlank() || !isAvailable()) {
            return 0;
        }
        try {
            Object storage = getStorage(player);
            if (storage == null) return 0;

            // Strategy 1: Direct getAmount(String) method
            try {
                Method getAmountMethod = storage.getClass().getMethod("getAmount", String.class);
                Object res = getAmountMethod.invoke(storage, storageKey);
                if (res instanceof Number num) return num.longValue();
            } catch (NoSuchMethodException ignored) {}

            // Strategy 2: Fallback item scan across storage.getItems()
            Method getItemsMethod = storage.getClass().getMethod("getItems");
            Object itemsObj = getItemsMethod.invoke(storage);
            if (itemsObj instanceof Collection<?> items) {
                for (Object item : items) {
                    if (item == null) continue;
                    if (isItemMatching(item, storageKey)) {
                        Method getAmountMethod = item.getClass().getMethod("getAmount");
                        Object amtObj = getAmountMethod.invoke(item);
                        if (amtObj instanceof Number num) {
                            return num.longValue();
                        }
                    }
                }
            }
        } catch (Throwable ignored) {}
        return 0;
    }

    /**
     * Atomically deducts the specified amount of an item from the player's Virtual Storage.
     * Triggers real-time GUI update if the player currently has their storage GUI open.
     *
     * @param player the player
     * @param storageKey identifier or material name
     * @param amount quantity to deduct
     * @return true if successfully deducted, false if insufficient items or error
     */
    public boolean take(Player player, String storageKey, int amount) {
        if (player == null || storageKey == null || amount <= 0 || !isAvailable()) {
            return false;
        }
        try {
            Object storage = getStorage(player);
            if (storage == null) return false;

            // Strategy 1: Direct removeItem(String, long) method
            try {
                Method removeItemMethod = storage.getClass().getMethod("removeItem", String.class, long.class);
                Object res = removeItemMethod.invoke(storage, storageKey, (long) amount);
                if (res instanceof Boolean b && b) {
                    notifyGuiUpdated(player.getUniqueId());
                    return true;
                }
            } catch (NoSuchMethodException ignored) {}

            // Strategy 2: Scan for matching StorageItem and call removeItem(StorageItem, long)
            Method getItemsMethod = storage.getClass().getMethod("getItems");
            Object itemsObj = getItemsMethod.invoke(storage);
            if (itemsObj instanceof Collection<?> items) {
                for (Object item : items) {
                    if (item == null) continue;
                    if (isItemMatching(item, storageKey)) {
                        Method removeItemMethod = storage.getClass().getMethod("removeItem", item.getClass(), long.class);
                        Object removedAmt = removeItemMethod.invoke(storage, item, (long) amount);
                        if (removedAmt instanceof Number num && num.longValue() >= amount) {
                            notifyGuiUpdated(player.getUniqueId());
                            return true;
                        }
                    }
                }
            }
        } catch (Throwable t) {
            Bukkit.getLogger().log(Level.WARNING, "[RoyItems] Error taking " + amount + " of " + storageKey + " from VirtualStorage for " + player.getName(), t);
        }
        return false;
    }

    /**
     * Checks if a StorageItem matches the given storageKey.
     */
    private boolean isItemMatching(Object storageItem, String storageKey) {
        if (storageItem == null || storageKey == null) return false;
        try {
            String normKey = storageKey.trim();

            // 1. Check RoyItems identifier
            try {
                Method getRoyItemsIdMethod = storageItem.getClass().getMethod("getRoyItemsIdentifier");
                Object royId = getRoyItemsIdMethod.invoke(storageItem);
                if (royId instanceof String str && str.equalsIgnoreCase(normKey)) {
                    return true;
                }
            } catch (NoSuchMethodException ignored) {}

            // 2. Check Vanilla material
            Method getProtoMethod = storageItem.getClass().getMethod("getPrototype");
            Object protoObj = getProtoMethod.invoke(storageItem);
            if (protoObj instanceof ItemStack is) {
                if (is.getType().name().equalsIgnoreCase(normKey)) {
                    return true;
                }
            }
        } catch (Throwable ignored) {}
        return false;
    }

    /**
     * Notifies active StorageGui of an update.
     */
    private void notifyGuiUpdated(UUID uuid) {
        if (uuid == null) return;
        try {
            Class<?> guiClass = Class.forName("com.github.orenaidaucac.vstorage.gui.StorageGui");
            Method notifyMethod = guiClass.getMethod("notifyStorageUpdated", UUID.class);
            notifyMethod.invoke(null, uuid);
        } catch (Throwable ignored) {}
    }

    /**
     * Attempts to deposit an item into the player's Virtual Storage.
     */
    public int store(Player player, ItemStack item) {
        if (player == null || item == null || item.getAmount() <= 0 || !isAvailable()) {
            return 0;
        }
        try {
            Object storage = getStorage(player);
            if (storage == null) return 0;

            Method getInstanceMethod = virtualStoragePlugin.getClass().getMethod("getInstance");
            Object main = getInstanceMethod.invoke(null);
            if (main == null) return 0;

            Method getUpgradeManagerMethod = main.getClass().getMethod("getUpgradeManager");
            Object upgradeManager = getUpgradeManagerMethod.invoke(main);
            if (upgradeManager == null) return 0;

            Method getLevelMethod = storage.getClass().getMethod("getLevel");
            int level = (int) getLevelMethod.invoke(storage);

            Method getCapacityMethod = upgradeManager.getClass().getMethod("getCapacity", int.class);
            long capacity = (long) getCapacityMethod.invoke(upgradeManager, level);

            Method addItemMethod = storage.getClass().getMethod("addItem", ItemStack.class, long.class, long.class);
            long added = (long) addItemMethod.invoke(storage, item, (long) item.getAmount(), capacity);

            if (added > 0) {
                notifyGuiUpdated(player.getUniqueId());
                return (int) Math.min(Integer.MAX_VALUE, added);
            }
        } catch (Throwable ignored) {}
        return 0;
    }
}
