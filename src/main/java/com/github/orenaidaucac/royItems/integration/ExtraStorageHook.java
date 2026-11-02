package com.github.orenaidaucac.royItems.integration;

import me.hsgamer.extrastorage.api.StorageAPI;
import me.hsgamer.extrastorage.api.item.Item;
import me.hsgamer.extrastorage.api.storage.Storage;
import me.hsgamer.extrastorage.api.user.User;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.util.Optional;

/**
 * Direct high-performance hook into ExtraStorage plugin for Crafting-Station ingredient resolution.
 * Supports querying and transactional deduction for both RoyItems materials and Vanilla items.
 */
public class ExtraStorageHook {

    private boolean available = false;

    public boolean setup() {
        if (Bukkit.getPluginManager().isPluginEnabled("ExtraStorage")) {
            try {
                Class.forName("me.hsgamer.extrastorage.api.StorageAPI");
                available = true;
                return true;
            } catch (Throwable t) {
                available = false;
                return false;
            }
        }
        available = false;
        return false;
    }

    public boolean isAvailable() {
        return available && Bukkit.getPluginManager().isPluginEnabled("ExtraStorage");
    }

    /**
     * Get item quantity from ExtraStorage for a player and specified storage key.
     */
    public int getAmount(Player player, Object key) {
        if (!isAvailable() || player == null || key == null) return 0;
        try {
            StorageAPI api = StorageAPI.getInstance();
            if (api == null) return 0;
            User user = api.getUser(player.getUniqueId());
            if (user == null) return 0;
            Storage storage = user.getStorage();
            if (storage == null) return 0;

            Optional<Item> itemOpt = storage.getItem(key);
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
            StorageAPI api = StorageAPI.getInstance();
            if (api == null) return false;
            User user = api.getUser(player.getUniqueId());
            if (user == null) return false;
            Storage storage = user.getStorage();
            if (storage == null) return false;

            Optional<Item> itemOpt = storage.getItem(key);
            if (!itemOpt.isPresent()) return false;
            Item item = itemOpt.get();
            if (item.getQuantity() < amount) return false;

            storage.subtract(key, amount);
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
            StorageAPI api = StorageAPI.getInstance();
            if (api == null) return;
            User user = api.getUser(player.getUniqueId());
            if (user == null) return;
            Storage storage = user.getStorage();
            if (storage == null) return;

            storage.add(key, amount);
            try {
                user.save();
            } catch (Throwable ignored) {}
        } catch (Throwable ignored) {}
    }
}
