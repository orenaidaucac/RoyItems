package com.github.orenaidaucac.royItems.socket;

import com.github.orenaidaucac.royItems.core.text.TextUtility;
import com.github.orenaidaucac.royItems.item.*;
import com.github.orenaidaucac.royItems.lore.DynamicLoreEngine;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.List;

/**
 * Handles Gem Stone socketing logic, socket compatibility checking, and item rebuilding.
 */
public class GemSocketManager {

    private final ItemRegistry registry;
    private final ItemPDCManager pdcManager;
    private final DynamicLoreEngine loreEngine;

    public GemSocketManager(ItemRegistry registry, ItemPDCManager pdcManager, DynamicLoreEngine loreEngine) {
        this.registry = registry;
        this.pdcManager = pdcManager;
        this.loreEngine = loreEngine;
    }

    /**
     * Checks if a gem stone can be slotted into a target item.
     * Returns the index of the first compatible empty socket, or -1 if none available.
     */
    public int findAvailableSocket(ItemDefinition targetDef, List<String> currentSlotted, String gemTypeId) {
        if (targetDef == null || targetDef.getGemSockets() == null || gemTypeId == null) return -1;

        List<String> sockets = targetDef.getGemSockets();
        for (int i = 0; i < sockets.size(); i++) {
            // Check if socket is empty
            if (i >= currentSlotted.size() || currentSlotted.get(i) == null || currentSlotted.get(i).isEmpty()) {
                String socketType = sockets.get(i);
                if (isSocketCompatible(socketType, gemTypeId)) {
                    return i;
                }
            }
        }
        return -1;
    }

    /**
     * Compatibility rule:
     * - "1": Universal socket, accepts any gem
     * - Socket matches or contains the gem's type id
     */
    public boolean isSocketCompatible(String socketType, String gemTypeId) {
        if (socketType == null || gemTypeId == null) return false;
        if (socketType.equalsIgnoreCase("1")) return true;
        if (socketType.equalsIgnoreCase(gemTypeId)) return true;
        return socketType.toUpperCase().contains(gemTypeId.toUpperCase());
    }

    /**
     * Sockets a gem stone into a target item stack.
     */
    public boolean socketGem(Player player, ItemStack targetItem, ItemStack gemItem) {
        if (targetItem == null || gemItem == null) return false;
        if (!pdcManager.isRoyItem(targetItem) || !pdcManager.isRoyItem(gemItem)) return false;

        ItemType gemType = pdcManager.getType(gemItem);
        if (gemType != ItemType.GEM_STONE) return false;

        String gemId = pdcManager.getId(gemItem);
        ItemDefinition gemDef = registry.get(ItemType.GEM_STONE, gemId);
        if (gemDef == null) return false;

        ItemType targetType = pdcManager.getType(targetItem);
        String targetId = pdcManager.getId(targetItem);
        ItemDefinition targetDef = registry.get(targetType, targetId);
        if (targetDef == null || targetDef.getGemSockets() == null || targetDef.getGemSockets().isEmpty()) return false;

        String socketsRaw = pdcManager.getSocketsData(targetItem);
        List<String> slotted = ItemBuilder.deserializeSockets(socketsRaw);

        // Ensure list has enough capacity
        while (slotted.size() < targetDef.getGemSockets().size()) {
            slotted.add("");
        }

        String gemTypeId = gemDef.getGemId() != null ? gemDef.getGemId() : gemDef.getId();
        int slotIndex = findAvailableSocket(targetDef, slotted, gemTypeId);
        if (slotIndex == -1) {
            player.sendMessage(TextUtility.formatMessage("&cNo compatible empty socket found on this item!"));
            return false;
        }

        // Slot the gem
        slotted.set(slotIndex, gemDef.getName() != null && !gemDef.getName().isEmpty() ? gemDef.getName() : gemDef.getId());

        // Update target item
        pdcManager.updateSockets(targetItem, String.join(";;", slotted));

        // Rebuild lore
        ItemMeta meta = targetItem.getItemMeta();
        long expire = pdcManager.getExpireTime(targetItem);
        List<String> newLore = loreEngine.buildLore(targetDef, slotted, expire);
        meta.lore(TextUtility.formatLore(newLore));
        targetItem.setItemMeta(meta);

        // Audio & Visual feedback
        player.playSound(player.getLocation(), Sound.BLOCK_ANVIL_USE, 0.8f, 1.2f);
        player.sendMessage(TextUtility.formatMessage("&aSuccessfully socketed &f" +
                (gemDef.getName() != null ? gemDef.getName() : gemDef.getId()) + " &ainto socket #" + (slotIndex + 1) + "!"));

        return true;
    }
}
