package com.github.orenaidaucac.royItems.integration;

import org.bukkit.Bukkit;

/**
 * Citizens NPC Hook.
 */
public class CitizensHook {

    public boolean isAvailable() {
        return Bukkit.getPluginManager().isPluginEnabled("Citizens");
    }
}
