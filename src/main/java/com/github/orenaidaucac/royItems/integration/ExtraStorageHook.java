package com.github.orenaidaucac.royItems.integration;

import me.hsgamer.extrastorage.ExtraStorage;
import me.hsgamer.extrastorage.api.StorageAPI;
import me.hsgamer.extrastorage.api.item.Item;
import me.hsgamer.extrastorage.api.storage.Storage;
import me.hsgamer.extrastorage.api.user.User;
import me.hsgamer.extrastorage.manager.UserManager;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.Plugin;

import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Direct high-performance hook into ExtraStorage plugin for Crafting-Station ingredient resolution
 * and zero-lag Mine Radius storage deposits.
 *
 * Designed with dynamic self-healing lifecycle:
 * - Tolerates any startup loading order (whether RoyItems or ExtraStorage enables first).
 * - Self-heals instantly on demand upon first storage query even if cold started before ExtraStorage.
 * - Dual-strategy user data resolution (direct UserManager + StorageAPI singleton).
 * - Multi-key resilient item finding (supports Enum names, ItemStacks, Materials, and RoyItems identifiers).
 */
public class ExtraStorageHook {

    private volatile boolean available = false;

    public synchronized boolean setup() {
        try {
            if (Bukkit.getServer() == null || Bukkit.getPluginManager() == null) {
                available = false;
                return false;
            }
            Plugin esPlugin = Bukkit.getPluginManager().getPlugin("ExtraStorage");
            if (esPlugin == null || !esPlugin.isEnabled()) {
                available = false;
                return false;
            }
            Class.forName("me.hsgamer.extrastorage.ExtraStorage");
            Class.forName("me.hsgamer.extrastorage.api.StorageAPI");
            available = true;
            return true;
        } catch (Throwable t) {
            available = false;
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
            Plugin esPlugin = Bukkit.getPluginManager().getPlugin("ExtraStorage");
            if (esPlugin == null || !esPlugin.isEnabled()) {
                this.available = false;
                return false;
            }
            if (!this.available) {
                setup();
            }
            return this.available;
        } catch (Throwable t) {
            return false;
        }
    }

    /**
     * Resolves the ExtraStorage User object with dual fallback strategy.
     */
    public User getUser(Player player) {
        if (player == null) return null;
        UUID uuid = player.getUniqueId();

        // Strategy 1: Direct from ExtraStorage main instance -> UserManager
        try {
            ExtraStorage es = ExtraStorage.getInstance();
            if (es == null) {
                Plugin p = Bukkit.getPluginManager().getPlugin("ExtraStorage");
                if (p instanceof ExtraStorage) {
                    es = (ExtraStorage) p;
                }
            }
            if (es != null && es.isEnabled()) {
                UserManager userManager = es.get(UserManager.class);
                if (userManager != null) {
                    User user = userManager.getUser(uuid);
                    if (user != null) {
                        return user;
                    }
                }
            }
        } catch (Throwable ignored) {}

        // Strategy 2: Via StorageAPI singleton
        try {
            StorageAPI api = StorageAPI.getInstance();
            if (api != null) {
                User user = api.getUser(uuid);
                if (user != null) {
                    return user;
                }
            }
        } catch (Throwable ignored) {}

        return null;
    }

