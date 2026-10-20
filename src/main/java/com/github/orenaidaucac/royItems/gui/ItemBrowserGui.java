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
import java.util.List;

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
        List<ItemDefinition> items = manager.getRegistry().getByType(type);

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
                    player.sendMessage(TextUtility.formatMessage("&c[RoyItems] Đã xóa vật phẩm &f" + def.getId() + "&c!"));
                    open(); // Refresh view
                } else if (right) {
                    // Give to player
                    player.getInventory().addItem(manager.getItemBuilder().build(def, 1));
                    player.sendMessage(TextUtility.formatMessage("&a[RoyItems] Đã lấy 1x &f" + def.getId() + " &avào túi đồ!"));
                } else {
                    // Open detail editor
                    if (!manager.getSessionManager().acquireLock(player, type, def.getId())) {
                        player.sendMessage(TextUtility.formatMessage("&c[RoyItems] Vật phẩm này hiện đang được quản trị viên khác chỉnh sửa!"));
                        return;
                    }
                    manager.openDetailEditor(player, def);
                }
            });

            gui.addItem(guiItem);
        }

        // Back button
        EditorGuiConfig.ButtonConfig backBtn = config.getItemBrowserButton("back",
                new EditorGuiConfig.ButtonConfig(45, Material.ARROW, "<yellow><bold>◀ Quay Lại Danh Mục</bold></yellow>", List.of(), 0));
        gui.setItem(backBtn.slot(), GuiHelper.createGuiItem(
                backBtn.material(),
                TextUtility.formatGui(backBtn.name()),
                TextUtility.formatGuiLore(backBtn.lore()),
                event -> manager.openMainDashboard(player)
        ));

        // Previous page button
        EditorGuiConfig.ButtonConfig prevBtn = config.getItemBrowserButton("prev-page",
                new EditorGuiConfig.ButtonConfig(48, Material.SPECTRAL_ARROW, "<yellow>Trang Trước</yellow>", List.of(), 0));
        gui.setItem(prevBtn.slot(), GuiHelper.createGuiItem(
                prevBtn.material(),
                TextUtility.formatGui(prevBtn.name()),
                TextUtility.formatGuiLore(prevBtn.lore()),
                event -> gui.previous()
        ));

        // Create new item button
        EditorGuiConfig.ButtonConfig createBtn = config.getItemBrowserButton("create-item",
                new EditorGuiConfig.ButtonConfig(49, Material.NETHERITE_UPGRADE_SMITHING_TEMPLATE,
                        "<green><bold>+ Tạo Vật Phẩm Mới</bold></green>", List.of(), 0));
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
                new EditorGuiConfig.ButtonConfig(50, Material.SPECTRAL_ARROW, "<yellow>Trang Tiếp Theo</yellow>", List.of(), 0));
        gui.setItem(nextBtn.slot(), GuiHelper.createGuiItem(
                nextBtn.material(),
                TextUtility.formatGui(nextBtn.name()),
                TextUtility.formatGuiLore(nextBtn.lore()),
                event -> gui.next()
        ));

        // Close button
        EditorGuiConfig.ButtonConfig closeBtn = config.getItemBrowserButton("close",
                new EditorGuiConfig.ButtonConfig(53, Material.BARRIER, "<red><bold>Đóng Menu</bold></red>", List.of(), 0));
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
