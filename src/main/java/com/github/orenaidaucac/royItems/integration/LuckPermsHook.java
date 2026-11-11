package com.github.orenaidaucac.royItems.integration;

import com.github.orenaidaucac.royItems.core.platform.PlatformScheduler;
import com.github.orenaidaucac.royItems.stat.PermissionStatManager;
import com.github.orenaidaucac.royItems.stat.StatManager;
import net.luckperms.api.LuckPerms;
import net.luckperms.api.LuckPermsProvider;
import net.luckperms.api.event.EventBus;
import net.luckperms.api.event.EventSubscription;
import net.luckperms.api.event.user.UserDataRecalculateEvent;
import net.luckperms.api.model.user.User;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

import java.util.UUID;
import java.util.logging.Level;

/**
 * High-performance, real-time LuckPerms Event Integration.
 * Subscribes to UserDataRecalculateEvent to detect permission node changes, group inheritance shifts,
 * context updates, and temporary permission expirations without requiring server reload or player reconnect.
 */
public class LuckPermsHook {

    private boolean available = false;
    private EventSubscription<UserDataRecalculateEvent> subscription;

    public boolean setup(Plugin plugin, StatManager statManager, PermissionStatManager permissionStatManager, PlatformScheduler scheduler) {
        try {
            if (Bukkit.getServer() == null || Bukkit.getPluginManager() == null) {
                this.available = false;
                return false;
            }
            Plugin lpPlugin = Bukkit.getPluginManager().getPlugin("LuckPerms");
            if (lpPlugin == null || !lpPlugin.isEnabled()) {
                this.available = false;
                return false;
            }

            LuckPerms luckPerms = LuckPermsProvider.get();
            if (luckPerms == null) {
                this.available = false;
                return false;
            }

            EventBus eventBus = luckPerms.getEventBus();
            this.subscription = eventBus.subscribe(plugin, UserDataRecalculateEvent.class, event -> {
                User user = event.getUser();
                if (user == null) return;
                UUID uuid = user.getUniqueId();

                Player player = Bukkit.getPlayer(uuid);
                if (player != null && player.isOnline()) {
                    if (permissionStatManager != null) {
                        permissionStatManager.invalidate(uuid);
                    }
                    if (scheduler != null && statManager != null) {
                        scheduler.runEntity(player, () -> {
                            if (player.isOnline()) {
                                statManager.recalculate(player);
                            }
                        });
                    }
                }
            });

            this.available = true;
            return true;
        } catch (Throwable t) {
            this.available = false;
            return false;
        }
    }

    public boolean isAvailable() {
        return available;
    }

    /**
     * Unsubscribes from the LuckPerms EventBus to prevent listener leaks.
     */
    public void cleanup() {
        if (subscription != null) {
            try {
                subscription.close();
            } catch (Throwable ignored) {}
            subscription = null;
        }
        available = false;
    }
}