    /**
     * Finds an item inside a player's ExtraStorage with resilient multi-key matching.
     */
    public Optional<Item> findItem(Storage storage, Object key) {
        if (storage == null || key == null) return Optional.empty();

        // 1. Direct query with raw key
        try {
            Optional<Item> direct = storage.getItem(key);
            if (direct.isPresent()) return direct;
        } catch (Throwable ignored) {}

        // 2. Material resolution
        if (key instanceof Material mat) {
            try {
                Optional<Item> byName = storage.getItem(mat.name());
                if (byName.isPresent()) return byName;
                Optional<Item> byStack = storage.getItem(new ItemStack(mat));
                if (byStack.isPresent()) return byStack;
            } catch (Throwable ignored) {}
        } else if (key instanceof String strKey) {
            // String representing Material name
            try {
                Material mat = Material.matchMaterial(strKey);
                if (mat != null) {
                    Optional<Item> byName = storage.getItem(mat.name());
                    if (byName.isPresent()) return byName;
                    Optional<Item> byStack = storage.getItem(new ItemStack(mat));
                    if (byStack.isPresent()) return byStack;
                }
            } catch (Throwable ignored) {}

            // RoyItems key case variants
            if (strKey.toUpperCase(Locale.ROOT).startsWith("ROYITEMS:MATERIAL:")) {
                try {
                    Optional<Item> optUpper = storage.getItem(strKey.toUpperCase(Locale.ROOT));
                    if (optUpper.isPresent()) return optUpper;

                    String[] parts = strKey.split(":", 3);
                    if (parts.length == 3) {
                        Optional<Item> optNorm = storage.getItem("ROYITEMS:MATERIAL:" + parts[2].toLowerCase(Locale.ROOT));
                        if (optNorm.isPresent()) return optNorm;
                    }
                } catch (Throwable ignored) {}
            }

            // Fallback scan across storage items
            try {
                Map<String, Item> allItems = storage.getItems();
                if (allItems != null) {
                    for (Map.Entry<String, Item> entry : allItems.entrySet()) {
                        if (entry.getKey().equalsIgnoreCase(strKey)) {
                            return Optional.ofNullable(entry.getValue());
                        }
                    }
                }
            } catch (Throwable ignored) {}
        } else if (key instanceof ItemStack is) {
            try {
                Optional<Item> byType = storage.getItem(is.getType().name());
                if (byType.isPresent()) return byType;
            } catch (Throwable ignored) {}
        }

        return Optional.empty();
    }

    /**
     * Directly deposits items into player's ExtraStorage safely.
     * Respects user storage status, filter rules, and free space limits.
     *
     * @param player the player who owns the storage
     * @param item   the item stack to deposit
     * @return the number of items successfully stored, or 0 if storage is full/unavailable
     */
    public int store(Player player, ItemStack item) {
        if (!isAvailable() || player == null || item == null || item.getAmount() <= 0) return 0;
        try {
            User user = getUser(player);
            if (user == null) return 0;
            Storage storage = user.getStorage();
            if (storage == null || !storage.getStatus()) return 0;

            if (!storage.canStore(item)) return 0;

            long freeSpace = storage.getFreeSpace();
            if (freeSpace == 0) return 0;

            int toStore = item.getAmount();
            if (freeSpace > 0 && freeSpace < toStore) {
                toStore = (int) freeSpace;
            }

            storage.add(item, toStore);
            try {
                user.save();
            } catch (Throwable ignored) {}
            return toStore;
        } catch (Throwable ignored) {}
        return 0;
    }

    /**
     * Get item quantity from ExtraStorage for a player and specified storage key.
     */
    public int getAmount(Player player, Object key) {
        if (!isAvailable() || player == null || key == null) return 0;
        try {
            User user = getUser(player);
            if (user == null) return 0;
            Storage storage = user.getStorage();
            if (storage == null) return 0;

            Optional<Item> itemOpt = findItem(storage, key);
            if (itemOpt.isPresent()) {
                long qty = itemOpt.get().getQuantity();
                return (int) Math.min(Integer.MAX_VALUE, Math.max(0, qty));
            }
        } catch (Throwable ignored) {}
        return 0;
    }

    /**
     * Deducts item quantity from player's ExtraStorage safely.
     * Returns true if successful, false if insufficient items or error.
     */
    public boolean take(Player player, Object key, int amount) {
        if (amount <= 0) return true;
        if (!isAvailable() || player == null || key == null) return false;
        try {
            User user = getUser(player);
            if (user == null) return false;
            Storage storage = user.getStorage();
            if (storage == null) return false;

            Optional<Item> itemOpt = findItem(storage, key);
            if (!itemOpt.isPresent()) return false;
            Item item = itemOpt.get();
            if (item.getQuantity() < amount) return false;

            storage.subtract(item.getKey(), amount);
            try {
                user.save();
            } catch (Throwable ignored) {}
            return true;
        } catch (Throwable ignored) {}
        return false;
    }

    /**
     * Adds items back to ExtraStorage (used for atomic rollback if recipe fails midway).
     */
    public void give(Player player, Object key, int amount) {
        if (amount <= 0 || !isAvailable() || player == null || key == null) return;
        try {
            User user = getUser(player);
            if (user == null) return;
            Storage storage = user.getStorage();
            if (storage == null) return;

            Optional<Item> itemOpt = findItem(storage, key);
            String targetKey = itemOpt.isPresent() ? itemOpt.get().getKey() : key.toString();

            storage.add(targetKey, amount);
            try {
                user.save();
            } catch (Throwable ignored) {}
        } catch (Throwable ignored) {}
    }
}
