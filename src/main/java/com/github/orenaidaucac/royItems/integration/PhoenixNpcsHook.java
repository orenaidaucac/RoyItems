package com.github.orenaidaucac.royItems.integration;

import com.phoenixplugins.phoenixnpcs.api.PhoenixNPCsAPI;
import org.bukkit.Bukkit;

/**
 * PhoenixNPCs Hook.
 */
public class PhoenixNpcsHook {

    public boolean isAvailable() {
        if (Bukkit.getServer() == null || Bukkit.getPluginManager() == null) {
            return false;
        }
        if (!Bukkit.getPluginManager().isPluginEnabled("PhoenixNPCs")
                && !Bukkit.getPluginManager().isPluginEnabled("phoenixnpcs")) {
            return false;
        }
        try {
            return PhoenixNPCsAPI.isAvailable();
        } catch (Throwable ignored) {
            return true;
        }
    }
}
