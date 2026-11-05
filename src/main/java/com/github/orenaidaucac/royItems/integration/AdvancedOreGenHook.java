package com.github.orenaidaucac.royItems.integration;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.BlockState;
import org.bukkit.block.data.Waterlogged;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;

/**
 * Robust soft-dependency hook for AdvancedOreGen (spaceio).
 * Integrates directly with AdvancedOreGen's generator cache and block replacement pipeline
 * to seamlessly regenerate generator blocks broken by Mine Radius without turning them to AIR,
 * completely preventing broken fluid flows, cobblestone lockups, and generator breakage.
 */
public class AdvancedOreGenHook {

    private boolean available = false;
    private Plugin aogPlugin;
    private Class<?> mainClass;
    private Class<?> eventsClass;
    private Method isOreGeneratorMethod;
    private Method getEventsMethod;
    private Method generateRandomBlockMethod;
    private Method updateOreGeneratorMethod;
    private Object eventsInstance;

    // Direct miner record registration
    private ConcurrentHashMap<String, Object> recentMinersMap;
    private Class<?> minerRecordClass;

    private static final BlockFace[] ALL_CHECK_FACES = new BlockFace[]{
            BlockFace.UP, BlockFace.DOWN, BlockFace.NORTH, BlockFace.SOUTH, BlockFace.EAST, BlockFace.WEST
    };

    public boolean setup() {
        try {
            if (Bukkit.getServer() == null || Bukkit.getPluginManager() == null) {
                available = false;
                return false;
            }
            aogPlugin = Bukkit.getPluginManager().getPlugin("AdvancedOreGen");
            if (aogPlugin == null || !aogPlugin.isEnabled()) {
                available = false;
                return false;
            }

            mainClass = Class.forName("xyz.spaceio.AdvancedOreGen.Main");
            eventsClass = Class.forName("xyz.spaceio.AdvancedOreGen.Events");

            isOreGeneratorMethod = mainClass.getMethod("isOreGenerator", Location.class);
            getEventsMethod = mainClass.getMethod("getEvents");
            updateOreGeneratorMethod = mainClass.getMethod("updateOreGenerator", Location.class);

            generateRandomBlockMethod = eventsClass.getMethod("generateRandomBlock", BlockState.class, Block.class, Material.class);

            eventsInstance = getEventsMethod.invoke(null);

            // Optional: bind to recentMiners map for 100% deterministic miner resolution
            try {
                Field minersField = eventsClass.getDeclaredField("recentMiners");
                minersField.setAccessible(true);
                //noinspection unchecked
                recentMinersMap = (ConcurrentHashMap<String, Object>) minersField.get(eventsInstance);

                for (Class<?> inner : eventsClass.getDeclaredClasses()) {
                    if (inner.getSimpleName().equals("MinerRecord")) {
                        minerRecordClass = inner;
                        break;
                    }
                }
            } catch (Throwable ignored) {}

            available = (eventsInstance != null && generateRandomBlockMethod != null);
            return available;
        } catch (Throwable t) {
            available = false;
            return false;
        }
    }

    public boolean isAvailable() {
        return available && aogPlugin != null && aogPlugin.isEnabled();
    }

    /**
     * Checks if a target block is managed or recognized as an AdvancedOreGen generator.
     * Evaluates both AdvancedOreGen's internal location registry and physical layout
     * (adjacent water and fence or lava) to support brand new generators.
     */
    public boolean isOreGenerator(Block block) {
        if (!isAvailable() || block == null) return false;

        Location loc = block.getLocation();

        // 1. Check internal registered generator locations
        if (isOreGeneratorMethod != null) {
            try {
                Boolean isGen = (Boolean) isOreGeneratorMethod.invoke(null, loc);
                if (Boolean.TRUE.equals(isGen)) {
                    return true;
                }
            } catch (Throwable ignored) {}
        }

        // 2. Physical Layout Detection (Fence + Water generator or Cobble/Basalt generator)
        return isPhysicalGeneratorSetup(block);
    }

    /**
     * Detects if the block is surrounded by the classic SkyBlock generator configuration:
     * - Water adjacent (water source, flowing water, or waterlogged block)
     * - Fence adjacent or below (for fence-based generators), or Lava adjacent
     */
    public boolean isPhysicalGeneratorSetup(Block block) {
        if (block == null) return false;

        boolean hasWater = false;
        boolean hasLava = false;
        boolean hasFence = false;

        for (BlockFace face : ALL_CHECK_FACES) {
            Block rel = block.getRelative(face);
            Material mat = rel.getType();

            if (isWater(rel)) {
                hasWater = true;
            } else if (isLava(rel)) {
                hasLava = true;
            }

            if (isFence(mat)) {
                hasFence = true;
            }
        }

        // Classic Fence Generator: Water + Fence
        if (hasWater && hasFence) {
            return true;
        }

        // Classic Cobblestone Generator: Water + Lava
        if (hasWater && hasLava) {
            return true;
        }

        return false;
    }

    private boolean isWater(Block block) {
        if (block == null) return false;
        try {
            Material m = block.getType();
            if (m == Material.WATER) return true;
            if (block.getBlockData() instanceof Waterlogged w && w.isWaterlogged()) return true;
        } catch (Throwable ignored) {}
        return false;
    }

    private boolean isLava(Block block) {
        if (block == null) return false;
        try {
            return block.getType() == Material.LAVA;
        } catch (Throwable ignored) {}
        return false;
    }

    private boolean isFence(Material mat) {
        if (mat == null) return false;
        String name = mat.name();
        return name.endsWith("_FENCE") || name.endsWith("_WALL");
    }

    /**
     * Triggers AdvancedOreGen to regenerate the target block into the player's resolved generator ore.
     * Ensures the player is recorded as the active miner, calls generateRandomBlock, and updates cache.
     *
     * @param block The generator block to regenerate
     * @param player The player whose stats and island determine the ore probabilities
     * @return true if successfully regenerated by AdvancedOreGen, false otherwise
     */
    public boolean regenerate(Block block, Player player) {
        if (!isAvailable() || block == null) return false;

        try {
            Location loc = block.getLocation();

            // Refresh events instance if needed (e.g. after /oregen reload)
            if (eventsInstance == null && getEventsMethod != null) {
                eventsInstance = getEventsMethod.invoke(null);
            }
            if (eventsInstance == null) return false;

            // Ensure miner record is fresh in recentMiners
            if (player != null && recentMinersMap != null && minerRecordClass != null) {
                try {
                    String locKey = loc.getWorld().getName() + "," + loc.getBlockX() + "," + loc.getBlockY() + "," + loc.getBlockZ();
                    Object record = minerRecordClass.getConstructor(UUID.class, long.class)
                            .newInstance(player.getUniqueId(), System.currentTimeMillis());
                    recentMinersMap.put(locKey, record);
                } catch (Throwable ignored) {}
            }

            // Determine base template material (BASALT for basalt generators, COBBLESTONE for others)
            Material baseMat = (block.getType() == Material.BASALT) ? Material.BASALT : Material.COBBLESTONE;

            // Invoke AdvancedOreGen's generation pipeline
            generateRandomBlockMethod.invoke(eventsInstance, block.getState(), block, baseMat);

            // Register in ore generator locations
            if (updateOreGeneratorMethod != null) {
                try {
                    updateOreGeneratorMethod.invoke(null, loc);
                } catch (Throwable ignored) {}
            }

            return true;
        } catch (Throwable t) {
            Bukkit.getLogger().log(Level.WARNING, "[RoyItems] Failed to regenerate block via AdvancedOreGen at " + block.getLocation(), t);
            return false;
        }
    }
}
