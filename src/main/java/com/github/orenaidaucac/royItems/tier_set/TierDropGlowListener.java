package com.github.orenaidaucac.royItems.tier_set;

import com.github.orenaidaucac.royItems.item.ItemDefinition;
import com.github.orenaidaucac.royItems.item.ItemPDCManager;
import com.github.orenaidaucac.royItems.item.ItemRegistry;
import com.github.orenaidaucac.royItems.item.ItemType;
import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.entity.Item;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityPickupItemEvent;
import org.bukkit.event.entity.ItemDespawnEvent;
import org.bukkit.event.entity.ItemMergeEvent;
import org.bukkit.event.entity.ItemSpawnEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.scoreboard.Scoreboard;
import org.bukkit.scoreboard.Team;

/**
 * Handles smooth and stable glowing outlines for dropped tiered items on the ground.
 * Integrates with Scoreboard Teams to dynamically color glowing aura according to each Tier's 'drop-color'.
 */
public class TierDropGlowListener implements Listener {

    private static final String TEAM_PREFIX = "roy_t_";

    private final ItemPDCManager itemPDCManager;
    private final ItemRegistry itemRegistry;
    private final ItemTierManager itemTierManager;

    public TierDropGlowListener(ItemPDCManager itemPDCManager, ItemRegistry itemRegistry, ItemTierManager itemTierManager) {
        this.itemPDCManager = itemPDCManager;
        this.itemRegistry = itemRegistry;
        this.itemTierManager = itemTierManager;
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onItemSpawn(ItemSpawnEvent event) {
        applyGlow(event.getEntity());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPlayerDrop(PlayerDropItemEvent event) {
        applyGlow(event.getItemDrop());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPickup(EntityPickupItemEvent event) {
        removeGlow(event.getItem());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onDespawn(ItemDespawnEvent event) {
        removeGlow(event.getEntity());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onMerge(ItemMergeEvent event) {
        removeGlow(event.getEntity());
        applyGlow(event.getTarget());
    }

    /**
     * Applies glowing effect and registers the dropped item entity to its tier's scoreboard team.
     */
    public void applyGlow(Item itemEntity) {
        if (itemEntity == null || !itemEntity.isValid()) return;

        ItemStack stack = itemEntity.getItemStack();
        if (!itemPDCManager.isRoyItem(stack)) return;

        ItemType type = itemPDCManager.getType(stack);
        String id = itemPDCManager.getId(stack);
        ItemDefinition def = itemRegistry.get(type, id);
        if (def == null || def.getItemTier() == null) return;

        ItemTier tier = itemTierManager.get(def.getItemTier());
        if (tier == null) return;

        Scoreboard board = Bukkit.getScoreboardManager().getMainScoreboard();
        String teamName = getTierTeamName(tier.getId());
        Team team = board.getTeam(teamName);
        if (team == null) {
            team = board.registerNewTeam(teamName);
        }
        team.color(tier.getDropNamedTextColor());
        team.addEntity(itemEntity);
        itemEntity.setGlowing(true);
    }

    /**
     * Removes the item entity from any active tier scoreboard teams.
     */
    public void removeGlow(Item itemEntity) {
        if (itemEntity == null) return;
        try {
            Scoreboard board = Bukkit.getScoreboardManager().getMainScoreboard();
            Team team = board.getEntryTeam(itemEntity.getUniqueId().toString());
            if (team != null && team.getName().startsWith(TEAM_PREFIX)) {
                team.removeEntity(itemEntity);
            }
        } catch (Throwable ignored) {}
    }

    /**
     * Scans all loaded worlds to apply tier glowing on any existing items on ground.
     */
    public void applyToAllGroundItems() {
        for (World world : Bukkit.getWorlds()) {
            for (Item item : world.getEntitiesByClass(Item.class)) {
                applyGlow(item);
            }
        }
    }

    /**
     * Cleans up all tier glowing teams on plugin reload or shutdown.
     */
    public void cleanup() {
        try {
            Scoreboard board = Bukkit.getScoreboardManager().getMainScoreboard();
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
