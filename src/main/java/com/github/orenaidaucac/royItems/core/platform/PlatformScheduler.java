package com.github.orenaidaucac.royItems.core.platform;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Entity;
import org.bukkit.plugin.Plugin;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

/**
 * Universal Platform Scheduler providing seamless compatibility between
 * Spigot, Paper, and Folia regionized multithreading.
 */
public class PlatformScheduler {

    private final Plugin plugin;
    private final boolean isFolia;

    public PlatformScheduler(Plugin plugin) {
        this.plugin = plugin;
        boolean foliaCheck = false;
        try {
            Class.forName("io.papermc.paper.threadedregions.RegionizedServer");
            foliaCheck = true;
        } catch (ClassNotFoundException ignored) {
            foliaCheck = false;
        }
        this.isFolia = foliaCheck;
    }

    public boolean isFolia() {
        return isFolia;
    }

    /**
     * Run a task asynchronously.
     */
    public void runAsync(Runnable runnable) {
        if (isFolia) {
            Bukkit.getAsyncScheduler().runNow(plugin, task -> runnable.run());
        } else {
            Bukkit.getScheduler().runTaskAsynchronously(plugin, runnable);
        }
    }

    /**
     * Run a task asynchronously with a delay.
     */
    public void runAsyncDelayed(Runnable runnable, long delayTicks) {
        if (isFolia) {
            long delayMillis = delayTicks * 50L;
            Bukkit.getAsyncScheduler().runDelayed(plugin, task -> runnable.run(), delayMillis, TimeUnit.MILLISECONDS);
        } else {
            Bukkit.getScheduler().runTaskLaterAsynchronously(plugin, runnable, delayTicks);
        }
    }

    /**
     * Run a recurring async task.
     */
    public void runAsyncTimer(Runnable runnable, long initialDelayTicks, long periodTicks) {
        if (isFolia) {
            Bukkit.getAsyncScheduler().runAtFixedRate(plugin,
                    task -> runnable.run(),
                    initialDelayTicks * 50L,
                    periodTicks * 50L,
                    TimeUnit.MILLISECONDS);
        } else {
            Bukkit.getScheduler().runTaskTimerAsynchronously(plugin, runnable, initialDelayTicks, periodTicks);
        }
    }

    /**
     * Run on the global thread (or main thread on Spigot/Paper).
     */
    public void runGlobal(Runnable runnable) {
        if (isFolia) {
            Bukkit.getGlobalRegionScheduler().run(plugin, task -> runnable.run());
        } else {
            Bukkit.getScheduler().runTask(plugin, runnable);
        }
    }

    /**
     * Run on the global thread with a delay.
     */
    public void runGlobalDelayed(Runnable runnable, long delayTicks) {
        if (isFolia) {
            Bukkit.getGlobalRegionScheduler().runDelayed(plugin, task -> runnable.run(), delayTicks);
        } else {
            Bukkit.getScheduler().runTaskLater(plugin, runnable, delayTicks);
        }
    }

    /**
     * Run a recurring global task.
     */
    public void runGlobalTimer(Runnable runnable, long initialDelayTicks, long periodTicks) {
        if (isFolia) {
            Bukkit.getGlobalRegionScheduler().runAtFixedRate(plugin, task -> runnable.run(), initialDelayTicks, periodTicks);
        } else {
            Bukkit.getScheduler().runTaskTimer(plugin, runnable, initialDelayTicks, periodTicks);
        }
    }

    /**
     * Run on the specific entity's scheduler (Entity region on Folia, main thread on Paper/Spigot).
     */
    public void runEntity(Entity entity, Runnable runnable) {
        if (entity == null || !entity.isValid()) return;
        if (isFolia) {
            entity.getScheduler().run(plugin, task -> runnable.run(), null);
        } else {
            Bukkit.getScheduler().runTask(plugin, runnable);
        }
    }

    /**
     * Run on entity's scheduler with delay.
     */
    public void runEntityDelayed(Entity entity, Runnable runnable, long delayTicks) {
        if (entity == null || !entity.isValid()) return;
        if (isFolia) {
            entity.getScheduler().runDelayed(plugin, task -> runnable.run(), null, delayTicks);
        } else {
            Bukkit.getScheduler().runTaskLater(plugin, runnable, delayTicks);
        }
    }

    /**
     * Run on the region containing the specified location.
     */
    public void runLocation(Location location, Runnable runnable) {
        if (location == null || location.getWorld() == null) return;
        if (isFolia) {
            Bukkit.getRegionScheduler().run(plugin, location, task -> runnable.run());
        } else {
            Bukkit.getScheduler().runTask(plugin, runnable);
        }
    }

    /**
     * Cancel all tasks for this plugin when disabling or reloading.
     */
    public void cancelAllTasks() {
        if (isFolia) {
            Bukkit.getAsyncScheduler().cancelTasks(plugin);
            Bukkit.getGlobalRegionScheduler().cancelTasks(plugin);
        } else {
            Bukkit.getScheduler().cancelTasks(plugin);
        }
    }
}
