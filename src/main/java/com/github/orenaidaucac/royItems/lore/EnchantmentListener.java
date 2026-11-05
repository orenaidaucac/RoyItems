package com.github.orenaidaucac.royItems.lore;

import com.github.orenaidaucac.royItems.core.platform.PlatformScheduler;
import com.github.orenaidaucac.royItems.item.ItemPDCManager;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.enchantment.EnchantItemEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.PrepareAnvilEvent;
import org.bukkit.event.inventory.PrepareGrindstoneEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerItemHeldEvent;
import org.bukkit.inventory.ItemStack;

/**
 * Real-time Enchantment Synchronization Listener:
 * Automatically recognizes and synchronizes lore (#enchantments#) immediately when items
 * are enchanted, upgraded, combined, or disenchanted via Anvils, Enchanting Tables,
 * Enchanted Books, Grindstones, Console/Admin /enchant commands, or EcoEnchants scrolls.
 */
public class EnchantmentListener implements Listener {

    private final ItemPDCManager pdcManager;
    private final DynamicLoreEngine loreEngine;
    private final PlatformScheduler scheduler;

    public EnchantmentListener(ItemPDCManager pdcManager, DynamicLoreEngine loreEngine, PlatformScheduler scheduler) {
        this.pdcManager = pdcManager;
        this.loreEngine = loreEngine;
        this.scheduler = scheduler;
    }

    /**
     * Updates anvil result item preview in real-time so players immediately see
     * the formatted enchantment lines before finalizing the combination.
     */
    @EventHandler(priority = EventPriority.HIGHEST)
    public void onPrepareAnvil(PrepareAnvilEvent event) {
        ItemStack result = event.getResult();
        if (result != null && !result.getType().isAir() && pdcManager.isRoyItem(result)) {
            loreEngine.synchronizeItemEnchantments(result);
            event.setResult(result);
        }
    }

    /**
     * Synchronizes lore 1 tick after enchanting at an enchanting table.
     */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onEnchantItem(EnchantItemEvent event) {
        ItemStack item = event.getItem();
        if (item != null && !item.getType().isAir() && pdcManager.isRoyItem(item)) {
            scheduler.runEntityDelayed(event.getEnchanter(), () -> {
                loreEngine.synchronizeItemEnchantments(item);
            }, 1L);
        }
    }

    /**
     * Updates grindstone result preview to remove disenchanted lines from lore.
     */
    @EventHandler(priority = EventPriority.HIGHEST)
    public void onPrepareGrindstone(PrepareGrindstoneEvent event) {
        ItemStack result = event.getResult();
        if (result != null && !result.getType().isAir() && pdcManager.isRoyItem(result)) {
            loreEngine.synchronizeItemEnchantments(result);
            event.setResult(result);
        }
    }

    /**
     * Fast O(1) hash check on clicked items and cursor items during inventory operations
     * (handles Anvil pickups, EcoEnchants scrolls/drag-and-drop, and custom enchanting mechanisms).
     */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onInventoryClick(InventoryClickEvent event) {
        if (event.getWhoClicked() instanceof Player) {
            ItemStack current = event.getCurrentItem();
            if (current != null && !current.getType().isAir() && pdcManager.isRoyItem(current)) {
                loreEngine.checkAndSynchronizeEnchantments(current);
            }
            ItemStack cursor = event.getCursor();
            if (cursor != null && !cursor.getType().isAir() && pdcManager.isRoyItem(cursor)) {
                loreEngine.checkAndSynchronizeEnchantments(cursor);
            }
        }
    }

    /**
     * Fast O(1) hash check when player switches active hotbar slot
     * (instantly updates lore if an admin used /enchant while the player held or stored the item).
     */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onItemHeld(PlayerItemHeldEvent event) {
        Player player = event.getPlayer();
        ItemStack item = player.getInventory().getItem(event.getNewSlot());
        if (item != null && !item.getType().isAir() && pdcManager.isRoyItem(item)) {
            loreEngine.checkAndSynchronizeEnchantments(item);
        }
    }

    /**
     * Fast O(1) hash check upon interaction to ensure zero latency when using items.
     */
    @EventHandler(priority = EventPriority.LOW)
    public void onInteract(PlayerInteractEvent event) {
        ItemStack item = event.getItem();
        if (item != null && !item.getType().isAir() && pdcManager.isRoyItem(item)) {
            loreEngine.checkAndSynchronizeEnchantments(item);
        }
    }
}
