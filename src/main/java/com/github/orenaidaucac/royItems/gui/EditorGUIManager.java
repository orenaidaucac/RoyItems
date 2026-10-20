package com.github.orenaidaucac.royItems.gui;

import com.github.orenaidaucac.royItems.RoyItems;
import com.github.orenaidaucac.royItems.core.config.ItemConfigManager;
import com.github.orenaidaucac.royItems.core.platform.PlatformScheduler;
import com.github.orenaidaucac.royItems.item.ItemBuilder;
import com.github.orenaidaucac.royItems.item.ItemDefinition;
import com.github.orenaidaucac.royItems.item.ItemRegistry;
import com.github.orenaidaucac.royItems.item.ItemType;
import com.github.orenaidaucac.royItems.item.LiveUpdateService;
import dev.triumphteam.gui.guis.BaseGui;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryCloseEvent;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Coordinates GUI Editor opening, navigation, safety guards, and lifecycle cleanup using TriumphGUI.
 */
public class EditorGUIManager implements Listener {

    private final RoyItems plugin;
    private final ItemRegistry registry;
    private final ItemConfigManager configManager;
    private final ItemBuilder itemBuilder;
    private final PlatformScheduler scheduler;
    private final EditorSessionManager sessionManager;
    private final EditorGuiConfig guiConfig;
    private final EditorChatInputManager chatInputManager;

    private final Map<UUID, BaseGui> openGuis = new ConcurrentHashMap<>();

    public EditorGUIManager(RoyItems plugin, ItemRegistry registry, ItemConfigManager configManager,
                            ItemBuilder itemBuilder, PlatformScheduler scheduler) {
        this.plugin = plugin;
        this.registry = registry;
        this.configManager = configManager;
        this.itemBuilder = itemBuilder;
        this.scheduler = scheduler;
        this.sessionManager = new EditorSessionManager();
        this.guiConfig = new EditorGuiConfig(plugin);
        this.guiConfig.load();
        this.chatInputManager = new EditorChatInputManager(plugin, this);
    }

    public RoyItems getPlugin() {
        return plugin;
    }

    public ItemRegistry getRegistry() {
        return registry;
    }

    public ItemConfigManager getConfigManager() {
        return configManager;
    }

    public ItemBuilder getItemBuilder() {
        return itemBuilder;
    }

    public PlatformScheduler getScheduler() {
        return scheduler;
    }

    public EditorSessionManager getSessionManager() {
        return sessionManager;
    }

    public EditorGuiConfig getGuiConfig() {
        return guiConfig;
    }

    public EditorChatInputManager getChatInputManager() {
        return chatInputManager;
    }

    public LiveUpdateService getLiveUpdateService() {
        return plugin.getLiveUpdateService();
    }

    public void reloadConfig() {
        guiConfig.load();
    }

    public void registerGui(Player player, BaseGui gui) {
        openGuis.put(player.getUniqueId(), gui);
    }

    public void openMainDashboard(Player player) {
        new MainTypeSelectorGui(this, player).open();
    }

    public void openBrowser(Player player, ItemType type) {
        new ItemBrowserGui(this, player, type).open();
    }

    public void openDetailEditor(Player player, ItemDefinition def) {
        openDetailEditor(player, def, 1);
    }

    public void openDetailEditor(Player player, ItemDefinition def, int page) {
        new ItemDetailEditorGui(this, player, def, page).open();
    }

    /**
     * Closes all active GUI editor windows cleanly on reload or disable.
     */
    public void closeAll() {
        if (chatInputManager != null) {
            chatInputManager.clearAll();
        }

        for (Map.Entry<UUID, BaseGui> entry : openGuis.entrySet()) {
            Player player = Bukkit.getPlayer(entry.getKey());
            if (player != null && player.isOnline()) {
                scheduler.runEntity(player, () -> {
                    try {
                        entry.getValue().close(player);
                    } catch (Throwable ignored) {
                        player.closeInventory();
                    }
                });
            }
        }
        openGuis.clear();
        sessionManager.clearAll();
    }

    @EventHandler
    public void onInventoryClose(InventoryCloseEvent event) {
        if (event.getPlayer() instanceof Player player) {
            openGuis.remove(player.getUniqueId());
            // Note: We only release locks if the player isn't currently in a chat input session
            // (When in chat input, inventory closes temporarily but session must stay locked)
        }
    }
}
