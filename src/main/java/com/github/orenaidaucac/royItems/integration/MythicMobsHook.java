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
 * MythicMobs hook supporting custom skill casting, robust multi-layer mob identification,
 * and dynamic skill damage mitigation (MythicDamageEvent) with accurate defense caching
 * and zero double-mitigation.
 */
public class MythicMobsHook {

    public static class MitigatedHit {
        private final long timestamp;
        private final double mitigatedDamage;

        public MitigatedHit(long timestamp, double mitigatedDamage) {
            this.timestamp = timestamp;
            this.mitigatedDamage = mitigatedDamage;
        }

        public long getTimestamp() {
            return timestamp;
        }

        public double getMitigatedDamage() {
            return mitigatedDamage;
        }
    }

    private boolean available = false;
    private Object apiHelper;
    private Object mobManager;
    private Method castSkillMethod;

    // Methods for isMythicMob detection
    private Method apiIsMythicMobMethod;
    private Method mobManagerIsMythicMobMethod;
    private Method getActiveMobMethod;
    private Method getMythicMobInstanceMethod;
    private Method isActiveMobMethod;

    private DefenseManager defenseManager;
    private boolean listenerRegistered = false;

    // Reflection handles for MythicDamageEvent
    private Method getDamageMethod;
    private Method setDamageMethod;
    private Method getTargetMethod;
    private Method getEntityMethod;
    private Method getCasterMethod;
    private Method getDamagerMethod;
    private Method isCancelledMethod;
    private Method getDamageMetadataMethod;
    private Method metadataGetAmountMethod;
    private Method metadataSetAmountMethod;

    // Track players recently mitigated in MythicDamageEvent with exact mitigated damage values
    private final Map<UUID, MitigatedHit> recentMitigations = new ConcurrentHashMap<>();

