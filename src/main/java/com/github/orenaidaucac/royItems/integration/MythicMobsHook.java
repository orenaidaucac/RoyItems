package com.github.orenaidaucac.royItems.integration;

import org.bukkit.Bukkit;
import org.bukkit.entity.Entity;

import java.lang.reflect.Method;

/**
 * MythicMobs hook for triggering custom skills from RoyItems weapons/staffs.
 */
public class MythicMobsHook {

    private boolean available = false;
    private Object apiHelper;
    private Method castSkillMethod;

    public boolean setup() {
        if (Bukkit.getPluginManager().getPlugin("MythicMobs") == null) return false;
        try {
            Class<?> mythicBukkitClass = Class.forName("io.lumine.mythic.bukkit.MythicBukkit");
            Method instMethod = mythicBukkitClass.getMethod("inst");
            Object mythicInstance = instMethod.invoke(null);
            Method getApiMethod = mythicBukkitClass.getMethod("getAPIHelper");
            apiHelper = getApiMethod.invoke(mythicInstance);

            castSkillMethod = apiHelper.getClass().getMethod("castSkill", Entity.class, String.class);
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

    public boolean castSkill(Entity caster, String skillName) {
        if (!available || caster == null || skillName == null) return false;
        try {
            return (boolean) castSkillMethod.invoke(apiHelper, caster, skillName);
        } catch (Throwable t) {
            return false;
        }
    }
}
