package com.github.orenaidaucac.royItems.gui;

import com.github.orenaidaucac.royItems.item.ItemType;
import org.bukkit.entity.Player;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Manages active editor sessions and prevents conflicting edits between multiple administrators.
 */
public class EditorSessionManager {

    // Key: "TYPE:ID" -> Admin UUID
    private final Map<String, UUID> activeLocks = new ConcurrentHashMap<>();

    private String buildKey(ItemType type, String id) {
        return type.name() + ":" + id.toLowerCase();
    }

    public boolean acquireLock(Player player, ItemType type, String id) {
        String key = buildKey(type, id);
        UUID current = activeLocks.get(key);
        if (current == null || current.equals(player.getUniqueId())) {
            activeLocks.put(key, player.getUniqueId());
            return true;
        }
        return false;
    }

    public void releaseLock(Player player, ItemType type, String id) {
        String key = buildKey(type, id);
        activeLocks.remove(key, player.getUniqueId());
    }

    public void releaseAllForPlayer(Player player) {
        activeLocks.values().removeIf(uuid -> uuid.equals(player.getUniqueId()));
    }

    public void clearAll() {
        activeLocks.clear();
    }
}
