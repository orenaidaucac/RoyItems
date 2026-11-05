package com.github.orenaidaucac.royItems.mechanics;

import com.github.orenaidaucac.royItems.core.message.MessageManager;
import com.github.orenaidaucac.royItems.item.ItemDefinition;
import com.github.orenaidaucac.royItems.item.ItemPDCManager;
import com.github.orenaidaucac.royItems.item.ItemRegistry;
import com.github.orenaidaucac.royItems.item.ItemType;
import org.bukkit.configuration.file.FileConfiguration;
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
import org.bukkit.inventory.meta.ItemMeta;

import java.util.Objects;

/**
 * Handles security restrictions and item protections:
 * 1. disable-on-use: Prevents right-click usage, block placing, consumption, shearing, etc.
 * 2. disable-on-enchantment: Blocks vanilla enchanting, anvil combination, grindstone, smithing table.
 * 3. disable-on-crafting: Blocks usage as crafting ingredient in workbench, stonecutter, furnace, brewing stand, etc.
 * 4. disable-on-drop: Prevents dropping from inventory or containers outside.
 * 5. prevent-anvil-rename: Blocks renaming RoyItems in Anvils while safely preserving repair, combine, and enchanting.
 */
public class ItemProtectionListener implements Listener {

    private final ItemPDCManager pdcManager;
    private final ItemRegistry registry;
    private final MessageManager messageManager;
    private boolean preventAnvilRename = true;

    public ItemProtectionListener(ItemPDCManager pdcManager, ItemRegistry registry) {
        this(pdcManager, registry, null);
    }

    public ItemProtectionListener(ItemPDCManager pdcManager, ItemRegistry registry, MessageManager messageManager) {
        this.pdcManager = pdcManager;
        this.registry = registry;
        this.messageManager = messageManager;
    }

    public void loadConfig(FileConfiguration config) {
        if (config != null) {
            this.preventAnvilRename = config.getBoolean("anvil.prevent-rename", true);
        }
    }

    public boolean isPreventAnvilRename() {
        return preventAnvilRename;
    }

    public boolean isPreventAnvilRename(ItemStack item) {
        ItemDefinition def = getDefinition(item);
        if (def != null && def.getPreventAnvilRename() != null) {
            return def.getPreventAnvilRename();
        }
        return preventAnvilRename;
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

    @EventHandler(priority = EventPriority.HIGH)
    public void onPrepareAnvil(PrepareAnvilEvent event) {
        AnvilInventory inv = event.getInventory();
        ItemStack first = inv.getFirstItem();
        ItemStack second = inv.getSecondItem();

        // 1. Existing disable-on-enchantment protection
        if (isDisableOnEnchantment(first) || isDisableOnEnchantment(second)) {
            event.setResult(null);
            return;
        }

        // 2. Anvil Rename Protection for RoyItems
        if (first == null || first.getType().isAir() || !pdcManager.isRoyItem(first)) {
            return;
        }

        if (!isPreventAnvilRename(first)) {
            return;
        }

        // Case A: Slot 1 is empty -> only renaming could be occurring
        if (second == null || second.getType().isAir()) {
            event.setResult(null);
            inv.setRepairCost(0);
            return;
        }

        // Case B: Slot 1 is NOT empty -> repair, combination, or enchanting
        ItemStack result = event.getResult();
        if (result == null || result.getType().isAir()) {
            return;
        }

        ItemMeta firstMeta = first.getItemMeta();
        ItemMeta resultMeta = result.getItemMeta();
        net.kyori.adventure.text.Component originalName = (firstMeta != null && firstMeta.hasDisplayName())
                ? firstMeta.displayName()
                : null;
        net.kyori.adventure.text.Component resultName = (resultMeta != null && resultMeta.hasDisplayName())
                ? resultMeta.displayName()
                : null;

        if (!Objects.equals(originalName, resultName)) {
            // Player attempted to rename while repairing/combining/enchanting.
            // Enforce original display name:
            resultMeta.displayName(originalName);
            result.setItemMeta(resultMeta);

            // Refund the 1 extra level Vanilla added for renaming so player is not overcharged:
            if (inv.getRepairCost() > 1) {
                inv.setRepairCost(inv.getRepairCost() - 1);
            }
            event.setResult(result);
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
                ItemStack first = anvil.getFirstItem();
                ItemStack second = anvil.getSecondItem();
                if (isDisableOnEnchantment(first) || isDisableOnEnchantment(second)) {
                    event.setCancelled(true);
                    if (event.getWhoClicked() instanceof Player p) p.updateInventory();
                    return;
                }

                if (first != null && !first.getType().isAir() && pdcManager.isRoyItem(first) && isPreventAnvilRename(first)) {
                    // 1. If slot 1 is empty, only rename could have occurred: cancel immediately
                    if (second == null || second.getType().isAir()) {
                        event.setCancelled(true);
                        anvil.setItem(2, null);
                        if (event.getWhoClicked() instanceof Player p) {
                            p.updateInventory();
                            if (messageManager != null) {
                                messageManager.send(p, "anvil-rename-blocked", "&cYou cannot rename RoyItems items using an anvil.");
                            }
                        }
                        return;
                    }

                    // 2. If slot 1 is present (repair / enchant), ensure the result item has not been renamed
                    ItemStack result = event.getCurrentItem();
                    if (result == null || result.getType().isAir()) {
                        result = anvil.getItem(2);
                    }
                    if (result != null && !result.getType().isAir()) {
                        ItemMeta firstMeta = first.getItemMeta();
                        ItemMeta resultMeta = result.getItemMeta();
                        net.kyori.adventure.text.Component originalName = (firstMeta != null && firstMeta.hasDisplayName())
                                ? firstMeta.displayName()
                                : null;
                        net.kyori.adventure.text.Component resultName = (resultMeta != null && resultMeta.hasDisplayName())
                                ? resultMeta.displayName()
                                : null;

                        if (!Objects.equals(originalName, resultName)) {
                            resultMeta.displayName(originalName);
                            result.setItemMeta(resultMeta);
                            anvil.setItem(2, result);
                            if (event.getCurrentItem() != null && !event.getCurrentItem().getType().isAir()) {
                                event.getCurrentItem().setItemMeta(resultMeta);
                            }
                        }
                    }
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
