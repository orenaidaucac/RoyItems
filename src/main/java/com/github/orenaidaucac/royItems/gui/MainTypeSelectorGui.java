package com.github.orenaidaucac.royItems.gui;

import com.github.orenaidaucac.royItems.core.text.TextUtility;
import com.github.orenaidaucac.royItems.item.ItemType;
import dev.triumphteam.gui.guis.Gui;
import dev.triumphteam.gui.guis.GuiItem;
import net.kyori.adventure.text.Component;
import org.bukkit.Material;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;

/**
 * Main Editor Dashboard: Select from the 14 MMORPG Item Types.
 * Fully configurable via gui/editor-gui.yml, 100% anti-italic by default.
 */
public class MainTypeSelectorGui {

    private final EditorGUIManager manager;
    private final Player player;
    private final EditorGuiConfig config;

    public MainTypeSelectorGui(EditorGUIManager manager, Player player) {
        this.manager = manager;
        this.player = player;
        this.config = manager.getGuiConfig();
    }

    public void open() {
        Gui gui = Gui.gui()
                .title(config.getTypeSelectorTitle())
                .rows(config.getTypeSelectorRows())
                .disableAllInteractions()
                .create();

        // Border filler using configured border material
        gui.getFiller().fillBorder(GuiHelper.createFiller(config.getTypeSelectorBorder()));

        // Add 14 item types to slots in row 2 and row 3
        ItemType[] types = ItemType.values();
        int[] slots = new int[]{
                10, 11, 12, 13, 14, 15, 16,
                19, 20, 21, 22, 23, 24, 25
        };

        String nameTemplate = config.getTypeItemNameTemplate();
        List<String> loreTemplate = config.getTypeItemLoreTemplate();

        for (int i = 0; i < types.length && i < slots.length; i++) {
            ItemType type = types[i];
            int count = manager.getRegistry().getByType(type).size();
            Material icon = resolveTypeIcon(type);

            String formattedName = nameTemplate.replace("%type%", type.name());
            List<String> formattedLore = new ArrayList<>();
            for (String line : loreTemplate) {
                formattedLore.add(line
                        .replace("%type%", type.name())
                        .replace("%description%", type.getDescription())
                        .replace("%count%", String.valueOf(count))
                );
            }

            GuiItem typeItem = GuiHelper.createGuiItem(
                    icon,
                    TextUtility.formatGui(formattedName),
                    TextUtility.formatGuiLore(formattedLore),
                    event -> manager.openBrowser(player, type)
            );
            gui.setItem(slots[i], typeItem);
        }

        // Close button
        EditorGuiConfig.ButtonConfig closeBtn = config.getTypeSelectorCloseButton();
        GuiItem closeItem = GuiHelper.createGuiItem(
                closeBtn.material(),
                TextUtility.formatGui(closeBtn.name()),
                TextUtility.formatGuiLore(closeBtn.lore()),
                event -> gui.close(player)
        );
        gui.setItem(closeBtn.slot(), closeItem);

        manager.registerGui(player, gui);
        gui.open(player);
    }

    private Material resolveTypeIcon(ItemType type) {
        return switch (type) {
            case SWORD -> Material.DIAMOND_SWORD;
            case ARMOR -> Material.NETHERITE_CHESTPLATE;
            case BOW -> Material.BOW;
            case CROSSBOW -> Material.CROSSBOW;
            case STAFF -> Material.BLAZE_ROD;
            case TOOL -> Material.NETHERITE_PICKAXE;
            case GEM_STONE -> Material.EMERALD;
            case CONSUMABLE -> Material.GOLDEN_APPLE;
            case MATERIAL -> Material.DIAMOND;
            case OFF_HAND -> Material.SHIELD;
            case WING -> Material.ELYTRA;
            case VOUCHER -> Material.PAPER;
            case PET -> Material.BONE;
            case TALISMAN -> Material.TOTEM_OF_UNDYING;
        };
    }
}
