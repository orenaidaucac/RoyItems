package com.github.orenaidaucac.royItems.tier_set;

import com.github.orenaidaucac.royItems.core.platform.PlatformScheduler;
import com.github.orenaidaucac.royItems.item.ItemDefinition;
import com.github.orenaidaucac.royItems.item.ItemPDCManager;
import com.github.orenaidaucac.royItems.item.ItemRegistry;
import com.github.orenaidaucac.royItems.item.ItemType;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Item;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityPickupItemEvent;
import org.bukkit.event.entity.ItemDespawnEvent;
import org.bukkit.event.entity.ItemMergeEvent;
import org.bukkit.event.entity.ItemSpawnEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.world.ChunkLoadEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.Plugin;
import org.bukkit.scoreboard.Scoreboard;
import org.bukkit.scoreboard.Team;

/**
 * Handles smooth, persistent, and stable glowing outlines for dropped tiered items on the ground.
 * Integrates with Scoreboard Teams to dynamically color glowing aura according to each Tier's 'drop-color'.
 * Synchronizes with both main scoreboard and custom player scoreboards across all server forks.
 */
public class TierDropGlowListener implements Listener {

    private static final String TEAM_PREFIX = "roy_t_";

    private final Plugin plugin;
    private final PlatformScheduler scheduler;
    private final ItemPDCManager itemPDCManager;
    private final ItemRegistry itemRegistry;
    private final ItemTierManager itemTierManager;

    public TierDropGlowListener(Plugin plugin, PlatformScheduler scheduler, ItemPDCManager itemPDCManager, ItemRegistry itemRegistry, ItemTierManager itemTierManager) {
        this.plugin = plugin;
        this.scheduler = scheduler;
        this.itemPDCManager = itemPDCManager;
        this.itemRegistry = itemRegistry;
        this.itemTierManager = itemTierManager;
    }

