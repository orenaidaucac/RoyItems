package com.github.orenaidaucac.royItems.integration;

import org.bukkit.Bukkit;

/**
 * FancyNpcs Hook.
 */
public class FancyNpcsHook {

    public boolean isAvailable() {
        return Bukkit.getPluginManager().isPluginEnabled("FancyNpcs");
    }
}
