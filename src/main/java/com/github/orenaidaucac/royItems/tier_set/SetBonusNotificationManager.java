package com.github.orenaidaucac.royItems.tier_set;

import com.github.orenaidaucac.royItems.core.message.MessageManager;
import com.github.orenaidaucac.royItems.core.platform.PlatformScheduler;
import com.github.orenaidaucac.royItems.core.text.TextUtility;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Manages automated notifications when players activate or deactivate Item Set bonuses,
 * featuring debouncing against rapid inventory clicks and suppression during join/respawn transitions.
 */
public class SetBonusNotificationManager {

    private final Plugin plugin;
    private final ItemSetManager setManager;
    private final MessageManager messageManager;
    private final PlatformScheduler scheduler;

    // Track active sets per player
    private final Map<UUID, Set<String>> lastNotifiedActiveSets = new ConcurrentHashMap<>();
    // Suppressed players during join/respawn/death transitions
    private final Set<UUID> suppressedPlayers = ConcurrentHashMap.newKeySet();
    // Track debounced check token per player to coalesce rapid inventory clicks
    private final Map<UUID, Long> debounceTokens = new ConcurrentHashMap<>();

    private boolean notifyActivated = true;
    private boolean notifyDeactivated = true;
    private long debounceTicks = 2L;

    public SetBonusNotificationManager(Plugin plugin, ItemSetManager setManager,
                                       MessageManager messageManager, PlatformScheduler scheduler) {
        this.plugin = plugin;
        this.setManager = setManager;
        this.messageManager = messageManager;
        this.scheduler = scheduler;
        loadConfig();
    }

    public void loadConfig() {
        if (plugin != null && plugin.getConfig() != null) {
            ConfigurationSection sec = plugin.getConfig().getConfigurationSection("set-bonus.notifications");
            if (sec != null) {
                this.notifyActivated = sec.getBoolean("activated", true);
                this.notifyDeactivated = sec.getBoolean("deactivated", true);
                this.debounceTicks = sec.getLong("debounce-ticks", 2L);
            } else {
                this.notifyActivated = plugin.getConfig().getBoolean("set-bonus.notifications.activated", true);
                this.notifyDeactivated = plugin.getConfig().getBoolean("set-bonus.notifications.deactivated", true);
                this.debounceTicks = plugin.getConfig().getLong("set-bonus.notifications.debounce-ticks", 2L);
            }
        }
    }

    public void markPlayerJoining(UUID uuid) {
        if (uuid != null) {
            suppressedPlayers.add(uuid);
        }
    }

    public void markPlayerRespawning(UUID uuid) {
        if (uuid != null) {
            suppressedPlayers.add(uuid);
        }
    }

    public void cleanupPlayer(UUID uuid) {
        if (uuid != null) {
            lastNotifiedActiveSets.remove(uuid);
            suppressedPlayers.remove(uuid);
            debounceTokens.remove(uuid);
        }
    }

    public void initializePlayer(Player player, Set<String> initialActiveSets) {
        if (player == null) return;
        UUID uuid = player.getUniqueId();
        lastNotifiedActiveSets.put(uuid, initialActiveSets != null ? new HashSet<>(initialActiveSets) : new HashSet<>());
        suppressedPlayers.remove(uuid);
    }

    /**
     * Handles set bonus changes for a player.
     * Uses a debounce delay so that picking up and placing down items rapidly in inventory
     * does not cause duplicate activation/deactivation messages.
     */
    public void handleActiveSetsChanged(Player player, Set<String> currentActiveSets,
                                        com.github.orenaidaucac.royItems.stat.StatManager statManager) {
        if (player == null || !player.isOnline()) return;
        UUID uuid = player.getUniqueId();

        // If player is joining or respawning, silently record initial state without spamming
        if (suppressedPlayers.contains(uuid)) {
            initializePlayer(player, currentActiveSets);
            return;
        }

        Set<String> previous = lastNotifiedActiveSets.get(uuid);
        if (previous == null) {
            // First time seeing player: initialize quietly
            initializePlayer(player, currentActiveSets);
            return;
        }

        // Fast path: if state is identical, nothing to do
        if (previous.equals(currentActiveSets)) {
            return;
        }

        // Schedule debounced check on player's entity thread
        long token = System.currentTimeMillis();
        debounceTokens.put(uuid, token);

        if (scheduler == null || debounceTicks <= 0) {
            dispatchNotifications(player, currentActiveSets, statManager);
            return;
        }

        scheduler.runEntityDelayed(player, () -> {
            if (!player.isOnline() || !player.isValid()) return;
            Long currentToken = debounceTokens.get(uuid);
            if (currentToken == null || currentToken != token) {
                // A newer change arrived during the debounce window -> coalesced!
                return;
            }

            // Re-evaluate current active sets at the moment debounce completes
            Set<String> latestActive = statManager != null
                    ? statManager.getActiveItemSets(player)
                    : currentActiveSets;

            dispatchNotifications(player, latestActive, statManager);
        }, debounceTicks);
    }

