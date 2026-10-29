package com.github.orenaidaucac.royItems.mechanics;

import com.github.orenaidaucac.royItems.RoyItems;
import com.github.orenaidaucac.royItems.mechanics.packet.PacketEventsFilter;
import com.github.orenaidaucac.royItems.mechanics.packet.ParticleFilterHook;
import com.github.orenaidaucac.royItems.mechanics.packet.ProtocolLibFilter;
import org.bukkit.Bukkit;

/**
 * Manages client/server damage particle filtering.
 * Eliminates vanilla DAMAGE_INDICATOR heart particles during high-damage attacks
 * using PacketEvents or ProtocolLib to stop screen freeze/FPS drops.
 */
public class DamageParticleFilterManager {

    private final RoyItems plugin;
    private ParticleFilterHook activeHook;
    private boolean enabled = true;

    public DamageParticleFilterManager(RoyItems plugin) {
        this.plugin = plugin;
    }

    public void initialize() {
        loadConfig();
    }

    public void loadConfig() {
        this.enabled = plugin.getConfig().getBoolean("combat.disable-damage-heart-particles",
                plugin.getConfig().getBoolean("performance.disable-damage-heart-particles", true));

        // If previously hooked and now disabled, unregister
        if (!enabled) {
            if (activeHook != null) {
                activeHook.unregister();
                plugin.getLogger().info("[DamageParticles] Heart particle filter disabled in config.");
                activeHook = null;
            }
            return;
        }

        // Already hooked
        if (activeHook != null) {
            return;
        }

        // 1. Try PacketEvents first
        if (isPluginAvailable("packetevents") || isPluginAvailable("PacketEvents")) {
            try {
                activeHook = new PacketEventsFilter();
                activeHook.register();
                plugin.getLogger().info("[DamageParticles] Hooked into PacketEvents to filter client damage heart particles.");
                return;
            } catch (Throwable t) {
                plugin.getLogger().warning("[DamageParticles] Could not initialize PacketEvents filter: " + t.getMessage());
            }
        }

        // 2. Try ProtocolLib fallback
        if (isPluginAvailable("ProtocolLib")) {
            try {
                activeHook = new ProtocolLibFilter(plugin);
                activeHook.register();
                plugin.getLogger().info("[DamageParticles] Hooked into ProtocolLib to filter client damage heart particles.");
                return;
            } catch (Throwable t) {
                plugin.getLogger().warning("[DamageParticles] Could not initialize ProtocolLib filter: " + t.getMessage());
            }
        }

        plugin.getLogger().info("[DamageParticles] 'combat.disable-damage-heart-particles' is enabled. Install PacketEvents or ProtocolLib to eliminate client heart particles.");
    }

    public void disable() {
        if (activeHook != null) {
            activeHook.unregister();
            activeHook = null;
        }
    }

    public boolean isEnabled() {
        return enabled;
    }

    public boolean isHooked() {
        return activeHook != null;
    }

    public String getHookName() {
        return activeHook != null ? activeHook.getName() : "None";
    }

    private boolean isPluginAvailable(String name) {
        return Bukkit.getPluginManager().isPluginEnabled(name);
    }
}
