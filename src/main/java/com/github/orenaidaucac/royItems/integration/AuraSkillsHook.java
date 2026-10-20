package com.github.orenaidaucac.royItems.integration;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.lang.reflect.Method;

/**
 * AuraSkills (formerly AureliumSkills) integration for Skill Level checks.
 */
public class AuraSkillsHook {

    private boolean available = false;
    private Object auraSkillsApi;
    private Method getUserMethod;

    public boolean setup() {
        if (Bukkit.getPluginManager().getPlugin("AuraSkills") == null) return false;
        try {
            Class<?> apiClass = Class.forName("dev.aurelium.auraskills.api.AuraSkillsApi");
            Method getMethod = apiClass.getMethod("get");
            auraSkillsApi = getMethod.invoke(null);
            getUserMethod = auraSkillsApi.getClass().getMethod("getUser", java.util.UUID.class);
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

    public boolean meetsRequirement(Player player, String skillName, int requiredLevel) {
        if (!available || player == null || skillName == null) return true;
        try {
            Object user = getUserMethod.invoke(auraSkillsApi, player.getUniqueId());
            if (user == null) return true;

            // Get skill registry
            Method getSkillLevelMethod = user.getClass().getMethod("getSkillLevel", String.class);
            int level = (int) getSkillLevelMethod.invoke(user, skillName.toLowerCase());
            return level >= requiredLevel;
        } catch (Throwable t) {
            return true;
        }
    }
}
