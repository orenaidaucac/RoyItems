package com.github.orenaidaucac.royItems.stat;

import com.github.orenaidaucac.royItems.item.ItemBuilder;
import com.github.orenaidaucac.royItems.item.ItemDefinition;
import com.github.orenaidaucac.royItems.item.ItemPDCManager;
import com.github.orenaidaucac.royItems.item.ItemRegistry;
import com.github.orenaidaucac.royItems.item.ItemType;
import com.github.orenaidaucac.royItems.tier_set.ItemSetManager;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Event-driven Stat Manager that computes and caches player stats from equipped items,
 * inventory talismans, slotted gems, and set bonuses.
 */
public class StatManager {

    private final ItemRegistry registry;
    private final ItemPDCManager pdcManager;
    private final ItemSetManager setManager;
    private final Map<UUID, PlayerStatCache> playerCaches = new ConcurrentHashMap<>();

    public StatManager(ItemRegistry registry, ItemPDCManager pdcManager, ItemSetManager setManager) {
        this.registry = registry;
        this.pdcManager = pdcManager;
        this.setManager = setManager;
    }

    public PlayerStatCache getCache(Player player) {
        if (player == null) return new PlayerStatCache();
        return playerCaches.computeIfAbsent(player.getUniqueId(), k -> new PlayerStatCache());
    }

    public void removePlayer(UUID uuid) {
        playerCaches.remove(uuid);
    }

    /**
     * Recalculates all stats for a player based on equipment, gems, talismans, and sets.
     */
    public void recalculate(Player player) {
        if (player == null || !player.isValid()) return;

        PlayerStatCache cache = getCache(player);
        cache.reset();

        PlayerInventory inv = player.getInventory();
        Map<String, Integer> setCounts = new HashMap<>();

        // 1. Process Main Hand
        processItem(inv.getItemInMainHand(), cache, setCounts);

        // 2. Process Off Hand
        processItem(inv.getItemInOffHand(), cache, setCounts);

        // 3. Process Armor pieces
        for (ItemStack armor : inv.getArmorContents()) {
            processItem(armor, cache, setCounts);
        }

        // 4. Process Talismans in Inventory
        for (ItemStack item : inv.getStorageContents()) {
            if (item != null && pdcManager.isRoyItem(item)) {
                ItemType type = pdcManager.getType(item);
                if (type == ItemType.TALISMAN) {
                    processItem(item, cache, setCounts);
                }
            }
        }

        // 5. Apply Item Set Bonuses
        if (setManager != null) {
            setManager.applySetBonuses(setCounts, cache);
        }
    }

    private void processItem(ItemStack item, PlayerStatCache cache, Map<String, Integer> setCounts) {
        if (item == null || !pdcManager.isRoyItem(item)) return;

        ItemType type = pdcManager.getType(item);
        String id = pdcManager.getId(item);
        ItemDefinition def = registry.get(type, id);
        if (def == null) return;

        // Base item stats
        if (def.getStats() != null) {
            for (Map.Entry<StatType, StatModifier> entry : def.getStats().entrySet()) {
                cache.addStat(entry.getKey(), entry.getValue());
            }
        }

        // Slotted gems stats
        String socketsRaw = pdcManager.getSocketsData(item);
        if (socketsRaw != null && !socketsRaw.isEmpty()) {
            List<String> slotted = ItemBuilder.deserializeSockets(socketsRaw);
            for (String gemId : slotted) {
                if (gemId == null || gemId.isEmpty()) continue;
                ItemDefinition gemDef = registry.get(ItemType.GEM_STONE, gemId);
                if (gemDef != null && gemDef.getGemStats() != null) {
                    for (Map.Entry<StatType, StatModifier> gemEntry : gemDef.getGemStats().entrySet()) {
                        cache.addStat(gemEntry.getKey(), gemEntry.getValue());
                    }
                }
            }
        }

        // Track Set items
        if (def.getItemSet() != null && !def.getItemSet().isEmpty()) {
            setCounts.put(def.getItemSet(), setCounts.getOrDefault(def.getItemSet(), 0) + 1);
        }
    }
}
