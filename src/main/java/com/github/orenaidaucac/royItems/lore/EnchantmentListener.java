package com.github.orenaidaucac.royItems.lore;

import com.github.orenaidaucac.royItems.core.platform.PlatformScheduler;
import com.github.orenaidaucac.royItems.item.ItemPDCManager;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.enchantment.EnchantItemEvent;
import org.bukkit.event.entity.EntityPickupItemEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryOpenEvent;
import org.bukkit.event.inventory.PrepareAnvilEvent;
import org.bukkit.event.inventory.PrepareGrindstoneEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerItemHeldEvent;
import org.bukkit.event.player.PlayerSwapHandItemsEvent;
import org.bukkit.inventory.AnvilInventory;
import org.bukkit.inventory.EnchantingInventory;
import org.bukkit.inventory.GrindstoneInventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.Plugin;

/**
 * Real-time Enchantment Synchronization Listener:
 * Automatically recognizes and synchronizes lore (#enchantments#) immediately when items
 * are enchanted, upgraded, combined, or disenchanted via:
 * 1. Anvil (combining/applying enchanted books and repair).
 * 2. Enchanting Table (enchanting equipment directly).
 * 3. Drag & Drop (EcoEnchants books/scrolls and custom book applications).
 * 4. Admin / Console Commands (/enchant, /ecoenchants enchant, etc.).
 * 5. Plugin API & External modifications.
 */
public class EnchantmentListener implements Listener {

    private final Plugin plugin;
    private final ItemPDCManager pdcManager;
    private final DynamicLoreEngine loreEngine;
    private final PlatformScheduler scheduler;

    public EnchantmentListener(ItemPDCManager pdcManager, DynamicLoreEngine loreEngine, PlatformScheduler scheduler) {
        this(null, pdcManager, loreEngine, scheduler);
    }

    public EnchantmentListener(Plugin plugin, ItemPDCManager pdcManager, DynamicLoreEngine loreEngine, PlatformScheduler scheduler) {
        this.plugin = plugin;
        this.pdcManager = pdcManager;
        this.loreEngine = loreEngine;
        this.scheduler = scheduler;
        startPeriodicSyncTask();
    }

