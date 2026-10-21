package com.github.orenaidaucac.royItems.item;

import com.github.orenaidaucac.royItems.core.platform.PlatformScheduler;
import com.github.orenaidaucac.royItems.stat.StatManager;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.Damageable;

import java.util.Collection;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;

/**
 * LiveUpdate Service: Automatically updates custom items in player inventories and ender chests
 * when admin reloads or edits definitions, preserving instance data (sockets, expiration, durability).
 */
public class LiveUpdateService {

    private final ItemRegistry registry;
    private final ItemPDCManager pdcManager;
    private final ItemBuilder itemBuilder;
    private final PlatformScheduler scheduler;
    private StatManager statManager;

    public LiveUpdateService(ItemRegistry registry, ItemPDCManager pdcManager, ItemBuilder itemBuilder, PlatformScheduler scheduler) {
        this.registry = registry;
        this.pdcManager = pdcManager;
        this.itemBuilder = itemBuilder;
        this.scheduler = scheduler;
    }

    public void setStatManager(StatManager statManager) {
        this.statManager = statManager;
    }

    /**
     * Updates all online players' inventories and ender chests asynchronously and safely,
     * invoking the callback with a detailed report when all players have finished processing.
     */
    public void updateAllOnlinePlayers(Consumer<LiveUpdateReport> callback) {
        Collection<? extends Player> players = Bukkit.getOnlinePlayers();
        if (players.isEmpty()) {
            if (callback != null) {
                callback.accept(new LiveUpdateReport(0, 0, 0, 0));
            }
            return;
        }

        AtomicInteger invTotal = new AtomicInteger(0);
        AtomicInteger ecTotal = new AtomicInteger(0);
        AtomicInteger failTotal = new AtomicInteger(0);
        AtomicInteger pending = new AtomicInteger(players.size());

        for (Player player : players) {
            scheduler.runEntity(player, () -> {
                try {
                    if (player != null && player.isValid()) {
                        // 1. Update main inventory, armor, and offhand
                        int invCount = updateInventory(player.getInventory(), failTotal);
                        invTotal.addAndGet(invCount);

                        // 2. Update ender chest
                        int ecCount = updateInventory(player.getEnderChest(), failTotal);
                        ecTotal.addAndGet(ecCount);

                        // 3. Recalculate stats for online player immediately
                        if (statManager != null) {
                            statManager.recalculate(player);
                        }
                    }
                } catch (Throwable t) {
                    failTotal.incrementAndGet();
                } finally {
                    if (pending.decrementAndGet() == 0) {
                        if (callback != null) {
                            LiveUpdateReport report = new LiveUpdateReport(
                                    invTotal.get(), ecTotal.get(), failTotal.get(), players.size());
                            callback.accept(report);
                        }
                    }
                }
            });
        }
    }

    /**
     * Backward-compatible call without report callback.
     */
    public void updateAllOnlinePlayers() {
        updateAllOnlinePlayers(null);
    }

    /**
     * Updates an individual player's main inventory, equipment, and ender chest safely.
     */
    public void updatePlayer(Player player) {
        if (player == null || !player.isValid()) return;

        // 1. Update main inventory & armor
        updateInventory(player.getInventory(), null);

        // 2. Update ender chest
        updateInventory(player.getEnderChest(), null);

        // 3. Recalculate stats
        if (statManager != null) {
            statManager.recalculate(player);
        }
    }

    /**
     * Scans and updates RoyItems in an inventory, returning the number of items successfully updated.
     */
    public int updateInventory(Inventory inventory, AtomicInteger failCounter) {
        if (inventory == null) return 0;

        ItemStack[] contents = inventory.getContents();
        int updatedCount = 0;
        boolean changed = false;

        for (int i = 0; i < contents.length; i++) {
            ItemStack current = contents[i];
            if (current == null || !pdcManager.isRoyItem(current)) continue;

            ItemType type = pdcManager.getType(current);
            String id = pdcManager.getId(current);
            ItemDefinition def = registry.get(type, id);

            if (def == null) continue; // Definition removed or not loaded

            try {
                // Read instance metadata
                String socketsRaw = pdcManager.getSocketsData(current);
                List<String> sockets = ItemBuilder.deserializeSockets(socketsRaw);
                long expire = pdcManager.getExpireTime(current);

                // Build updated item
                ItemStack updated = itemBuilder.build(def, current.getAmount(), sockets, expire);

                // Preserve damage/durability if item is damageable and not unbreakable
                if (current.getItemMeta() instanceof Damageable curDmg
                        && updated.getItemMeta() instanceof Damageable updDmg) {
                    if (curDmg.hasDamage() && !def.isUnbreakable()) {
                        updDmg.setDamage(curDmg.getDamage());
                        updated.setItemMeta(updDmg);
                    }
                }

                contents[i] = updated;
                changed = true;
                updatedCount++;
            } catch (Throwable t) {
                if (failCounter != null) {
                    failCounter.incrementAndGet();
                }
            }
        }

        if (changed) {
            inventory.setContents(contents);
        }
        return updatedCount;
    }

    public int updateInventory(Inventory inventory) {
        return updateInventory(inventory, null);
    }
}