    private void dispatchNotifications(Player player, Set<String> currentActive,
                                       com.github.orenaidaucac.royItems.stat.StatManager statManager) {
        if (player == null || !player.isOnline()) return;
        UUID uuid = player.getUniqueId();

        Set<String> previous = lastNotifiedActiveSets.getOrDefault(uuid, Collections.emptySet());
        if (previous.equals(currentActive)) {
            return; // State returned to previous within the debounce window -> no alert
        }

        // 1. Newly activated sets
        if (isNotifyActivated()) {
            for (String setId : currentActive) {
                if (!previous.contains(setId)) {
                    sendActivationNotification(player, setId, statManager);
                }
            }
        }

        // 2. Newly deactivated sets
        if (isNotifyDeactivated()) {
            for (String setId : previous) {
                if (!currentActive.contains(setId)) {
                    sendDeactivationNotification(player, setId, statManager);
                }
            }
        }

        lastNotifiedActiveSets.put(uuid, new HashSet<>(currentActive));
    }

    private void sendActivationNotification(Player player, String setId, com.github.orenaidaucac.royItems.stat.StatManager statManager) {
        ItemSet set = setManager != null ? setManager.get(setId) : null;
        String displayName = set != null && set.getDisplayName() != null ? set.getDisplayName() : setId;
        int pieces = (statManager != null) ? statManager.getEquippedSetPieceCount(player, setId) : 0;
        int required = (set != null) ? set.getRequiredPieces() : 1;

        Map<String, String> placeholders = new HashMap<>();
        placeholders.put("set", displayName);
        placeholders.put("set-name", displayName);
        placeholders.put("set-display", displayName);
        placeholders.put("set-id", setId);
        placeholders.put("pieces", String.valueOf(pieces));
        placeholders.put("count", String.valueOf(pieces));
        placeholders.put("required", String.valueOf(required));
        placeholders.put("required-pieces", String.valueOf(required));
        placeholders.put("player", player.getName());

        String defaultMsg = "<gradient:#FFD166:#06D6A0>RoyItems</gradient> <dark_gray>»</dark_gray> &aBạn đã kích hoạt hiệu ứng bộ trang bị &e#set#&a!";
        if (messageManager != null) {
            String msg = messageManager.format("set-bonus-activated", defaultMsg, placeholders);
            if (msg != null && !msg.isBlank() && !msg.equalsIgnoreCase("none") && !msg.equalsIgnoreCase("false")) {
                player.sendMessage(TextUtility.formatMessage(msg));
            }
        }
    }

    private void sendDeactivationNotification(Player player, String setId, com.github.orenaidaucac.royItems.stat.StatManager statManager) {
        ItemSet set = setManager != null ? setManager.get(setId) : null;
        String displayName = set != null && set.getDisplayName() != null ? set.getDisplayName() : setId;
        int pieces = (statManager != null) ? statManager.getEquippedSetPieceCount(player, setId) : 0;
        int required = (set != null) ? set.getRequiredPieces() : 1;

        Map<String, String> placeholders = new HashMap<>();
        placeholders.put("set", displayName);
        placeholders.put("set-name", displayName);
        placeholders.put("set-display", displayName);
        placeholders.put("set-id", setId);
        placeholders.put("pieces", String.valueOf(pieces));
        placeholders.put("count", String.valueOf(pieces));
        placeholders.put("required", String.valueOf(required));
        placeholders.put("required-pieces", String.valueOf(required));
        placeholders.put("player", player.getName());

        String defaultMsg = "<gradient:#FFD166:#06D6A0>RoyItems</gradient> <dark_gray>»</dark_gray> &cHiệu ứng bộ trang bị &e#set# &cđã mất hiệu lực do tháo trang bị!";
        if (messageManager != null) {
            String msg = messageManager.format("set-bonus-deactivated", defaultMsg, placeholders);
            if (msg != null && !msg.isBlank() && !msg.equalsIgnoreCase("none") && !msg.equalsIgnoreCase("false")) {
                player.sendMessage(TextUtility.formatMessage(msg));
            }
        }
    }

    public boolean isNotifyActivated() {
        return notifyActivated;
    }

    public void setNotifyActivated(boolean notifyActivated) {
        this.notifyActivated = notifyActivated;
    }

    public boolean isNotifyDeactivated() {
        return notifyDeactivated;
    }

    public void setNotifyDeactivated(boolean notifyDeactivated) {
        this.notifyDeactivated = notifyDeactivated;
    }

    public long getDebounceTicks() {
        return debounceTicks;
    }

    public void setDebounceTicks(long debounceTicks) {
        this.debounceTicks = debounceTicks;
    }

    public Map<UUID, Set<String>> getLastNotifiedActiveSets() {
        return lastNotifiedActiveSets;
    }
}
