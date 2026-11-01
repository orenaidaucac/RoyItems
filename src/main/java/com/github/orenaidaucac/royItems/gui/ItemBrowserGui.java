package com.github.orenaidaucac.royItems.gui;

import com.github.orenaidaucac.royItems.core.text.TextUtility;
import com.github.orenaidaucac.royItems.item.ItemDefinition;
import com.github.orenaidaucac.royItems.item.ItemType;
import dev.triumphteam.gui.guis.Gui;
import dev.triumphteam.gui.guis.GuiItem;
import dev.triumphteam.gui.guis.PaginatedGui;
import net.kyori.adventure.text.Component;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Paginated browser showing all custom items of a specific ItemType.
 * Fully configurable via gui/editor-gui.yml, 100% anti-italic by default.
 */
public class ItemBrowserGui {

    private final EditorGUIManager manager;
    private final Player player;
    private final ItemType type;
    private final EditorGuiConfig config;

    public ItemBrowserGui(EditorGUIManager manager, Player player, ItemType type) {
        this.manager = manager;
        this.player = player;
        this.type = type;
        this.config = manager.getGuiConfig();
    }

    public void open() {
        List<ItemDefinition> items = new ArrayList<>(manager.getRegistry().getByType(type));

        // Sort items strictly according to the top-to-bottom order in the configuration file
        if (manager.getConfigManager() != null) {
            List<String> fileOrder = manager.getConfigManager().getItemKeysInFileOrder(type);
            if (!fileOrder.isEmpty()) {
                Map<String, Integer> orderMap = new HashMap<>();
                for (int i = 0; i < fileOrder.size(); i++) {
                    orderMap.put(fileOrder.get(i).toLowerCase(), i);
                }
                items.sort(Comparator.comparingInt(def -> orderMap.getOrDefault(def.getId().toLowerCase(), Integer.MAX_VALUE)));
            }
        }

        PaginatedGui gui = Gui.paginated()
                .title(config.getItemBrowserTitle(type.name(), items.size()))
                .rows(config.getItemBrowserRows())
                .pageSize(config.getItemBrowserPageSize())
                .disableAllInteractions()
                .create();

        // Border filler
        gui.getFiller().fillBorder(GuiHelper.createFiller(config.getItemBrowserBorder()));

        // Action instructions lore lines to append to previews
        List<Component> actionLoreComponents = TextUtility.formatGuiLore(config.getItemBrowserActionLore());

        // Add items to page
        for (ItemDefinition def : items) {
            ItemStack preview = manager.getItemBuilder().build(def, 1);
            ItemMeta meta = preview.getItemMeta();
            if (meta != null) {
                List<Component> lore = meta.hasLore() ? new ArrayList<>(meta.lore()) : new ArrayList<>();
                lore.addAll(actionLoreComponents);
                meta.lore(lore);
                preview.setItemMeta(meta);
            }

            GuiItem guiItem = new GuiItem(preview, event -> {
                boolean shift = event.isShiftClick();
                boolean right = event.isRightClick();

                if (shift && right) {
                    // Delete item
                    manager.getConfigManager().deleteItem(type, def.getId());
                    player.sendMessage(TextUtility.formatMessage("&c[RoyItems] Deleted item &f" + def.getId() + "&c!"));
                    open(); // Refresh view
                } else if (right) {
                    // Give to player
                    player.getInventory().addItem(manager.getItemBuilder().build(def, 1));
                    player.sendMessage(TextUtility.formatMessage("&a[RoyItems] Added 1x &f" + def.getId() + " &ato your inventory!"));
                } else {
                    // Open detail editor
                    if (!manager.getSessionManager().acquireLock(player, type, def.getId())) {
                        player.sendMessage(TextUtility.formatMessage("&c[RoyItems] This item is currently being edited by another administrator!"));
                        return;
                    }
                    manager.openDetailEditor(player, def);
                }
            });

            gui.addItem(guiItem);
        }

        // Back button
        EditorGuiConfig.ButtonConfig backBtn = config.getItemBrowserButton("back",
                new EditorGuiConfig.ButtonConfig(45, Material.ARROW, "<#FFD166><bold>◀ Back to Categories</bold></#FFD166>", List.of(), 0));
        gui.setItem(backBtn.slot(), GuiHelper.createGuiItem(
                backBtn.material(),
                TextUtility.formatGui(backBtn.name()),
                TextUtility.formatGuiLore(backBtn.lore()),
                event -> manager.openMainDashboard(player)
        ));

        // Previous page button
        EditorGuiConfig.ButtonConfig prevBtn = config.getItemBrowserButton("prev-page",
                new EditorGuiConfig.ButtonConfig(48, Material.SPECTRAL_ARROW, "<#FFD166>Previous Page</#FFD166>", List.of(), 0));
        gui.setItem(prevBtn.slot(), GuiHelper.createGuiItem(
                prevBtn.material(),
                TextUtility.formatGui(prevBtn.name()),
                TextUtility.formatGuiLore(prevBtn.lore()),
                event -> gui.previous()
        ));

        // Create new item button
        EditorGuiConfig.ButtonConfig createBtn = config.getItemBrowserButton("create-item",
                new EditorGuiConfig.ButtonConfig(49, Material.NETHERITE_UPGRADE_SMITHING_TEMPLATE,
                        "<#06D6A0><bold>+ Create New Item</bold></#06D6A0>", List.of(), 0));
        List<String> createLoreReplaced = new ArrayList<>();
        for (String line : createBtn.lore()) {
            createLoreReplaced.add(line.replace("%type%", type.name()));
        }
        gui.setItem(createBtn.slot(), GuiHelper.createGuiItem(
                createBtn.material(),
                TextUtility.formatGui(createBtn.name()),
                TextUtility.formatGuiLore(createLoreReplaced),
                event -> {
                    String newId = "new_" + type.name().toLowerCase() + "_" + (System.currentTimeMillis() % 1000);
                    ItemDefinition newDef = new ItemDefinition(type, newId);
                    newDef.setName("&e" + type.name() + " " + newId);
                    manager.getConfigManager().saveItem(newDef);
                    manager.openDetailEditor(player, newDef);
                }
        ));

        // Next page button
        EditorGuiConfig.ButtonConfig nextBtn = config.getItemBrowserButton("next-page",
                new EditorGuiConfig.ButtonConfig(50, Material.SPECTRAL_ARROW, "<#FFD166>Next Page</#FFD166>", List.of(), 0));
        gui.setItem(nextBtn.slot(), GuiHelper.createGuiItem(
                nextBtn.material(),
                TextUtility.formatGui(nextBtn.name()),
                TextUtility.formatGuiLore(nextBtn.lore()),
                event -> gui.next()
        ));

        // Close button
        EditorGuiConfig.ButtonConfig closeBtn = config.getItemBrowserButton("close",
                new EditorGuiConfig.ButtonConfig(53, Material.BARRIER, "<#EF476F><bold>Close Menu</bold></#EF476F>", List.of(), 0));
        gui.setItem(closeBtn.slot(), GuiHelper.createGuiItem(
                closeBtn.material(),
                TextUtility.formatGui(closeBtn.name()),
                TextUtility.formatGuiLore(closeBtn.lore()),
                event -> gui.close(player)
        ));

        manager.registerGui(player, gui);
        gui.open(player);
    }
}
