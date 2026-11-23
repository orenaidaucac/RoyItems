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
    private boolean preventSmithingCorrupt = true;
    private boolean preventGrindstoneDisenchantConfigured = true;

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
            this.preventSmithingCorrupt = config.getBoolean("smithing-table.prevent-corrupt", true);
            this.preventGrindstoneDisenchantConfigured = config.getBoolean("grindstone.prevent-disenchant-configured", true);
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

    public boolean isPreventSmithingCorrupt() {
        return preventSmithingCorrupt;
    }

    public boolean isPreventGrindstoneDisenchantConfigured() {
        return preventGrindstoneDisenchantConfigured;
    }

    /**
     * Checks if a RoyItem has pre-configured enchantments in its YAML definition.
     */
    public boolean hasConfiguredEnchantments(ItemStack item) {
        ItemDefinition def = getDefinition(item);
        if (def == null) return false;
        java.util.Map<org.bukkit.enchantments.Enchantment, Integer> conf = def.getEnchantments();
        return conf != null && !conf.isEmpty();
    }

    /**
     * Checks if a Smithing Table inventory contains any RoyItem in its input or result slots.
     */
    public boolean isSmithingCorruptAttempt(SmithingInventory inv) {
        if (inv == null || !preventSmithingCorrupt) return false;
        for (int i = 0; i < 3; i++) {
            ItemStack item = inv.getItem(i);
            if (item != null && !item.getType().isAir() && pdcManager.isRoyItem(item)) {
                return true;
            }
        }
        ItemStack result = inv.getItem(3);
        return result != null && !result.getType().isAir() && pdcManager.isRoyItem(result);
    }

    /**
     * Checks if a Grindstone inventory operation should be blocked:
     * 1. Either input item is a RoyItem with pre-configured enchantments.
     * 2. Combining two items where at least one is a RoyItem (which wipes RoyItem PDC/lore/stats).
     */
    public boolean isGrindstoneBlocked(GrindstoneInventory inv) {
        if (inv == null || !preventGrindstoneDisenchantConfigured) return false;
        ItemStack item0 = inv.getItem(0);
        ItemStack item1 = inv.getItem(1);
        boolean has0 = item0 != null && !item0.getType().isAir();
        boolean has1 = item1 != null && !item1.getType().isAir();

        // 1. Check if either item has pre-configured enchantments
        if (has0 && hasConfiguredEnchantments(item0)) return true;
        if (has1 && hasConfiguredEnchantments(item1)) return true;

        // 2. Combining two items where at least one is a RoyItem
        if (has0 && has1 && (pdcManager.isRoyItem(item0) || pdcManager.isRoyItem(item1))) {
            return true;
        }

        return false;
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

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onPrepareGrindstone(PrepareGrindstoneEvent event) {
        GrindstoneInventory inv = event.getInventory();
        if (isDisableOnEnchantment(inv.getItem(0)) || isDisableOnEnchantment(inv.getItem(1))
                || isGrindstoneBlocked(inv)) {
            event.setResult(null);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onPrepareSmithing(PrepareSmithingEvent event) {
        SmithingInventory inv = event.getInventory();
        for (ItemStack item : inv.getContents()) {
            if (isDisableOnEnchantment(item) || isDisableOnCrafting(item)) {
                event.setResult(null);
                return;
            }
        }
        if (isSmithingCorruptAttempt(inv)) {
            event.setResult(null);
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
            int rawSlot = event.getRawSlot();
            // A. Result Slot (Raw slot 2)
            if (rawSlot == 2) {
                boolean disabled = isDisableOnEnchantment(grindstone.getItem(0)) || isDisableOnEnchantment(grindstone.getItem(1));
                if (disabled || isGrindstoneBlocked(grindstone)) {
                    event.setCancelled(true);
                    grindstone.setItem(2, null);
                    if (event.getWhoClicked() instanceof Player p) {
                        p.updateInventory();
                        if (isGrindstoneBlocked(grindstone) && messageManager != null) {
                            messageManager.send(p, "grindstone-disenchant-blocked", "&cYou cannot remove pre-configured enchantments from RoyItems using a Grindstone.");
                        }
                    }
                    return;
                }
            }

            // B. Direct cursor placement into input slots (Raw slots 0, 1)
            if (rawSlot >= 0 && rawSlot <= 1 && preventGrindstoneDisenchantConfigured) {
                ItemStack cursor = event.getCursor();
                if (cursor != null && !cursor.getType().isAir()) {
                    if (hasConfiguredEnchantments(cursor)) {
                        event.setCancelled(true);
                        if (event.getWhoClicked() instanceof Player p) {
                            p.updateInventory();
                            if (messageManager != null) {
                                messageManager.send(p, "grindstone-disenchant-blocked", "&cYou cannot remove pre-configured enchantments from RoyItems using a Grindstone.");
                            }
                        }
                        return;
                    }
                    int otherSlot = (rawSlot == 0) ? 1 : 0;
                    ItemStack other = grindstone.getItem(otherSlot);
                    if (other != null && !other.getType().isAir() && (pdcManager.isRoyItem(cursor) || pdcManager.isRoyItem(other))) {
                        event.setCancelled(true);
                        if (event.getWhoClicked() instanceof Player p) {
                            p.updateInventory();
                            if (messageManager != null) {
                                messageManager.send(p, "grindstone-disenchant-blocked", "&cYou cannot remove pre-configured enchantments from RoyItems using a Grindstone.");
                            }
                        }
                        return;
                    }
                }
            }

            // C. Shift-clicking from player inventory into Grindstone
            if (event.isShiftClick() && rawSlot >= 3 && preventGrindstoneDisenchantConfigured) {
                ItemStack clicked = event.getCurrentItem();
                if (clicked != null && !clicked.getType().isAir()) {
                    if (hasConfiguredEnchantments(clicked)) {
                        event.setCancelled(true);
                        if (event.getWhoClicked() instanceof Player p) {
                            p.updateInventory();
                            if (messageManager != null) {
                                messageManager.send(p, "grindstone-disenchant-blocked", "&cYou cannot remove pre-configured enchantments from RoyItems using a Grindstone.");
                            }
                        }
                        return;
                    }
                    boolean has0 = grindstone.getItem(0) != null && !grindstone.getItem(0).getType().isAir();
                    boolean has1 = grindstone.getItem(1) != null && !grindstone.getItem(1).getType().isAir();
                    if ((has0 || has1) && (pdcManager.isRoyItem(clicked) || (has0 && pdcManager.isRoyItem(grindstone.getItem(0))) || (has1 && pdcManager.isRoyItem(grindstone.getItem(1))))) {
                        event.setCancelled(true);
                        if (event.getWhoClicked() instanceof Player p) {
                            p.updateInventory();
                            if (messageManager != null) {
                                messageManager.send(p, "grindstone-disenchant-blocked", "&cYou cannot remove pre-configured enchantments from RoyItems using a Grindstone.");
                            }
                        }
                        return;
                    }
                }
            }

            // D. Hotbar Number Key Swap into input slots (Raw slots 0, 1)
            if (rawSlot >= 0 && rawSlot <= 1 && event.getClick() == ClickType.NUMBER_KEY && preventGrindstoneDisenchantConfigured) {
                int hotbarBtn = event.getHotbarButton();
                if (hotbarBtn >= 0 && hotbarBtn < 9 && event.getWhoClicked() instanceof Player p) {
                    ItemStack hotbarItem = p.getInventory().getItem(hotbarBtn);
                    if (hotbarItem != null && !hotbarItem.getType().isAir()) {
                        if (hasConfiguredEnchantments(hotbarItem)) {
                            event.setCancelled(true);
                            p.updateInventory();
                            if (messageManager != null) {
                                messageManager.send(p, "grindstone-disenchant-blocked", "&cYou cannot remove pre-configured enchantments from RoyItems using a Grindstone.");
                            }
                            return;
                        }
                        int otherSlot = (rawSlot == 0) ? 1 : 0;
                        ItemStack other = grindstone.getItem(otherSlot);
                        if (other != null && !other.getType().isAir() && (pdcManager.isRoyItem(hotbarItem) || pdcManager.isRoyItem(other))) {
                            event.setCancelled(true);
                            p.updateInventory();
                            if (messageManager != null) {
                                messageManager.send(p, "grindstone-disenchant-blocked", "&cYou cannot remove pre-configured enchantments from RoyItems using a Grindstone.");
                            }
                            return;
                        }
                    }
                }
            }

            // E. Offhand Swap Key (F) into input slots (Raw slots 0, 1)
            if (rawSlot >= 0 && rawSlot <= 1 && event.getClick() == ClickType.SWAP_OFFHAND && preventGrindstoneDisenchantConfigured) {
                if (event.getWhoClicked() instanceof Player p) {
                    ItemStack offhand = p.getInventory().getItemInOffHand();
                    if (offhand != null && !offhand.getType().isAir()) {
                        if (hasConfiguredEnchantments(offhand)) {
                            event.setCancelled(true);
                            p.updateInventory();
                            if (messageManager != null) {
                                messageManager.send(p, "grindstone-disenchant-blocked", "&cYou cannot remove pre-configured enchantments from RoyItems using a Grindstone.");
                            }
                            return;
                        }
                        int otherSlot = (rawSlot == 0) ? 1 : 0;
                        ItemStack other = grindstone.getItem(otherSlot);
                        if (other != null && !other.getType().isAir() && (pdcManager.isRoyItem(offhand) || pdcManager.isRoyItem(other))) {
                            event.setCancelled(true);
                            p.updateInventory();
                            if (messageManager != null) {
                                messageManager.send(p, "grindstone-disenchant-blocked", "&cYou cannot remove pre-configured enchantments from RoyItems using a Grindstone.");
                            }
                            return;
                        }
                    }
                }
            }
        } else if (inv instanceof SmithingInventory smithing) {
            int rawSlot = event.getRawSlot();
            // A. Result Slot (Raw slot 3)
            if (rawSlot == 3) {
                boolean disabled = false;
                for (ItemStack item : smithing.getContents()) {
                    if (isDisableOnEnchantment(item) || isDisableOnCrafting(item)) {
                        disabled = true;
                        break;
                    }
                }
                if (disabled || isSmithingCorruptAttempt(smithing)) {
                    event.setCancelled(true);
                    smithing.setItem(3, null);
                    if (event.getWhoClicked() instanceof Player p) {
                        p.updateInventory();
                        if (isSmithingCorruptAttempt(smithing) && messageManager != null) {
                            messageManager.send(p, "smithing-table-blocked", "&cYou cannot upgrade or trim RoyItems using a Smithing Table.");
                        }
                    }
                    return;
                }
            }

            // B. Direct cursor placement into input slots (Raw slots 0, 1, 2)
            if (rawSlot >= 0 && rawSlot <= 2 && preventSmithingCorrupt) {
                ItemStack cursor = event.getCursor();
                if (cursor != null && !cursor.getType().isAir() && pdcManager.isRoyItem(cursor)) {
                    event.setCancelled(true);
                    if (event.getWhoClicked() instanceof Player p) {
                        p.updateInventory();
                        if (messageManager != null) {
                            messageManager.send(p, "smithing-table-blocked", "&cYou cannot upgrade or trim RoyItems using a Smithing Table.");
                        }
                    }
                    return;
                }
            }

            // C. Shift-clicking from player inventory into Smithing Table
            if (event.isShiftClick() && rawSlot >= 4 && preventSmithingCorrupt) {
                ItemStack clicked = event.getCurrentItem();
                if (clicked != null && !clicked.getType().isAir() && pdcManager.isRoyItem(clicked)) {
                    event.setCancelled(true);
                    if (event.getWhoClicked() instanceof Player p) {
                        p.updateInventory();
                        if (messageManager != null) {
                            messageManager.send(p, "smithing-table-blocked", "&cYou cannot upgrade or trim RoyItems using a Smithing Table.");
                        }
                    }
                    return;
                }
            }

            // D. Hotbar Number Key Swap into input slots (Raw slots 0, 1, 2)
            if (rawSlot >= 0 && rawSlot <= 2 && event.getClick() == ClickType.NUMBER_KEY && preventSmithingCorrupt) {
                int hotbarBtn = event.getHotbarButton();
                if (hotbarBtn >= 0 && hotbarBtn < 9 && event.getWhoClicked() instanceof Player p) {
                    ItemStack hotbarItem = p.getInventory().getItem(hotbarBtn);
                    if (hotbarItem != null && !hotbarItem.getType().isAir() && pdcManager.isRoyItem(hotbarItem)) {
                        event.setCancelled(true);
                        p.updateInventory();
                        if (messageManager != null) {
                            messageManager.send(p, "smithing-table-blocked", "&cYou cannot upgrade or trim RoyItems using a Smithing Table.");
                        }
                        return;
                    }
                }
            }

            // E. Offhand Swap Key (F) into input slots (Raw slots 0, 1, 2)
            if (rawSlot >= 0 && rawSlot <= 2 && event.getClick() == ClickType.SWAP_OFFHAND && preventSmithingCorrupt) {
                if (event.getWhoClicked() instanceof Player p) {
                    ItemStack offhand = p.getInventory().getItemInOffHand();
                    if (offhand != null && !offhand.getType().isAir() && pdcManager.isRoyItem(offhand)) {
                        event.setCancelled(true);
                        p.updateInventory();
                        if (messageManager != null) {
                            messageManager.send(p, "smithing-table-blocked", "&cYou cannot upgrade or trim RoyItems using a Smithing Table.");
                        }
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
                return;
            }
        }

        Inventory top = event.getView().getTopInventory();
        if (top instanceof SmithingInventory) {
            if (preventSmithingCorrupt && event.getOldCursor() != null && !event.getOldCursor().getType().isAir() && pdcManager.isRoyItem(event.getOldCursor())) {
                for (int slot : event.getRawSlots()) {
                    if (slot >= 0 && slot <= 2) {
                        event.setCancelled(true);
                        if (event.getWhoClicked() instanceof Player p) {
                            p.updateInventory();
                            if (messageManager != null) {
                                messageManager.send(p, "smithing-table-blocked", "&cYou cannot upgrade or trim RoyItems using a Smithing Table.");
                            }
                        }
                        return;
                    }
                }
            }
        } else if (top instanceof GrindstoneInventory grindstone) {
            if (preventGrindstoneDisenchantConfigured && event.getOldCursor() != null && !event.getOldCursor().getType().isAir()) {
                for (int slot : event.getRawSlots()) {
                    if (slot >= 0 && slot <= 1) {
                        if (hasConfiguredEnchantments(event.getOldCursor())) {
                            event.setCancelled(true);
                            if (event.getWhoClicked() instanceof Player p) {
                                p.updateInventory();
                                if (messageManager != null) {
                                    messageManager.send(p, "grindstone-disenchant-blocked", "&cYou cannot remove pre-configured enchantments from RoyItems using a Grindstone.");
                                }
                            }
                            return;
                        }
                        int otherSlot = (slot == 0) ? 1 : 0;
                        ItemStack other = grindstone.getItem(otherSlot);
                        if (other != null && !other.getType().isAir() && (pdcManager.isRoyItem(event.getOldCursor()) || pdcManager.isRoyItem(other))) {
                            event.setCancelled(true);
                            if (event.getWhoClicked() instanceof Player p) {
                                p.updateInventory();
                                if (messageManager != null) {
                                    messageManager.send(p, "grindstone-disenchant-blocked", "&cYou cannot remove pre-configured enchantments from RoyItems using a Grindstone.");
                                }
                            }
                            return;
                        }
                    }
                }
            }
        }
    }
}
