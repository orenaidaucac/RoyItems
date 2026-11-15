package com.github.orenaidaucac.royItems.integration;

import org.bukkit.Bukkit;

/**
 * Citizens NPC Hook.
 */
public class CitizensHook {

    public boolean isAvailable() {
        if (Bukkit.getServer() == null || Bukkit.getPluginManager() == null) {
            return false;
        }
        return Bukkit.getPluginManager().isPluginEnabled("Citizens");
    }
}
