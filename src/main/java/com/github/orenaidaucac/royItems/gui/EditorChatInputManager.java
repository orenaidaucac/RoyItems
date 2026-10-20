package com.github.orenaidaucac.royItems.gui;

import com.github.orenaidaucac.royItems.RoyItems;
import com.github.orenaidaucac.royItems.core.text.TextUtility;
import com.github.orenaidaucac.royItems.item.ItemDefinition;
import io.papermc.paper.event.player.AsyncChatEvent;
import org.bukkit.Bukkit;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.AsyncPlayerChatEvent;
import org.bukkit.event.player.PlayerQuitEvent;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;
import java.util.function.Predicate;

/**
 * Manages asynchronous chat input for the In-game Item Editor.
 * Safely captures input, validates values, supports cancel/timeout,
 * and reopens the paginated GUI on the player's entity thread (Folia and Paper safe).
 */
public class EditorChatInputManager implements Listener {

    public record ChatSession(
            Player player,
            ItemDefinition def,
            int page,
            String propertyName,
            Predicate<String> validator,
            Consumer<String> consumer,
            long createdAt
    ) {}

    private final RoyItems plugin;
    private final EditorGUIManager guiManager;
    private final Map<UUID, ChatSession> activeSessions = new ConcurrentHashMap<>();

    public EditorChatInputManager(RoyItems plugin, EditorGUIManager guiManager) {
        this.plugin = plugin;
        this.guiManager = guiManager;
        startTimeoutTask();
    }

    /**
     * Prompts the player to enter a new value in chat for an item property.
     */
    public void requestInput(Player player, ItemDefinition def, int page,
                             String propertyName, String currentValue,
                             String promptHelp, Predicate<String> validator,
                             Consumer<String> consumer) {
        ChatSession session = new ChatSession(
                player, def, page, propertyName, validator, consumer, System.currentTimeMillis()
        );
        activeSessions.put(player.getUniqueId(), session);

        player.closeInventory();
        player.sendMessage(TextUtility.formatMessage("&8&m----------------------------------------------------"));
        player.sendMessage(TextUtility.formatMessage("&6&lRoyItems &8— &eChỉnh sửa: &f" + propertyName));
        if (currentValue != null && !currentValue.isEmpty()) {
            player.sendMessage(TextUtility.formatMessage("&7Giá trị hiện tại: &f" + currentValue));
        }
        player.sendMessage(TextUtility.formatMessage("&e" + promptHelp));
        player.sendMessage(TextUtility.formatMessage("&7Gõ &c'cancel'&7 hoặc &c'huy'&7 để hủy bỏ thao tác."));
        player.sendMessage(TextUtility.formatMessage("&8&m----------------------------------------------------"));

        try {
            player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_PLING, 1.0f, 1.0f);
        } catch (Throwable ignored) {}
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onAsyncChat(AsyncChatEvent event) {
        Player player = event.getPlayer();
        ChatSession session = activeSessions.remove(player.getUniqueId());
        if (session == null) return;

        event.setCancelled(true);
        String rawMessage = TextUtility.toPlain(event.message()).trim();
        processInput(player, session, rawMessage);
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onLegacyChat(AsyncPlayerChatEvent event) {
        Player player = event.getPlayer();
        ChatSession session = activeSessions.remove(player.getUniqueId());
        if (session == null) return;

        event.setCancelled(true);
        String rawMessage = event.getMessage().trim();
        processInput(player, session, rawMessage);
    }

    private void processInput(Player player, ChatSession session, String input) {
        // Handle cancel
        if (input.equalsIgnoreCase("cancel") || input.equalsIgnoreCase("huy") || input.equalsIgnoreCase("exit")) {
            player.sendMessage(TextUtility.formatMessage("&c[RoyItems] Đã hủy thao tác chỉnh sửa."));
            plugin.getPlatformScheduler().runEntity(player, () -> {
                guiManager.openDetailEditor(player, session.def(), session.page());
            });
            return;
        }

        // Validate
        if (session.validator() != null && !session.validator().test(input)) {
            player.sendMessage(TextUtility.formatMessage("&c[RoyItems] Giá trị nhập không hợp lệ! Vui lòng thử lại hoặc gõ &f'cancel'&c để hủy."));
            // Keep session active for retry
            activeSessions.put(player.getUniqueId(), session);
            return;
        }

        // Apply changes
        try {
            session.consumer().accept(input);
            player.sendMessage(TextUtility.formatMessage("&a[RoyItems] Đã cập nhật &e" + session.propertyName() + " &athành: &f" + input));
            try {
                player.playSound(player.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 1.0f, 1.2f);
            } catch (Throwable ignored) {}
        } catch (Exception e) {
            player.sendMessage(TextUtility.formatMessage("&c[RoyItems] Lỗi khi xử lý dữ liệu: &f" + e.getMessage()));
        }

        // Reopen Editor on entity thread
        plugin.getPlatformScheduler().runEntity(player, () -> {
            guiManager.openDetailEditor(player, session.def(), session.page());
        });
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        activeSessions.remove(event.getPlayer().getUniqueId());
    }

    public void clearAll() {
        activeSessions.clear();
    }

    private void startTimeoutTask() {
        plugin.getPlatformScheduler().runAsyncTimer(() -> {
            long now = System.currentTimeMillis();
            for (Map.Entry<UUID, ChatSession> entry : activeSessions.entrySet()) {
                if (now - entry.getValue().createdAt() > 60000L) { // 60 seconds
                    activeSessions.remove(entry.getKey());
                    Player player = Bukkit.getPlayer(entry.getKey());
                    if (player != null && player.isOnline()) {
                        player.sendMessage(TextUtility.formatMessage("&c[RoyItems] Phiên nhập liệu đã hết hạn do quá thời gian chờ (60s)."));
                    }
                }
            }
        }, 200L, 200L);
    }
}
