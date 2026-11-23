package com.github.orenaidaucac.royItems.extraction;

import com.github.orenaidaucac.royItems.item.ItemDefinition;
import org.bukkit.inventory.ItemStack;

import java.util.UUID;

/**
 * Tracks an active enchantment extraction GUI session for a player.
 */
public class ExtractionSession {

    private final UUID playerId;
    private final int targetSlot;
    private final ItemStack targetSnapshot;
    private final ItemDefinition consumableDef;
    private final String consumableId;

    public ExtractionSession(UUID playerId, int targetSlot, ItemStack targetSnapshot,
                             ItemDefinition consumableDef, String consumableId) {
        this.playerId = playerId;
        this.targetSlot = targetSlot;
        this.targetSnapshot = targetSnapshot != null ? targetSnapshot.clone() : null;
        this.consumableDef = consumableDef;
        this.consumableId = consumableId != null ? consumableId : "";
    }

    public UUID getPlayerId() {
        return playerId;
    }

    public int getTargetSlot() {
        return targetSlot;
    }

    public ItemStack getTargetSnapshot() {
        return targetSnapshot;
    }

    public ItemDefinition getConsumableDef() {
        return consumableDef;
    }

    public String getConsumableId() {
        return consumableId;
    }
}
