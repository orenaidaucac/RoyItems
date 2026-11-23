package com.github.orenaidaucac.royItems.extraction;

import com.github.orenaidaucac.royItems.item.ItemDefinition;
import com.github.orenaidaucac.royItems.item.ItemPDCManager;
import com.github.orenaidaucac.royItems.item.ItemRegistry;
import com.github.orenaidaucac.royItems.item.ItemType;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryAction;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.ItemStack;

/**
 * Event listener for drag-and-drop enchantment extraction.
 * Detects when players drag a CONSUMABLE with remove-enchants enabled onto an item.
 */
public class EnchantExtractionListener implements Listener {

    private final ExtractionManager extractionManager;
    private final ItemPDCManager pdcManager;
    private final ItemRegistry registry;

    public EnchantExtractionListener(ExtractionManager extractionManager, ItemPDCManager pdcManager, ItemRegistry registry) {
        this.extractionManager = extractionManager;
        this.pdcManager = pdcManager;
        this.registry = registry;
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) return;

        // 1. Only handle extraction in the player's own inventory
        if (event.getClickedInventory() == null || !event.getClickedInventory().equals(player.getInventory())) {
            return;
        }

        ItemStack cursor = event.getCursor();
        ItemStack target = event.getCurrentItem();

        if (cursor == null || cursor.getType().isAir() || target == null || target.getType().isAir()) {
            return;
        }

        // 2. Verify cursor item is a RoyItems CONSUMABLE with remove-enchants enabled
        if (!pdcManager.isRoyItem(cursor)) return;
        ItemType cursorType = pdcManager.getType(cursor);
        if (cursorType != ItemType.CONSUMABLE) return;

        String consumableId = pdcManager.getId(cursor);
        ItemDefinition consumableDef = registry.get(ItemType.CONSUMABLE, consumableId);
        if (consumableDef == null || !consumableDef.hasRemoveEnchants()) {
            return;
        }

        EnchantExtractionConfig extractionConfig = consumableDef.getRemoveEnchantsConfig();
        if (extractionConfig == null || !extractionConfig.isEnabled()) {
            return;
        }

        // 3. Resolve target definition if target is a RoyItem
        ItemDefinition targetDef = pdcManager.isRoyItem(target)
                ? registry.get(pdcManager.getType(target), pdcManager.getId(target))
                : null;

        // 4. CRITICAL: NEVER allow two remove-enchants items to extract from each other
        if (targetDef != null && targetDef.hasRemoveEnchants()) {
            return; // Allow normal Minecraft stacking / swapping / inventory move
        }

        // 5. CRITICAL: If cursor and target can stack, allow normal Minecraft stacking
        if (cursor.isSimilar(target)) {
            return; // Allow normal Minecraft merge/stack
        }

        // 6. Action check: only drag-and-drop actions onto target slot
        InventoryAction action = event.getAction();
        if (action != InventoryAction.SWAP_WITH_CURSOR &&
                action != InventoryAction.PLACE_ALL &&
                action != InventoryAction.PLACE_ONE) {
            return;
        }

        // 7. Check if target is whitelisted for this extraction scroll
        if (!extractionConfig.isItemWhitelisted(target, targetDef)) {
            return; // Not whitelisted: allow normal inventory swap without obstruction
        }

        // 8. Check if target has real enchantments (excludes cosmetic glow)
        if (!extractionManager.hasRealEnchantments(target, targetDef)) {
            return; // No real enchantments (or only glow: true): allow normal inventory swap
        }

        // 9. All checks passed: this is an authoritative Enchantment Extraction request
        event.setCancelled(true);
        extractionManager.openExtractionGui(player, target, event.getSlot(), cursor);
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerQuit(PlayerQuitEvent event) {
        extractionManager.untrackOpenGui(event.getPlayer().getUniqueId());
    }
}
