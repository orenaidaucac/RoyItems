package com.github.orenaidaucac.royItems.mechanics;

import org.bukkit.entity.Player;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Manages player action cooldowns for custom items.
 */
public class CooldownManager {

    // Key: UUID:itemId -> expiry timestamp (millis)
    private final Map<String, Long> cooldowns = new ConcurrentHashMap<>();

    private String buildKey(UUID uuid, String itemId) {
        return uuid.toString() + ":" + itemId.toLowerCase();
    }

    public boolean isOnCooldown(Player player, String itemId) {
        if (player == null || itemId == null) return false;
        String key = buildKey(player.getUniqueId(), itemId);
        Long expiry = cooldowns.get(key);
        if (expiry == null) return false;
        if (System.currentTimeMillis() >= expiry) {
            cooldowns.remove(key);
            return false;
        }
        return true;
    }

    public double getRemainingSeconds(Player player, String itemId) {
        if (player == null || itemId == null) return 0.0;
        String key = buildKey(player.getUniqueId(), itemId);
        Long expiry = cooldowns.get(key);
        if (expiry == null) return 0.0;
        long remaining = expiry - System.currentTimeMillis();
        return remaining > 0 ? (remaining / 1000.0) : 0.0;
    }

    public void setCooldown(Player player, String itemId, double seconds) {
        if (player == null || itemId == null || seconds <= 0) return;
        String key = buildKey(player.getUniqueId(), itemId);
        long expiry = System.currentTimeMillis() + (long) (seconds * 1000.0);
        cooldowns.put(key, expiry);
    }

    public void removePlayer(UUID uuid) {
        cooldowns.keySet().removeIf(k -> k.startsWith(uuid.toString()));
    }
}
