package com.github.orenaidaucac.royItems.integration;

import org.black_ixx.playerpoints.PlayerPoints;
import org.black_ixx.playerpoints.PlayerPointsAPI;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

/**
 * PlayerPoints Integration Hook.
 */
public class PlayerPointsHook {

    private PlayerPointsAPI api;

    public boolean setup() {
        if (Bukkit.getPluginManager().getPlugin("PlayerPoints") == null) {
            return false;
        }
        try {
            PlayerPoints pp = PlayerPoints.getInstance();
            if (pp != null) {
                this.api = pp.getAPI();
                return this.api != null;
            }
        } catch (Throwable ignored) {}
        return false;
    }

    public boolean isAvailable() {
        return api != null;
    }

    public int getPoints(Player player) {
        if (api == null || player == null) return 0;
        try {
            return api.look(player.getUniqueId());
        } catch (Throwable e) {
            return 0;
        }
    }

    public boolean hasPoints(Player player, int amount) {
        return getPoints(player) >= amount;
    }

    public boolean takePoints(Player player, int amount) {
        if (api == null || player == null || amount <= 0) return true;
        try {
            return api.take(player.getUniqueId(), amount);
        } catch (Throwable e) {
            return false;
        }
    }

    public boolean givePoints(Player player, int amount) {
        if (api == null || player == null || amount <= 0) return true;
        try {
            return api.give(player.getUniqueId(), amount);
        } catch (Throwable e) {
            return false;
        }
    }
}