    /**
     * Periodic background check on player hands to catch console or admin /enchant commands
     * while the player is standing still without triggering inventory or interaction events.
     */
    private void startPeriodicSyncTask() {
        if (scheduler == null) return;
        long period = 10L; // Default: every 10 ticks = 0.5s
        if (plugin != null && plugin.getConfig() != null) {
            period = plugin.getConfig().getLong("enchantments.check-interval-ticks", 10L);
            if (period <= 0) return; // Feature disabled
        }
        scheduler.runGlobalTimer(() -> {
            for (Player player : Bukkit.getOnlinePlayers()) {
                if (player == null || !player.isOnline()) continue;
                scheduler.runEntity(player, () -> {
                    if (!player.isOnline()) return;
                    boolean changed = false;

                    ItemStack mainHand = player.getInventory().getItemInMainHand();
                    if (mainHand != null && !mainHand.getType().isAir() && pdcManager.isRoyItem(mainHand)) {
                        if (loreEngine.checkAndSynchronizeEnchantments(mainHand)) {
                            changed = true;
                        }
                    }

                    ItemStack offHand = player.getInventory().getItemInOffHand();
                    if (offHand != null && !offHand.getType().isAir() && pdcManager.isRoyItem(offHand)) {
                        if (loreEngine.checkAndSynchronizeEnchantments(offHand)) {
                            changed = true;
                        }
                    }

                    if (changed) {
                        player.updateInventory();
                    }
                });
            }
        }, period, period);
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
     * Synchronizes lore 1 tick after enchanting at an enchanting table.
     */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onEnchantItem(EnchantItemEvent event) {
        Player player = event.getEnchanter();
        ItemStack item = event.getItem();
        if (player == null || item == null || item.getType().isAir() || !pdcManager.isRoyItem(item)) {
            return;
        }
        scheduler.runEntityDelayed(player, () -> {
            if (!player.isOnline()) return;
            boolean changed = loreEngine.synchronizeItemEnchantments(item);

            if (event.getView().getTopInventory() instanceof EnchantingInventory enchInv) {
                ItemStack slot0 = enchInv.getItem();
                if (slot0 != null && !slot0.getType().isAir() && pdcManager.isRoyItem(slot0)) {
                    if (loreEngine.checkAndSynchronizeEnchantments(slot0)) changed = true;
                }
            }

            ItemStack cursor = player.getItemOnCursor();
            if (cursor != null && !cursor.getType().isAir() && pdcManager.isRoyItem(cursor)) {
                if (loreEngine.checkAndSynchronizeEnchantments(cursor)) changed = true;
            }

            if (changed) {
                player.updateInventory();
            }
        }, 1L);
    }

    /**
     * Handles inventory clicks with ignoreCancelled = false to capture drag-and-drop
     * enchantment scroll/book applications (e.g. EcoEnchants) which cancel the event
     * to prevent vanilla cursor swapping. Also handles Anvil / Grindstone result pickups.
     */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = false)
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) {
            return;
        }

        ItemStack current = event.getCurrentItem();
        if (current != null && !current.getType().isAir() && pdcManager.isRoyItem(current)) {
            loreEngine.checkAndSynchronizeEnchantments(current);
        }

        ItemStack cursor = event.getCursor();
        if (cursor != null && !cursor.getType().isAir() && pdcManager.isRoyItem(cursor)) {
            loreEngine.checkAndSynchronizeEnchantments(cursor);
        }

        // Detect if Anvil or Grindstone output slot (slot 2) is being picked up
        boolean isAnvilOrGrindstoneResult = (event.getInventory() instanceof AnvilInventory
                || event.getInventory() instanceof GrindstoneInventory)
                && event.getRawSlot() == 2;

        // Detect if an enchanted book or potential enchantment scroll was clicked onto an item
        boolean isPotentialEnchantApplication = (cursor != null && !cursor.getType().isAir() && (
                cursor.getType() == Material.ENCHANTED_BOOK
                || cursor.getType() == Material.BOOK
                || (cursor.getItemMeta() != null && cursor.getItemMeta().hasEnchants())
        )) || (current != null && !current.getType().isAir() && pdcManager.isRoyItem(current));

        if (isAnvilOrGrindstoneResult || isPotentialEnchantApplication) {
            scheduler.runEntityDelayed(player, () -> {
                if (!player.isOnline()) return;
                boolean changed = false;

                ItemStack postCursor = player.getItemOnCursor();
                if (postCursor != null && !postCursor.getType().isAir() && pdcManager.isRoyItem(postCursor)) {
                    if (loreEngine.checkAndSynchronizeEnchantments(postCursor)) {
                        changed = true;
                    }
                }

                int clickedSlot = event.getSlot();
                if (clickedSlot >= 0 && clickedSlot < player.getInventory().getSize()) {
                    ItemStack slotItem = player.getInventory().getItem(clickedSlot);
                    if (slotItem != null && !slotItem.getType().isAir() && pdcManager.isRoyItem(slotItem)) {
                        if (loreEngine.checkAndSynchronizeEnchantments(slotItem)) {
                            changed = true;
                        }
                    }
                }

                // If output was shift-clicked into player inventory, check storage contents
                if (isAnvilOrGrindstoneResult) {
                    for (ItemStack item : player.getInventory().getStorageContents()) {
                        if (item != null && !item.getType().isAir() && pdcManager.isRoyItem(item)) {
                            if (loreEngine.checkAndSynchronizeEnchantments(item)) {
                                changed = true;
                            }
                        }
                    }
                }

                if (changed) {
                    player.updateInventory();
                }
            }, 1L);
        }
    }

    /**
     * Synchronizes held items when closing any inventory (e.g. Anvil, Grindstone, Chest, Merchant).
     */
    @EventHandler(priority = EventPriority.MONITOR)
    public void onInventoryClose(InventoryCloseEvent event) {
        if (event.getPlayer() instanceof Player player) {
            scheduler.runEntityDelayed(player, () -> {
                if (!player.isOnline()) return;
                boolean changed = false;

                ItemStack mainHand = player.getInventory().getItemInMainHand();
                if (mainHand != null && !mainHand.getType().isAir() && pdcManager.isRoyItem(mainHand)) {
                    if (loreEngine.checkAndSynchronizeEnchantments(mainHand)) changed = true;
                }

                ItemStack offHand = player.getInventory().getItemInOffHand();
                if (offHand != null && !offHand.getType().isAir() && pdcManager.isRoyItem(offHand)) {
                    if (loreEngine.checkAndSynchronizeEnchantments(offHand)) changed = true;
                }

                ItemStack cursor = player.getItemOnCursor();
                if (cursor != null && !cursor.getType().isAir() && pdcManager.isRoyItem(cursor)) {
                    if (loreEngine.checkAndSynchronizeEnchantments(cursor)) changed = true;
                }

                if (changed) {
                    player.updateInventory();
                }
            }, 1L);
        }
    }

    /**
     * Fast check when opening any inventory to ensure held items are clean before viewing.
     */
    @EventHandler(priority = EventPriority.MONITOR)
    public void onInventoryOpen(InventoryOpenEvent event) {
        if (event.getPlayer() instanceof Player player) {
            ItemStack mainHand = player.getInventory().getItemInMainHand();
            if (mainHand != null && !mainHand.getType().isAir() && pdcManager.isRoyItem(mainHand)) {
                loreEngine.checkAndSynchronizeEnchantments(mainHand);
            }
            ItemStack offHand = player.getInventory().getItemInOffHand();
            if (offHand != null && !offHand.getType().isAir() && pdcManager.isRoyItem(offHand)) {
                loreEngine.checkAndSynchronizeEnchantments(offHand);
            }
        }
    }

    /**
     * Fast O(1) hash check when player switches active hotbar slot.
     */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onItemHeld(PlayerItemHeldEvent event) {
        Player player = event.getPlayer();
        boolean changed = false;

        ItemStack prevItem = player.getInventory().getItem(event.getPreviousSlot());
        if (prevItem != null && !prevItem.getType().isAir() && pdcManager.isRoyItem(prevItem)) {
            if (loreEngine.checkAndSynchronizeEnchantments(prevItem)) changed = true;
        }

        ItemStack newItem = player.getInventory().getItem(event.getNewSlot());
        if (newItem != null && !newItem.getType().isAir() && pdcManager.isRoyItem(newItem)) {
            if (loreEngine.checkAndSynchronizeEnchantments(newItem)) changed = true;
        }

        if (changed) {
            player.updateInventory();
        }
    }

    /**
     * Fast O(1) hash check upon interaction to ensure zero latency when using items.
     */
    @EventHandler(priority = EventPriority.LOW)
    public void onInteract(PlayerInteractEvent event) {
        ItemStack item = event.getItem();
        if (item != null && !item.getType().isAir() && pdcManager.isRoyItem(item)) {
            if (loreEngine.checkAndSynchronizeEnchantments(item)) {
                event.getPlayer().updateInventory();
            }
        }
    }

    /**
     * Fast check when swapping items between main hand and off hand (F key).
     */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onSwapHand(PlayerSwapHandItemsEvent event) {
        Player player = event.getPlayer();
        boolean changed = false;

        ItemStack main = event.getMainHandItem();
        if (main != null && !main.getType().isAir() && pdcManager.isRoyItem(main)) {
            if (loreEngine.checkAndSynchronizeEnchantments(main)) changed = true;
        }

        ItemStack off = event.getOffHandItem();
        if (off != null && !off.getType().isAir() && pdcManager.isRoyItem(off)) {
            if (loreEngine.checkAndSynchronizeEnchantments(off)) changed = true;
        }

        if (changed) {
            player.updateInventory();
        }
    }

    /**
     * Fast check when player drops an item to ensure it has updated lore on the ground.
     */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onDropItem(PlayerDropItemEvent event) {
        ItemStack item = event.getItemDrop().getItemStack();
        if (item != null && !item.getType().isAir() && pdcManager.isRoyItem(item)) {
            if (loreEngine.checkAndSynchronizeEnchantments(item)) {
                event.getItemDrop().setItemStack(item);
            }
        }
    }

    /**
     * Fast O(1) hash check when player picks up an item from the ground.
     */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPickupItem(EntityPickupItemEvent event) {
        if (event.getEntity() instanceof Player player) {
            ItemStack item = event.getItem().getItemStack();
            if (item != null && !item.getType().isAir() && pdcManager.isRoyItem(item)) {
                if (loreEngine.checkAndSynchronizeEnchantments(item)) {
                    event.getItem().setItemStack(item);
                    player.updateInventory();
                }
            }
        }
    }
}
