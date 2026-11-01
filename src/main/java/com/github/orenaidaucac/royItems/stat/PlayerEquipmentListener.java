package com.github.orenaidaucac.royItems.stat;

import com.destroystokyo.paper.event.player.PlayerArmorChangeEvent;
import com.github.orenaidaucac.royItems.core.platform.PlatformScheduler;
import com.github.orenaidaucac.royItems.item.ItemDefinition;
import com.github.orenaidaucac.royItems.item.ItemPDCManager;
import com.github.orenaidaucac.royItems.item.ItemRegistry;
import com.github.orenaidaucac.royItems.item.ItemType;
import com.github.orenaidaucac.royItems.item.LiveUpdateService;
import com.github.orenaidaucac.royItems.mechanics.RequirementChecker;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.entity.EntityPickupItemEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.player.*;
import org.bukkit.inventory.ItemStack;

/**
 * Event-driven listener tracking player gear changes to update stat caches and
 * synchronize player attributes without continuous tick polling.
 */
public class PlayerEquipmentListener implements Listener {

    private final StatManager statManager;
    private final PlatformScheduler scheduler;
    private final LiveUpdateService liveUpdateService;
    private final RequirementChecker requirementChecker;
    private final ItemPDCManager pdcManager;
    private final ItemRegistry registry;

    public PlayerEquipmentListener(StatManager statManager, PlatformScheduler scheduler,
                                   LiveUpdateService liveUpdateService, RequirementChecker requirementChecker,
                                   ItemPDCManager pdcManager, ItemRegistry registry) {
        this.statManager = statManager;
        this.scheduler = scheduler;
        this.liveUpdateService = liveUpdateService;
        this.requirementChecker = requirementChecker;
        this.pdcManager = pdcManager;
        this.registry = registry;
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onHeld(PlayerItemHeldEvent event) {
        Player player = event.getPlayer();
        scheduler.runEntity(player, () -> {
            checkHandRequirement(player, player.getInventory().getItem(event.getNewSlot()));
            statManager.recalculate(player);
        });
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onSwap(PlayerSwapHandItemsEvent event) {
        Player player = event.getPlayer();
        scheduler.runEntity(player, () -> {
            checkHandRequirement(player, player.getInventory().getItemInMainHand());
            checkHandRequirement(player, player.getInventory().getItemInOffHand());
            statManager.recalculate(player);
        });
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onDrop(PlayerDropItemEvent event) {
        Player player = event.getPlayer();
        scheduler.runEntity(player, () -> statManager.recalculate(player));
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onInventoryClick(InventoryClickEvent event) {
        if (event.getWhoClicked() instanceof Player player) {
            scheduler.runEntity(player, () -> statManager.recalculate(player));
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onInventoryDrag(InventoryDragEvent event) {
        if (event.getWhoClicked() instanceof Player player) {
            scheduler.runEntity(player, () -> statManager.recalculate(player));
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onInteract(PlayerInteractEvent event) {
        if (event.getAction() == Action.RIGHT_CLICK_AIR || event.getAction() == Action.RIGHT_CLICK_BLOCK) {
            Player player = event.getPlayer();
            ItemStack item = event.getItem();
            if (item != null) {
                scheduler.runEntity(player, () -> statManager.recalculate(player));
            }
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onArmorChange(PlayerArmorChangeEvent event) {
        Player player = event.getPlayer();
        scheduler.runEntity(player, () -> statManager.recalculate(player));
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onRespawn(PlayerRespawnEvent event) {
        Player player = event.getPlayer();
        scheduler.runEntityDelayed(player, () -> statManager.recalculate(player), 1L);
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onItemBreak(PlayerItemBreakEvent event) {
        Player player = event.getPlayer();
        scheduler.runEntity(player, () -> statManager.recalculate(player));
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPickup(EntityPickupItemEvent event) {
        if (event.getEntity() instanceof Player player) {
            scheduler.runEntity(player, () -> statManager.recalculate(player));
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        scheduler.runEntityDelayed(player, () -> {
            liveUpdateService.updatePlayer(player);
            statManager.recalculate(player);
        }, 1L);
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onQuit(PlayerQuitEvent event) {
        statManager.cleanupPlayer(event.getPlayer());
    }

    private void checkHandRequirement(Player player, ItemStack item) {
        if (item == null || !pdcManager.isRoyItem(item)) return;
        ItemType type = pdcManager.getType(item);
        String id = pdcManager.getId(item);
        ItemDefinition def = registry.get(type, id);
        if (def != null && !requirementChecker.canUse(player, def)) {
            requirementChecker.sendRequirementFailureMessage(player, def);
        }
    }
}