    public boolean setup() {
        try {
            if (Bukkit.getServer() == null || Bukkit.getPluginManager() == null || Bukkit.getPluginManager().getPlugin("MythicMobs") == null) {
                available = false;
                return false;
            }
        } catch (Throwable t) {
            available = false;
            return false;
        }
        try {
            Class<?> mythicBukkitClass = Class.forName("io.lumine.mythic.bukkit.MythicBukkit");
            Method instMethod = mythicBukkitClass.getMethod("inst");
            Object mythicInstance = instMethod.invoke(null);

            // 1. API Helper (standard entry point in MythicMobs 5+)
            try {
                Method getApiMethod = mythicBukkitClass.getMethod("getAPIHelper");
                apiHelper = getApiMethod.invoke(mythicInstance);
                if (apiHelper != null) {
                    try {
                        castSkillMethod = apiHelper.getClass().getMethod("castSkill", Entity.class, String.class);
                    } catch (NoSuchMethodException ignored) {}
                    try {
                        apiIsMythicMobMethod = apiHelper.getClass().getMethod("isMythicMob", Entity.class);
                    } catch (NoSuchMethodException ignored) {}
                }
            } catch (Throwable ignored) {}

            // 2. MobManager (internal fallback entry point)
            try {
                Method getMobManagerMethod = mythicBukkitClass.getMethod("getMobManager");
                mobManager = getMobManagerMethod.invoke(mythicInstance);
                if (mobManager != null) {
                    try {
                        mobManagerIsMythicMobMethod = mobManager.getClass().getMethod("isMythicMob", Entity.class);
                    } catch (NoSuchMethodException ignored) {}
                    try {
                        getMythicMobInstanceMethod = mobManager.getClass().getMethod("getMythicMobInstance", Entity.class);
                    } catch (NoSuchMethodException ignored) {}
                    try {
                        getActiveMobMethod = mobManager.getClass().getMethod("getActiveMob", UUID.class);
                    } catch (NoSuchMethodException ignored) {}
                    try {
                        isActiveMobMethod = mobManager.getClass().getMethod("isActiveMob", UUID.class);
                    } catch (NoSuchMethodException ignored) {}
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
        if (!available || caster == null || skillName == null || castSkillMethod == null || apiHelper == null) return false;
        try {
            return (boolean) castSkillMethod.invoke(apiHelper, caster, skillName);
        } catch (Throwable t) {
            return false;
        }
    }

    /**
     * Checks if a Bukkit entity is a valid MythicMob instance using multi-layer reflection.
     */
    public boolean isMythicMob(Entity entity) {
        if (!available || entity == null) return false;

        // Layer 1: BukkitAPIHelper isMythicMob(Entity) (Official API)
        if (apiIsMythicMobMethod != null && apiHelper != null) {
            try {
                return (boolean) apiIsMythicMobMethod.invoke(apiHelper, entity);
            } catch (Throwable ignored) {}
        }

        // Layer 2: MobManager isMythicMob(Entity)
        if (mobManagerIsMythicMobMethod != null && mobManager != null) {
            try {
                return (boolean) mobManagerIsMythicMobMethod.invoke(mobManager, entity);
            } catch (Throwable ignored) {}
        }

        // Layer 3: MobManager getMythicMobInstance(Entity) != null
        if (getMythicMobInstanceMethod != null && mobManager != null) {
            try {
                if (getMythicMobInstanceMethod.invoke(mobManager, entity) != null) return true;
            } catch (Throwable ignored) {}
        }

        // Layer 4: MobManager getActiveMob(UUID) != null
        if (getActiveMobMethod != null && mobManager != null) {
            try {
                if (getActiveMobMethod.invoke(mobManager, entity.getUniqueId()) != null) return true;
            } catch (Throwable ignored) {}
        }

        // Layer 5: MobManager isActiveMob(UUID)
        if (isActiveMobMethod != null && mobManager != null) {
            try {
                return (boolean) isActiveMobMethod.invoke(mobManager, entity.getUniqueId());
            } catch (Throwable ignored) {}
        }

        return false;
    }

    /**
     * Registers a dynamic listener for MythicDamageEvent to mitigate incoming skill mechanic damage
     * before it bypasses or skews standard Bukkit combat events.
     * Uses EventPriority.HIGHEST to ensure defense is calculated on the finalized damage.
     */
    @SuppressWarnings("unchecked")
    public void registerDamageListener(Plugin plugin, DefenseManager defenseManager) {
        this.defenseManager = defenseManager;
        if (!available || plugin == null || defenseManager == null || listenerRegistered) return;

        try {
            Class<? extends Event> mythicDamageEventClass =
                    (Class<? extends Event>) Class.forName("io.lumine.mythic.bukkit.events.MythicDamageEvent");

            // Damage accessor
            try {
                getDamageMethod = mythicDamageEventClass.getMethod("getDamage");
            } catch (NoSuchMethodException ignored) {}

            // Damage setter (double or float)
            try {
                setDamageMethod = mythicDamageEventClass.getMethod("setDamage", double.class);
            } catch (NoSuchMethodException e) {
                try {
                    setDamageMethod = mythicDamageEventClass.getMethod("setDamage", float.class);
                } catch (NoSuchMethodException ignored) {}
            }

            // Target victim accessors: getTarget() or getEntity() or getVictim()
            try {
                getTargetMethod = mythicDamageEventClass.getMethod("getTarget");
            } catch (NoSuchMethodException e) {
                try {
                    getEntityMethod = mythicDamageEventClass.getMethod("getEntity");
                } catch (NoSuchMethodException ignored) {}
            }
            if (getTargetMethod == null && getEntityMethod == null) {
                try {
                    getEntityMethod = mythicDamageEventClass.getMethod("getVictim");
                } catch (NoSuchMethodException ignored) {}
            }

            // Attacker / Caster accessors: getCaster() or getDamager()
            try {
                getCasterMethod = mythicDamageEventClass.getMethod("getCaster");
            } catch (NoSuchMethodException e) {
                try {
                    getDamagerMethod = mythicDamageEventClass.getMethod("getDamager");
                } catch (NoSuchMethodException ignored) {}
            }

            // Cancellation
            try {
                isCancelledMethod = mythicDamageEventClass.getMethod("isCancelled");
            } catch (NoSuchMethodException ignored) {}

            // DamageMetadata fallback for advanced Mythic versions
            try {
                getDamageMetadataMethod = mythicDamageEventClass.getMethod("getDamageMetadata");
                if (getDamageMetadataMethod != null) {
                    Class<?> metadataClass = getDamageMetadataMethod.getReturnType();
                    try {
                        metadataGetAmountMethod = metadataClass.getMethod("getAmount");
                    } catch (NoSuchMethodException ignored) {}
                    try {
                        metadataSetAmountMethod = metadataClass.getMethod("setAmount", double.class);
                    } catch (NoSuchMethodException ignored) {}
                }
            } catch (Throwable ignored) {}

            Bukkit.getPluginManager().registerEvent(
                    mythicDamageEventClass,
                    new Listener() {},
                    EventPriority.HIGHEST,
                    (listener, event) -> handleMythicDamage(event),
                    plugin
            );

            this.listenerRegistered = true;
            plugin.getLogger().info("[MythicMobsHook] Successfully registered MythicDamageEvent listener for RoyItems defense (HIGHEST priority).");
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

            // 1. Resolve Target Victim Player
            Object targetObj = null;
            if (getTargetMethod != null) {
                try {
                    targetObj = getTargetMethod.invoke(rawEvent);
                } catch (Throwable ignored) {}
            }
            if (targetObj == null && getEntityMethod != null) {
                try {
                    targetObj = getEntityMethod.invoke(rawEvent);
                } catch (Throwable ignored) {}
            }

            Entity victim = extractBukkitEntity(targetObj);
            if (!(victim instanceof Player player)) {
                return; // Defense applies to players receiving damage
            }

            // 2. Resolve Attacker / Caster
            Object casterObj = null;
            if (getCasterMethod != null) {
                try {
                    casterObj = getCasterMethod.invoke(rawEvent);
                } catch (Throwable ignored) {}
            }
            if (casterObj == null && getDamagerMethod != null) {
                try {
                    casterObj = getDamagerMethod.invoke(rawEvent);
                } catch (Throwable ignored) {}
            }
            Entity attacker = extractBukkitEntity(casterObj);

            boolean isPvP = (attacker instanceof Player);
            boolean isPvE = !isPvP;

            // 3. Resolve Raw Damage
            double rawDamage = 0.0;
            boolean damageFound = false;
            if (getDamageMethod != null) {
                try {
                    rawDamage = ((Number) getDamageMethod.invoke(rawEvent)).doubleValue();
                    damageFound = true;
                } catch (Throwable ignored) {}
            }
            if (!damageFound && getDamageMetadataMethod != null && metadataGetAmountMethod != null) {
                try {
                    Object metadata = getDamageMetadataMethod.invoke(rawEvent);
                    if (metadata != null) {
                        rawDamage = ((Number) metadataGetAmountMethod.invoke(metadata)).doubleValue();
                        damageFound = true;
                    }
                } catch (Throwable ignored) {}
            }

            if (!damageFound || rawDamage <= 0.0) {
                return;
            }

            // 4. Calculate Effective Defense & Apply MMORPG / Configured Defense Mode
            double effectiveDefense = defenseManager.calculateEffectiveDefense(player, isPvP, isPvE);
            double finalDamage = defenseManager.applyDefense(rawDamage, effectiveDefense);
            finalDamage = defenseManager.applyDamageReduction(player, finalDamage);

            // Safety clamp
            if (Double.isNaN(finalDamage) || Double.isInfinite(finalDamage) || finalDamage < 0) {
                finalDamage = 0.0;
            }

            // 5. Update damage back to the MythicDamageEvent
            if (setDamageMethod != null) {
                try {
                    if (setDamageMethod.getParameterTypes()[0] == float.class) {
                        setDamageMethod.invoke(rawEvent, (float) finalDamage);
                    } else {
                        setDamageMethod.invoke(rawEvent, finalDamage);
                    }
                } catch (Throwable ignored) {}
            }
            if (getDamageMetadataMethod != null && metadataSetAmountMethod != null) {
                try {
                    Object metadata = getDamageMetadataMethod.invoke(rawEvent);
                    if (metadata != null) {
                        metadataSetAmountMethod.invoke(metadata, finalDamage);
                    }
                } catch (Throwable ignored) {}
            }

            // 6. Cache exact mitigated damage for the player to avoid double mitigation in Bukkit event
            markMitigated(player.getUniqueId(), finalDamage);

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

        // 3. io.lumine.mythic.bukkit.BukkitAdapter.adapt(AbstractEntity)
        try {
            Class<?> adapterClass = Class.forName("io.lumine.mythic.bukkit.BukkitAdapter");
            Method adaptMethod = adapterClass.getMethod("adapt", obj.getClass());
            Object res = adaptMethod.invoke(null, obj);
            if (res instanceof Entity entity) return entity;
        } catch (Throwable ignored) {}

        return null;
    }

    public void markMitigated(UUID uuid) {
        markMitigated(uuid, 0.0);
    }

    public void markMitigated(UUID uuid, double mitigatedDamage) {
        if (uuid != null) {
            recentMitigations.put(uuid, new MitigatedHit(System.currentTimeMillis(), mitigatedDamage));
        }
    }

    public boolean isRecentlyMitigated(UUID uuid) {
        if (uuid == null) return false;
        MitigatedHit hit = recentMitigations.get(uuid);
        if (hit == null) return false;
        if (System.currentTimeMillis() - hit.getTimestamp() > 200) {
            recentMitigations.remove(uuid);
            return false;
        }
        return true;
    }

    /**
     * Retrieves the previously calculated mitigated damage value and clears the cache entry.
     * Returns null if no valid mitigation occurred within the last 200ms.
     */
    public Double consumeMitigatedDamage(UUID uuid) {
        if (uuid == null) return null;
        MitigatedHit hit = recentMitigations.remove(uuid);
        if (hit == null) return null;
        if (System.currentTimeMillis() - hit.getTimestamp() > 200) {
            return null;
        }
        return hit.getMitigatedDamage();
    }

    public void clearMitigation(UUID uuid) {
        if (uuid != null) {
            recentMitigations.remove(uuid);
        }
    }

    public boolean isListenerRegistered() {
        return listenerRegistered;
    }
}
