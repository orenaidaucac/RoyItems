package com.github.orenaidaucac.royItems.socket;

import com.github.orenaidaucac.royItems.item.ItemPDCManager;
import com.github.orenaidaucac.royItems.item.ItemType;
import com.github.orenaidaucac.royItems.stat.StatManager;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryAction;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.ItemStack;

/**
 * Listener enabling drag-and-drop gem stone socketing directly in player inventories.
 */
public class GemSocketListener implements Listener {

    private final GemSocketManager socketManager;
    private final ItemPDCManager pdcManager;
    private final StatManager statManager;

    public GemSocketListener(GemSocketManager socketManager, ItemPDCManager pdcManager, StatManager statManager) {
        this.socketManager = socketManager;
        this.pdcManager = pdcManager;
        this.statManager = statManager;
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onSocketClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) return;

        ItemStack cursor = event.getCursor();
        ItemStack target = event.getCurrentItem();

        if (cursor == null || cursor.getType().isAir() || target == null || target.getType().isAir()) return;

        // Check if cursor is GEM_STONE
        if (!pdcManager.isRoyItem(cursor)) return;
        ItemType cursorType = pdcManager.getType(cursor);
        if (cursorType != ItemType.GEM_STONE) return;

        // Check if target is a RoyItem with sockets
        if (!pdcManager.isRoyItem(target)) return;

        // Only handle SWAP_WITH_CURSOR or PLACE_ALL / PLACE_ONE on target
        if (event.getAction() == InventoryAction.SWAP_WITH_CURSOR ||
                event.getAction() == InventoryAction.PLACE_ALL ||
                event.getAction() == InventoryAction.PLACE_ONE) {

            boolean success = socketManager.socketGem(player, target, cursor);
            if (success) {
                event.setCancelled(true);
                // Consume 1 gem from cursor
                if (cursor.getAmount() > 1) {
                    cursor.setAmount(cursor.getAmount() - 1);
                    player.setItemOnCursor(cursor);
                } else {
                    player.setItemOnCursor(null);
                }
                // Refresh target slot
                event.setCurrentItem(target);
                // Recalculate player stats
                statManager.recalculate(player);
            }
        }
    }
}
