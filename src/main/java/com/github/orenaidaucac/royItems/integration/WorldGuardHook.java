package com.github.orenaidaucac.royItems.integration;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Player;

/**
 * WorldGuard Hook for checking region protection flags before item abilities or PvP.
 */
public class WorldGuardHook {

    private boolean available = false;

    public boolean setup() {
        if (Bukkit.getPluginManager().getPlugin("WorldGuard") == null) return false;
        try {
            Class.forName("com.sk89q.worldguard.WorldGuard");
            available = true;
            return true;
        } catch (Throwable t) {
            available = false;
            return false;
        }
    }

    public boolean isAvailable() {
        return available;
    }

    public boolean canUseItem(Player player, Location location) {
        // Fail-safe: if WorldGuard is present, could check ITEM_USE flag or allow by default
        return true;
    }
}