    public TierDropGlowListener(ItemPDCManager itemPDCManager, ItemRegistry itemRegistry, ItemTierManager itemTierManager) {
        this(null, null, itemPDCManager, itemRegistry, itemTierManager);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onItemSpawn(ItemSpawnEvent event) {
        scheduleApplyGlow(event.getEntity());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPlayerDrop(PlayerDropItemEvent event) {
        scheduleApplyGlow(event.getItemDrop());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPickup(EntityPickupItemEvent event) {
        if (event.getItem() != null) {
            removeGlow(event.getItem());
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onDespawn(ItemDespawnEvent event) {
        removeGlow(event.getEntity());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onMerge(ItemMergeEvent event) {
        removeGlow(event.getEntity());
        scheduleApplyGlow(event.getTarget());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPlayerJoin(PlayerJoinEvent event) {
        syncForPlayer(event.getPlayer());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onChunkLoad(ChunkLoadEvent event) {
        for (Entity entity : event.getChunk().getEntities()) {
            if (entity instanceof Item item) {
                scheduleApplyGlow(item);
            }
        }
    }

    /**
     * Schedules glow application with a 1-tick delay to ensure the Item entity
     * is completely added to the world and registered in the network tracker.
     */
    public void scheduleApplyGlow(Item itemEntity) {
        if (itemEntity == null) return;

        // Immediate application if already valid in the world
        if (itemEntity.isValid() && !itemEntity.isDead()) {
            applyGlow(itemEntity);
        }

        // Always schedule a 1-tick delayed application to catch newly instantiated items during spawn
        if (scheduler != null && itemEntity.getLocation().getWorld() != null) {
            scheduler.runLocationDelayed(itemEntity.getLocation(), () -> {
                if (itemEntity.isValid() && !itemEntity.isDead()) {
                    applyGlow(itemEntity);
                }
            }, 1L);
        }
    }

    /**
     * Applies glowing effect and registers the dropped item entity to its tier's scoreboard team.
     */
    public void applyGlow(Item itemEntity) {
        if (itemEntity == null || itemEntity.isDead() || !itemEntity.isValid()) return;

        ItemStack stack = itemEntity.getItemStack();
        if (!itemPDCManager.isRoyItem(stack)) return;

        // 1. Resolve Tier ID from PDC or ItemDefinition
        String tierId = itemPDCManager.getTier(stack);
        if (tierId == null || tierId.isEmpty()) {
            ItemType type = itemPDCManager.getType(stack);
            String id = itemPDCManager.getId(stack);
            ItemDefinition def = itemRegistry.get(type, id);
            if (def != null) {
                tierId = def.getItemTier();
            }
        }
        if (tierId == null || tierId.isEmpty()) return;

        ItemTier tier = itemTierManager.get(tierId);
        if (tier == null) return;

        NamedTextColor color = tier.getDropNamedTextColor();
        if (color == null) {
            color = NamedTextColor.WHITE;
        }

        // 2. Enable Glowing on the item entity
        itemEntity.setGlowing(true);

        // 3. Register entity into Scoreboard Team for coloring
        String teamName = getTierTeamName(tier.getId());

        // A. Main Scoreboard
        Scoreboard mainBoard = Bukkit.getScoreboardManager().getMainScoreboard();
        assignToScoreboardTeam(mainBoard, teamName, color, itemEntity);

        // B. Individual Player Scoreboards (e.g. FastBoard, TAB, or custom scoreboards)
        for (Player player : Bukkit.getOnlinePlayers()) {
            Scoreboard playerBoard = player.getScoreboard();
            if (playerBoard != mainBoard) {
                assignToScoreboardTeam(playerBoard, teamName, color, itemEntity);
            }
        }
    }

    private void assignToScoreboardTeam(Scoreboard board, String teamName, NamedTextColor color, Item itemEntity) {
        if (board == null || itemEntity == null) return;
        try {
            Team team = board.getTeam(teamName);
            if (team == null) {
                team = board.registerNewTeam(teamName);
            }
            team.color(color);

            String uuidStr = itemEntity.getUniqueId().toString();
            if (!team.hasEntry(uuidStr)) {
                try {
                    team.addEntity(itemEntity);
                } catch (Throwable ignored) {
                    team.addEntry(uuidStr);
                }
            }
        } catch (Throwable ignored) {}
    }

    /**
     * Removes the item entity from all active tier scoreboard teams.
     */
    public void removeGlow(Item itemEntity) {
        if (itemEntity == null) return;
        try {
            String uuidStr = itemEntity.getUniqueId().toString();

            Scoreboard mainBoard = Bukkit.getScoreboardManager().getMainScoreboard();
            removeFromScoreboardTeam(mainBoard, uuidStr);

            for (Player player : Bukkit.getOnlinePlayers()) {
                Scoreboard playerBoard = player.getScoreboard();
                if (playerBoard != mainBoard) {
                    removeFromScoreboardTeam(playerBoard, uuidStr);
                }
            }
        } catch (Throwable ignored) {}
    }

    private void removeFromScoreboardTeam(Scoreboard board, String uuidStr) {
        if (board == null) return;
        try {
            Team team = board.getEntryTeam(uuidStr);
            if (team != null && team.getName().startsWith(TEAM_PREFIX)) {
                team.removeEntry(uuidStr);
            }
        } catch (Throwable ignored) {}
    }

    /**
     * Synchronizes glowing entities for a joining player.
     */
    public void syncForPlayer(Player player) {
        if (player == null || !player.isOnline()) return;
        Runnable syncTask = () -> {
            if (!player.isOnline()) return;
            World world = player.getWorld();
            for (Item item : world.getEntitiesByClass(Item.class)) {
                if (item.isValid() && !item.isDead() && item.isGlowing()) {
                    applyGlow(item);
                }
            }
        };

        if (scheduler != null && player.getLocation().getWorld() != null) {
            scheduler.runLocationDelayed(player.getLocation(), syncTask, 5L);
        } else {
            syncTask.run();
        }
    }

    /**
     * Scans all loaded worlds to apply tier glowing on any existing items on ground.
     */
    public void applyToAllGroundItems() {
        for (World world : Bukkit.getWorlds()) {
            for (Item item : world.getEntitiesByClass(Item.class)) {
                if (item.isValid() && !item.isDead()) {
                    applyGlow(item);
                }
            }
        }
    }

    /**
     * Cleans up all tier glowing teams on plugin reload or shutdown.
     */
    public void cleanup() {
        try {
            Scoreboard mainBoard = Bukkit.getScoreboardManager().getMainScoreboard();
            unregisterTierTeams(mainBoard);

            for (Player player : Bukkit.getOnlinePlayers()) {
                unregisterTierTeams(player.getScoreboard());
            }
        } catch (Throwable ignored) {}
    }

    private void unregisterTierTeams(Scoreboard board) {
        if (board == null) return;
        try {
            for (Team team : board.getTeams()) {
                if (team.getName().startsWith(TEAM_PREFIX)) {
                    team.unregister();
                }
            }
        } catch (Throwable ignored) {}
    }

    private String getTierTeamName(String tierId) {
        String clean = tierId.toLowerCase().replaceAll("[^a-z0-9_]", "");
        if (clean.length() > 10) clean = clean.substring(0, 10);
        return TEAM_PREFIX + clean;
    }
}
