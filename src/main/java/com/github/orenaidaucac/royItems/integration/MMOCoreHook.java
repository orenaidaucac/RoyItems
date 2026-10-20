package com.github.orenaidaucac.royItems.integration;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.lang.reflect.Method;

/**
 * MMOCore integration for Class, Level, Mana, and Stamina checks.
 */
public class MMOCoreHook {

    private boolean available = false;
    private Class<?> playerDataClass;
    private Method getPlayerDataMethod;

    public boolean setup() {
        if (Bukkit.getPluginManager().getPlugin("MMOCore") == null) return false;
        try {
            playerDataClass = Class.forName("net.Indyuce.mmocore.api.player.PlayerData");
            getPlayerDataMethod = playerDataClass.getMethod("get", Player.class);
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

    public boolean meetsRequirement(Player player, String requiredClass, int requiredLevel) {
        if (!available || player == null) return true;
        try {
            Object playerData = getPlayerDataMethod.invoke(null, player);
            if (playerData == null) return true;

            // Check level
            if (requiredLevel > 0) {
                Method getLevelMethod = playerDataClass.getMethod("getLevel");
                int level = (int) getLevelMethod.invoke(playerData);
                if (level < requiredLevel) return false;
            }

            // Check class
            if (requiredClass != null && !requiredClass.isEmpty()) {
                Method getProfMethod = playerDataClass.getMethod("getProfess");
                Object profess = getProfMethod.invoke(playerData);
                if (profess != null) {
                    Method getIdMethod = profess.getClass().getMethod("getId");
                    String classId = (String) getIdMethod.invoke(profess);
                    if (!requiredClass.equalsIgnoreCase(classId)) return false;
                }
            }

            return true;
        } catch (Throwable t) {
            return true;
        }
    }
}
