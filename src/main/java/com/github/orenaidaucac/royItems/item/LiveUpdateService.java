package com.github.orenaidaucac.royItems.item;

import com.github.orenaidaucac.royItems.core.platform.PlatformScheduler;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

import java.util.List;

/**
 * LiveUpdate Service: Automatically updates custom items in player inventories and ender chests
 * when admin reloads or edits definitions, preserving instance data (sockets, expiration).
 */
public class LiveUpdateService {

    private final ItemRegistry registry;
    private final ItemPDCManager pdcManager;
    private final ItemBuilder itemBuilder;
    private final PlatformScheduler scheduler;

    public LiveUpdateService(ItemRegistry registry, ItemPDCManager pdcManager, ItemBuilder itemBuilder, PlatformScheduler scheduler) {
        this.registry = registry;
        this.pdcManager = pdcManager;
        this.itemBuilder = itemBuilder;
        this.scheduler = scheduler;
    }

    /**
     * Updates all online players' inventories and ender chests (used on /ri reload).
     */
    public void updateAllOnlinePlayers() {
        for (Player player : Bukkit.getOnlinePlayers()) {
            scheduler.runEntity(player, () -> updatePlayer(player));
        }
    }

    /**
     * Updates an individual player's main inventory, equipment, and ender chest safely.
     */
    public void updatePlayer(Player player) {
        if (player == null || !player.isValid()) return;

        // 1. Update main inventory & armor
        updateInventory(player.getInventory());

        // 2. Update ender chest
        updateInventory(player.getEnderChest());
    }

    /**
     * Scans and updates outdated RoyItems in an inventory.
     */
    public void updateInventory(Inventory inventory) {
        if (inventory == null) return;

        ItemStack[] contents = inventory.getContents();
        boolean changed = false;

        for (int i = 0; i < contents.length; i++) {
            ItemStack current = contents[i];
            if (current == null || !pdcManager.isRoyItem(current)) continue;

            ItemType type = pdcManager.getType(current);
            String id = pdcManager.getId(current);
            ItemDefinition def = registry.get(type, id);

            if (def == null) continue; // Definition removed or not loaded

            int currentVersion = pdcManager.getVersion(current);
            // If version differs or live reload requested
            if (currentVersion != def.getVersion()) {
                String socketsRaw = pdcManager.getSocketsData(current);
                List<String> sockets = ItemBuilder.deserializeSockets(socketsRaw);
                long expire = pdcManager.getExpireTime(current);

                ItemStack updated = itemBuilder.build(def, current.getAmount(), sockets, expire);
                contents[i] = updated;
                changed = true;
            }
        }

        if (changed) {
            inventory.setContents(contents);
        }
    }
}
