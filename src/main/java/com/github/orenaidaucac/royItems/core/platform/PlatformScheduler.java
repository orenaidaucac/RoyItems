package com.github.orenaidaucac.royItems.core.platform;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Entity;
import org.bukkit.plugin.Plugin;

import java.lang.reflect.Method;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

/**
 * Universal Platform Scheduler providing seamless compatibility between
 * Bukkit, Spigot, Paper, Purpur, Leaf, and Folia regionized multithreading.
 */
public class PlatformScheduler {

    private final Plugin plugin;
    private final boolean isFolia;
    private final Method isOwnedByCurrentRegionMethod;

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

        Method ownedMethod = null;
        try {
            ownedMethod = Bukkit.class.getMethod("isOwnedByCurrentRegion", Entity.class);
        } catch (Throwable ignored) {}
        this.isOwnedByCurrentRegionMethod = ownedMethod;
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
            Bukkit.getGlobalRegionScheduler().runAtFixedRate(plugin, task -> runnable.run(),
                    Math.max(1L, initialDelayTicks), Math.max(1L, periodTicks));
        } else {
            Bukkit.getScheduler().runTaskTimer(plugin, runnable, Math.max(0L, initialDelayTicks), Math.max(1L, periodTicks));
        }
    }

    /**
     * Run a recurring global task returning a cancellable TaskHandle.
     */
    public TaskHandle runGlobalTimerHandle(Consumer<TaskHandle> consumer, long initialDelayTicks, long periodTicks) {
        if (consumer == null || plugin == null) return () -> {};
        if (isFolia) {
            TaskHandle[] handleRef = new TaskHandle[1];
            io.papermc.paper.threadedregions.scheduler.ScheduledTask st =
                    Bukkit.getGlobalRegionScheduler().runAtFixedRate(
                            plugin,
                            task -> consumer.accept(handleRef[0]),
                            Math.max(1L, initialDelayTicks),
                            Math.max(1L, periodTicks)
                    );
            handleRef[0] = () -> {
                if (st != null) st.cancel();
            };
            return handleRef[0];
        } else {
            TaskHandle[] handleRef = new TaskHandle[1];
            org.bukkit.scheduler.BukkitTask bt = Bukkit.getScheduler().runTaskTimer(
                    plugin,
                    () -> consumer.accept(handleRef[0]),
                    Math.max(0L, initialDelayTicks),
                    Math.max(1L, periodTicks)
            );
            handleRef[0] = () -> {
                if (bt != null) bt.cancel();
            };
            return handleRef[0];
        }
    }

    /**
     * Run on entity's scheduler with delay and return a cancellable TaskHandle.
     */
    public TaskHandle runEntityDelayedHandle(Entity entity, Runnable runnable, long delayTicks) {
        if (entity == null || runnable == null || plugin == null) return () -> {};
        long safeDelay = Math.max(0L, delayTicks);
        if (isFolia) {
            io.papermc.paper.threadedregions.scheduler.ScheduledTask st = null;
            if (entity.isValid()) {
                if (safeDelay == 0L) {
                    st = entity.getScheduler().run(plugin, task -> runnable.run(), null);
                } else {
                    st = entity.getScheduler().runDelayed(plugin, task -> runnable.run(), null, safeDelay);
                }
            } else if (entity.getLocation().getWorld() != null) {
                if (safeDelay == 0L) {
                    st = Bukkit.getRegionScheduler().run(plugin, entity.getLocation(), task -> runnable.run());
                } else {
                    st = Bukkit.getRegionScheduler().runDelayed(plugin, entity.getLocation(), task -> runnable.run(), safeDelay);
                }
            }
            final io.papermc.paper.threadedregions.scheduler.ScheduledTask finalSt = st;
            return () -> {
                if (finalSt != null) finalSt.cancel();
            };
        } else {
            org.bukkit.scheduler.BukkitTask bt = safeDelay == 0L
                    ? Bukkit.getScheduler().runTask(plugin, runnable)
                    : Bukkit.getScheduler().runTaskLater(plugin, runnable, safeDelay);
            return () -> {
                if (bt != null) bt.cancel();
            };
        }
    }

    /**
     * Checks whether the calling thread currently owns the given entity
     * (main thread on Bukkit/Spigot/Paper/Purpur/Leaf, or entity's region thread on Folia).
     */
    public boolean isOwnedByCurrentThread(Entity entity) {
        if (entity == null) return false;
        try {
            if (Bukkit.getServer() == null) return true;
        } catch (Throwable ignored) {}

        if (isFolia) {
            if (isOwnedByCurrentRegionMethod != null) {
                try {
                    return Boolean.TRUE.equals(isOwnedByCurrentRegionMethod.invoke(null, entity));
                } catch (Throwable ignored) {}
            }
            return false;
        }
        try {
            return Bukkit.isPrimaryThread();
        } catch (Throwable ignored) {
            return false;
        }
    }

    /**
     * Executes immediately if the current thread owns the entity; otherwise dispatches
     * onto the entity's scheduler when the plugin is enabled.
     * Essential for synchronous GUI closing during onDisable/PlugmanX and safe Folia reloads.
     */
    public void runEntityNowOrSchedule(Entity entity, Runnable runnable) {
        if (entity == null || runnable == null) return;
        if (isOwnedByCurrentThread(entity)) {
            runnable.run();
            return;
        }
        if (plugin != null && plugin.isEnabled()) {
            runEntity(entity, runnable);
        }
    }

    /**
     * Run on the specific entity's scheduler (Entity region on Folia, main thread on Paper/Spigot).
     */
    public void runEntity(Entity entity, Runnable runnable) {
        if (entity == null || runnable == null) return;
        if (isFolia) {
            if (entity.isValid()) {
                entity.getScheduler().run(plugin, task -> runnable.run(), null);
            } else if (entity.getLocation().getWorld() != null) {
                Bukkit.getRegionScheduler().run(plugin, entity.getLocation(), task -> runnable.run());
            }
        } else {
            Bukkit.getScheduler().runTask(plugin, runnable);
        }
    }

    /**
     * Run on entity's scheduler with delay.
     */
    public void runEntityDelayed(Entity entity, Runnable runnable, long delayTicks) {
        if (entity == null) return;
        if (isFolia) {
            if (entity.isValid()) {
                entity.getScheduler().runDelayed(plugin, task -> runnable.run(), null, delayTicks);
            } else if (entity.getLocation().getWorld() != null) {
                Bukkit.getRegionScheduler().runDelayed(plugin, entity.getLocation(), task -> runnable.run(), delayTicks);
            }
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
     * Run on the region containing the specified location with delay.
     */
    public void runLocationDelayed(Location location, Runnable runnable, long delayTicks) {
        if (location == null || location.getWorld() == null) return;
        if (isFolia) {
            Bukkit.getRegionScheduler().runDelayed(plugin, location, task -> runnable.run(), delayTicks);
        } else {
            Bukkit.getScheduler().runTaskLater(plugin, runnable, delayTicks);
        }
    }

    public interface TaskHandle {
        void cancel();
    }

    /**
     * Run a recurring task on an entity's scheduler with self-cancellation support.
     */
    public TaskHandle runEntityTimer(Entity entity, Consumer<TaskHandle> consumer, long initialDelayTicks, long periodTicks) {
        if (entity == null) return () -> {};
        if (isFolia) {
            TaskHandle[] handleRef = new TaskHandle[1];
            io.papermc.paper.threadedregions.scheduler.ScheduledTask st = entity.getScheduler().runAtFixedRate(
                    plugin,
                    task -> consumer.accept(handleRef[0]),
                    null,
                    initialDelayTicks,
                    periodTicks
            );
            handleRef[0] = () -> {
                if (st != null) st.cancel();
            };
            return handleRef[0];
        } else {
            TaskHandle[] handleRef = new TaskHandle[1];
            org.bukkit.scheduler.BukkitTask bt = Bukkit.getScheduler().runTaskTimer(
                    plugin,
                    () -> consumer.accept(handleRef[0]),
                    initialDelayTicks,
                    periodTicks
            );
            handleRef[0] = () -> {
                if (bt != null) bt.cancel();
            };
            return handleRef[0];
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
