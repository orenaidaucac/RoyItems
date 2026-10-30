package com.github.orenaidaucac.royItems.mechanics;

import com.github.orenaidaucac.royItems.item.ItemDefinition;
import com.github.orenaidaucac.royItems.item.ItemPDCManager;
import com.github.orenaidaucac.royItems.item.ItemRegistry;
import com.github.orenaidaucac.royItems.item.ItemType;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.enchantment.EnchantItemEvent;
import org.bukkit.event.enchantment.PrepareItemEnchantEvent;
import org.bukkit.event.entity.EntityShootBowEvent;
import org.bukkit.event.entity.ProjectileLaunchEvent;
import org.bukkit.event.inventory.*;
import org.bukkit.event.player.*;
import org.bukkit.inventory.*;

/**
 * Handles security restrictions and item protections:
 * 1. disable-on-use: Prevents right-click usage, block placing, consumption, shearing, etc.
 * 2. disable-on-enchantment: Blocks vanilla enchanting, anvil combination, grindstone, smithing table.
 * 3. disable-on-crafting: Blocks usage as crafting ingredient in workbench, stonecutter, furnace, brewing stand, etc.
 * 4. disable-on-drop: Prevents dropping from inventory or containers outside.
 */
public class ItemProtectionListener implements Listener {

    private final ItemPDCManager pdcManager;
    private final ItemRegistry registry;

    public ItemProtectionListener(ItemPDCManager pdcManager, ItemRegistry registry) {
        this.pdcManager = pdcManager;
        this.registry = registry;
    }

    public ItemDefinition getDefinition(ItemStack item) {
        if (item == null || item.getType().isAir() || !pdcManager.isRoyItem(item)) return null;
        ItemType type = pdcManager.getType(item);
        String id = pdcManager.getId(item);
        return registry.get(type, id);
    }

    public boolean isDisableOnUse(ItemStack item) {
        ItemDefinition def = getDefinition(item);
        return def != null && def.isDisableOnUse();
    }

    public boolean isDisableOnEnchantment(ItemStack item) {
        ItemDefinition def = getDefinition(item);
        return def != null && def.isDisableOnEnchantment();
    }

    public boolean isDisableOnCrafting(ItemStack item) {
        ItemDefinition def = getDefinition(item);
        return def != null && def.isDisableOnCrafting();
    }

    public boolean isDisableOnDrop(ItemStack item) {
        ItemDefinition def = getDefinition(item);
        return def != null && def.isDisableOnDrop();
    }

    // =========================================================================
    // 1. DISABLE ON USE (RIGHT-CLICK, CONSUME, PLACE, SHEAR, BUCKET, BOW, ETC.)
    // =========================================================================

