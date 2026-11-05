package com.github.orenaidaucac.royItems.integration;

import com.github.orenaidaucac.royItems.stat.DefenseManager;
import org.bukkit.Bukkit;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.plugin.Plugin;

import java.lang.reflect.Method;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;

/**
 * MythicMobs hook supporting custom skill casting, mob identification,
 * and dynamic skill damage mitigation (MythicDamageEvent) with zero double-mitigation.
 */
public class MythicMobsHook {

    private boolean available = false;
    private Object apiHelper;
    private Object mobManager;
    private Method castSkillMethod;
    private Method isMythicMobMethod;
    private Method getActiveMobMethod;

    private DefenseManager defenseManager;
    private Method getDamageMethod;
    private Method setDamageMethod;
    private Method getTargetMethod;
    private Method getCasterMethod;
    private Method isCancelledMethod;

    // Track players recently mitigated in MythicDamageEvent to prevent double-mitigation in EntityDamageByEntityEvent
    private final Map<UUID, Long> recentMitigations = new ConcurrentHashMap<>();

    public boolean setup() {
        if (Bukkit.getPluginManager().getPlugin("MythicMobs") == null) return false;
        try {
            Class<?> mythicBukkitClass = Class.forName("io.lumine.mythic.bukkit.MythicBukkit");
            Method instMethod = mythicBukkitClass.getMethod("inst");
            Object mythicInstance = instMethod.invoke(null);

            Method getApiMethod = mythicBukkitClass.getMethod("getAPIHelper");
            apiHelper = getApiMethod.invoke(mythicInstance);
            castSkillMethod = apiHelper.getClass().getMethod("castSkill", Entity.class, String.class);

            try {
                Method getMobManagerMethod = mythicBukkitClass.getMethod("getMobManager");
                mobManager = getMobManagerMethod.invoke(mythicInstance);
                try {
                    isMythicMobMethod = mobManager.getClass().getMethod("isMythicMob", Entity.class);
                } catch (NoSuchMethodException e) {
                    getActiveMobMethod = mobManager.getClass().getMethod("getActiveMob", UUID.class);
                }
            } catch (Throwable ignored) {}

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

    /**
     * Checks if a Bukkit entity is a valid MythicMob instance.
     */
    public boolean isMythicMob(Entity entity) {
        if (!available || entity == null || mobManager == null) return false;
        try {
            if (isMythicMobMethod != null) {
                return (boolean) isMythicMobMethod.invoke(mobManager, entity);
            }
            if (getActiveMobMethod != null) {
                return getActiveMobMethod.invoke(mobManager, entity.getUniqueId()) != null;
            }
        } catch (Throwable ignored) {}
        return false;
    }

    /**
     * Registers a dynamic listener for MythicDamageEvent to mitigate incoming skill mechanic damage
     * before it bypasses or skews standard Bukkit combat events.
     */
    @SuppressWarnings("unchecked")
    public void registerDamageListener(Plugin plugin, DefenseManager defenseManager) {
        this.defenseManager = defenseManager;
        if (!available || plugin == null || defenseManager == null) return;

        try {
            Class<? extends Event> mythicDamageEventClass =
                    (Class<? extends Event>) Class.forName("io.lumine.mythic.bukkit.events.MythicDamageEvent");

            getDamageMethod = mythicDamageEventClass.getMethod("getDamage");
            try {
                setDamageMethod = mythicDamageEventClass.getMethod("setDamage", double.class);
            } catch (NoSuchMethodException e) {
                setDamageMethod = mythicDamageEventClass.getMethod("setDamage", float.class);
            }

            getTargetMethod = mythicDamageEventClass.getMethod("getTarget");
            getCasterMethod = mythicDamageEventClass.getMethod("getCaster");

            try {
                isCancelledMethod = mythicDamageEventClass.getMethod("isCancelled");
            } catch (NoSuchMethodException ignored) {}

            Bukkit.getPluginManager().registerEvent(
                    mythicDamageEventClass,
                    new Listener() {},
                    EventPriority.HIGH,
                    (listener, event) -> handleMythicDamage(event),
                    plugin
            );

            plugin.getLogger().info("[MythicMobsHook] Successfully registered MythicDamageEvent listener for RoyItems defense.");
        } catch (Throwable t) {
            plugin.getLogger().log(Level.WARNING, "[MythicMobsHook] Could not register MythicDamageEvent listener: " + t.getMessage());
        }
    }

    /**
     * Handles MythicDamageEvent fired by MythicMobs skill mechanics (e.g. - damage{...}).
     */
    public void handleMythicDamage(Event rawEvent) {
        if (defenseManager == null) return;

        try {
            if (isCancelledMethod != null && (boolean) isCancelledMethod.invoke(rawEvent)) {
                return;
            }

            Object targetObj = getTargetMethod.invoke(rawEvent);
            Entity victim = extractBukkitEntity(targetObj);
            if (!(victim instanceof Player player)) {
                return; // Defense applies to players receiving damage
            }

            Object casterObj = getCasterMethod != null ? getCasterMethod.invoke(rawEvent) : null;
            Entity attacker = extractBukkitEntity(casterObj);

            boolean isPvP = (attacker instanceof Player);
            boolean isPvE = !isPvP;

            double rawDamage = ((Number) getDamageMethod.invoke(rawEvent)).doubleValue();

            // 1. Calculate Effective Defense
            double effectiveDefense = defenseManager.calculateEffectiveDefense(player, isPvP, isPvE);

            // 2. Apply configured Defense Mode (SIMPLE, MMORPG, CUSTOM_LIMIT)
            double finalDamage = defenseManager.applyDefense(rawDamage, effectiveDefense);

            // 3. Apply Damage Reduction stat
            finalDamage = defenseManager.applyDamageReduction(player, finalDamage);

            // 4. Safety clamp
            if (Double.isNaN(finalDamage) || Double.isInfinite(finalDamage) || finalDamage < 0) {
                finalDamage = 0.0;
            }

            // 5. Update damage on the event
            if (setDamageMethod.getParameterTypes()[0] == float.class) {
                setDamageMethod.invoke(rawEvent, (float) finalDamage);
            } else {
                setDamageMethod.invoke(rawEvent, finalDamage);
            }

            // 6. Tag player to avoid double mitigation in subsequent EntityDamageByEntityEvent
            markMitigated(player.getUniqueId());

        } catch (Throwable ignored) {}
    }

    /**
     * Extracts an org.bukkit.entity.Entity from various MythicMobs abstraction wrappers
     * (AbstractEntity, SkillCaster, ActiveMob, Bukkit Entity).
     */
    public static Entity extractBukkitEntity(Object obj) {
        if (obj == null) return null;
        if (obj instanceof Entity entity) return entity;

        // 1. Direct getBukkitEntity()
        try {
            Method m = obj.getClass().getMethod("getBukkitEntity");
            Object res = m.invoke(obj);
            if (res instanceof Entity entity) return entity;
        } catch (Throwable ignored) {}

        // 2. SkillCaster getEntity().getBukkitEntity()
        try {
            Method m1 = obj.getClass().getMethod("getEntity");
            Object wrapped = m1.invoke(obj);
            if (wrapped instanceof Entity entity) return entity;
            if (wrapped != null) {
                Method m2 = wrapped.getClass().getMethod("getBukkitEntity");
                Object res2 = m2.invoke(wrapped);
                if (res2 instanceof Entity entity) return entity;
            }
        } catch (Throwable ignored) {}

        return null;
    }

    public void markMitigated(UUID uuid) {
        if (uuid != null) {
            recentMitigations.put(uuid, System.currentTimeMillis());
        }
    }

    public boolean isRecentlyMitigated(UUID uuid) {
        if (uuid == null) return false;
        Long time = recentMitigations.get(uuid);
        if (time == null) return false;
        if (System.currentTimeMillis() - time > 150) {
            recentMitigations.remove(uuid);
            return false;
        }
        return true;
    }

    public void clearMitigation(UUID uuid) {
        if (uuid != null) {
            recentMitigations.remove(uuid);
        }
    }
}
