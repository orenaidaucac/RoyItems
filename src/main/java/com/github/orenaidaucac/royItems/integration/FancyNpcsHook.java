package com.github.orenaidaucac.royItems.integration;

import org.bukkit.Bukkit;

/**
 * FancyNpcs Hook.
 */
public class FancyNpcsHook {

    public boolean isAvailable() {
        if (Bukkit.getServer() == null || Bukkit.getPluginManager() == null) {
            return false;
        }
        return Bukkit.getPluginManager().isPluginEnabled("FancyNpcs");
    }
}