    @EventHandler(priority = EventPriority.LOW)
    public void onPlayerInteract(PlayerInteractEvent event) {
        ItemStack item = event.getItem();
        if (isDisableOnUse(item)) {
            Action action = event.getAction();
            if (action == Action.RIGHT_CLICK_AIR || action == Action.RIGHT_CLICK_BLOCK) {
                event.setCancelled(true);
                event.setUseItemInHand(Event.Result.DENY);
                event.setUseInteractedBlock(Event.Result.DENY);
            }
        }
    }

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onBlockPlace(BlockPlaceEvent event) {
        if (isDisableOnUse(event.getItemInHand())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onPlayerConsume(PlayerItemConsumeEvent event) {
        if (isDisableOnUse(event.getItem())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onInteractEntity(PlayerInteractEntityEvent event) {
        ItemStack item = (event.getHand() == EquipmentSlot.HAND)
                ? event.getPlayer().getInventory().getItemInMainHand()
                : event.getPlayer().getInventory().getItemInOffHand();
        if (isDisableOnUse(item)) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onInteractAtEntity(PlayerInteractAtEntityEvent event) {
        ItemStack item = (event.getHand() == EquipmentSlot.HAND)
                ? event.getPlayer().getInventory().getItemInMainHand()
                : event.getPlayer().getInventory().getItemInOffHand();
        if (isDisableOnUse(item)) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onShear(PlayerShearEntityEvent event) {
        if (isDisableOnUse(event.getItem())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onBucketEmpty(PlayerBucketEmptyEvent event) {
        ItemStack item = (event.getHand() == EquipmentSlot.HAND)
                ? event.getPlayer().getInventory().getItemInMainHand()
                : event.getPlayer().getInventory().getItemInOffHand();
        if (isDisableOnUse(item) || isDisableOnUse(event.getItemStack())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onBucketFill(PlayerBucketFillEvent event) {
        ItemStack item = (event.getHand() == EquipmentSlot.HAND)
                ? event.getPlayer().getInventory().getItemInMainHand()
                : event.getPlayer().getInventory().getItemInOffHand();
        if (isDisableOnUse(item) || isDisableOnUse(event.getItemStack())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onShootBow(EntityShootBowEvent event) {
        if (event.getEntity() instanceof Player && isDisableOnUse(event.getBow())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onProjectileLaunch(ProjectileLaunchEvent event) {
        if (event.getEntity().getShooter() instanceof Player player) {
            ItemStack main = player.getInventory().getItemInMainHand();
            ItemStack off = player.getInventory().getItemInOffHand();
            if (isDisableOnUse(main) || isDisableOnUse(off)) {
                event.setCancelled(true);
            }
        }
    }

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onArmorStandManipulate(PlayerArmorStandManipulateEvent event) {
        if (isDisableOnUse(event.getPlayerItem())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onFish(PlayerFishEvent event) {
        Player player = event.getPlayer();
        if (isDisableOnUse(player.getInventory().getItemInMainHand()) || isDisableOnUse(player.getInventory().getItemInOffHand())) {
            event.setCancelled(true);
        }
    }

    // =========================================================================
    // 2. DISABLE ON ENCHANTMENT (ENCHANTING TABLE, ANVIL, GRINDSTONE, SMITHING)
    // =========================================================================

    @EventHandler(priority = EventPriority.LOW)
    public void onPrepareItemEnchant(PrepareItemEnchantEvent event) {
        if (isDisableOnEnchantment(event.getItem())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.LOW)
    public void onEnchantItem(EnchantItemEvent event) {
        if (isDisableOnEnchantment(event.getItem())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.LOW)
    public void onPrepareAnvil(PrepareAnvilEvent event) {
        AnvilInventory inv = event.getInventory();
        if (isDisableOnEnchantment(inv.getFirstItem()) || isDisableOnEnchantment(inv.getSecondItem())) {
            event.setResult(null);
        }
    }

    @EventHandler(priority = EventPriority.LOW)
    public void onPrepareGrindstone(PrepareGrindstoneEvent event) {
        GrindstoneInventory inv = event.getInventory();
        if (isDisableOnEnchantment(inv.getItem(0)) || isDisableOnEnchantment(inv.getItem(1))) {
            event.setResult(null);
        }
    }

    @EventHandler(priority = EventPriority.LOW)
    public void onPrepareSmithing(PrepareSmithingEvent event) {
        SmithingInventory inv = event.getInventory();
        for (ItemStack item : inv.getContents()) {
            if (isDisableOnEnchantment(item) || isDisableOnCrafting(item)) {
                event.setResult(null);
                return;
            }
        }
    }

    // =========================================================================
    // 3. DISABLE ON CRAFTING (WORKBENCH, FURNACE, BREWING, STONECUTTER, ETC.)
    // =========================================================================

    @EventHandler(priority = EventPriority.LOW)
    public void onPrepareCraft(PrepareItemCraftEvent event) {
        if (event.getInventory() == null) return;
        for (ItemStack matrixItem : event.getInventory().getMatrix()) {
            if (isDisableOnCrafting(matrixItem)) {
                event.getInventory().setResult(null);
                return;
            }
        }
    }

    @EventHandler(priority = EventPriority.LOW)
    public void onCraft(CraftItemEvent event) {
        if (event.getInventory() == null) return;
        for (ItemStack matrixItem : event.getInventory().getMatrix()) {
            if (isDisableOnCrafting(matrixItem)) {
                event.setCancelled(true);
                event.getInventory().setResult(null);
                if (event.getWhoClicked() instanceof Player player) {
                    player.updateInventory();
                }
                return;
            }
        }
    }

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onFurnaceSmelt(FurnaceSmeltEvent event) {
        if (isDisableOnCrafting(event.getSource())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onFurnaceBurn(FurnaceBurnEvent event) {
        if (isDisableOnCrafting(event.getFuel())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onBrew(BrewEvent event) {
        if (isDisableOnCrafting(event.getContents().getIngredient())) {
            event.setCancelled(true);
        }
    }

    // =========================================================================
    // 4. DISABLE ON DROP & CONTAINER DROPPING
    // =========================================================================

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onPlayerDrop(PlayerDropItemEvent event) {
        if (isDisableOnDrop(event.getItemDrop().getItemStack())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.LOW)
    public void onInventoryClick(InventoryClickEvent event) {
        // A. Drop Protection
        // 1. Outside click with cursor
        if (event.getSlotType() == InventoryType.SlotType.OUTSIDE || event.getRawSlot() == -999) {
            if (isDisableOnDrop(event.getCursor())) {
                event.setCancelled(true);
                return;
            }
        }

        // 2. Drop key (Q or Ctrl+Q) on any slot in any inventory/container
        ClickType click = event.getClick();
        if (click == ClickType.DROP || click == ClickType.CONTROL_DROP) {
            if (isDisableOnDrop(event.getCurrentItem())) {
                event.setCancelled(true);
                return;
            }
        }

        // 3. Drop InventoryActions
        InventoryAction action = event.getAction();
        if (action == InventoryAction.DROP_ALL_SLOT || action == InventoryAction.DROP_ONE_SLOT) {
            if (isDisableOnDrop(event.getCurrentItem())) {
                event.setCancelled(true);
                return;
            }
        }
        if (action == InventoryAction.DROP_ALL_CURSOR || action == InventoryAction.DROP_ONE_CURSOR) {
            if (isDisableOnDrop(event.getCursor())) {
                event.setCancelled(true);
                return;
            }
        }

        // B. Enchantment and Smithing table result pickup prevention
        Inventory inv = event.getInventory();
        if (inv instanceof AnvilInventory anvil) {
            if (event.getRawSlot() == 2) {
                if (isDisableOnEnchantment(anvil.getFirstItem()) || isDisableOnEnchantment(anvil.getSecondItem())) {
                    event.setCancelled(true);
                    if (event.getWhoClicked() instanceof Player p) p.updateInventory();
                    return;
                }
            }
        } else if (inv instanceof GrindstoneInventory grindstone) {
            if (event.getRawSlot() == 2) {
                if (isDisableOnEnchantment(grindstone.getItem(0)) || isDisableOnEnchantment(grindstone.getItem(1))) {
                    event.setCancelled(true);
                    if (event.getWhoClicked() instanceof Player p) p.updateInventory();
                    return;
                }
            }
        } else if (inv instanceof SmithingInventory smithing) {
            if (event.getRawSlot() == 3) {
                for (ItemStack item : smithing.getContents()) {
                    if (isDisableOnEnchantment(item) || isDisableOnCrafting(item)) {
                        event.setCancelled(true);
                        if (event.getWhoClicked() instanceof Player p) p.updateInventory();
                        return;
                    }
                }
            }
        }

        // C. Specialized Crafting Stations (Stonecutter, Cartography, Loom)
        InventoryView view = event.getView();
        Inventory top = view.getTopInventory();
        if (top != null) {
            if (top.getType() == InventoryType.STONECUTTER) {
                if (event.getRawSlot() == 1) { // Result slot
                    if (isDisableOnCrafting(top.getItem(0))) {
                        event.setCancelled(true);
                        if (event.getWhoClicked() instanceof Player p) p.updateInventory();
                    }
                }
            } else if (top.getType() == InventoryType.CARTOGRAPHY) {
                if (event.getRawSlot() == 2) {
                    if (isDisableOnCrafting(top.getItem(0)) || isDisableOnCrafting(top.getItem(1))) {
                        event.setCancelled(true);
                        if (event.getWhoClicked() instanceof Player p) p.updateInventory();
                    }
                }
            } else if (top.getType() == InventoryType.LOOM) {
                if (event.getRawSlot() == 3) {
                    if (isDisableOnCrafting(top.getItem(0)) || isDisableOnCrafting(top.getItem(1)) || isDisableOnCrafting(top.getItem(2))) {
                        event.setCancelled(true);
                        if (event.getWhoClicked() instanceof Player p) p.updateInventory();
                    }
                }
            }
        }
    }

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onInventoryDrag(InventoryDragEvent event) {
        if (event.getRawSlots().contains(-999)) {
            if (isDisableOnDrop(event.getOldCursor())) {
                event.setCancelled(true);
            }
        }
    }
}
