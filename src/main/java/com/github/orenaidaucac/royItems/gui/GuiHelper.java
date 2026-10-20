package com.github.orenaidaucac.royItems.gui;

import com.github.orenaidaucac.royItems.core.text.TextUtility;
import dev.triumphteam.gui.components.GuiAction;
import dev.triumphteam.gui.guis.GuiItem;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Material;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.List;

/**
 * Universal GUI Item Helper that guarantees anti-italic display names and lore,
 * bypasses legacy NMS reflection (MinecraftComponentSerializer) by using Paper's
 * native Adventure API directly on ItemMeta, and provides GUI utility methods.
 */
public final class GuiHelper {

    private GuiHelper() {}

    /**
     * Ensures an Adventure Component is not italic unless it was explicitly configured to be italic.
     */
    public static Component ensureAntiItalic(Component component) {
        if (component == null) return Component.empty();
        if (component.decoration(TextDecoration.ITALIC) == TextDecoration.State.NOT_SET) {
            return component.decoration(TextDecoration.ITALIC, false);
        }
        return component;
    }

    public static ItemStack createItem(Material material, Component name, List<Component> lore) {
        return createItem(material, name, lore, false, 0);
    }

    public static ItemStack createItem(Material material, Component name, List<Component> lore, boolean glow, int customModelData) {
        ItemStack item = new ItemStack(material != null ? material : Material.STONE);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            if (name != null) {
                meta.displayName(ensureAntiItalic(name));
            }
            if (lore != null) {
                List<Component> cleanLore = new ArrayList<>(lore.size());
                for (Component line : lore) {
                    cleanLore.add(ensureAntiItalic(line));
                }
                meta.lore(cleanLore);
            }
            if (glow) {
                meta.addEnchant(Enchantment.UNBREAKING, 1, true);
                meta.addItemFlags(ItemFlag.HIDE_ENCHANTS);
            }
            if (customModelData > 0) {
                meta.setCustomModelData(customModelData);
            }
            meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES, ItemFlag.HIDE_ADDITIONAL_TOOLTIP);
            item.setItemMeta(meta);
        }
        return item;
    }

    public static GuiItem createGuiItem(Material material, Component name, List<Component> lore, GuiAction<InventoryClickEvent> action) {
        return new GuiItem(createItem(material, name, lore), action);
    }

    public static GuiItem createGuiItem(Material material, Component name, List<Component> lore, boolean glow, GuiAction<InventoryClickEvent> action) {
        return new GuiItem(createItem(material, name, lore, glow, 0), action);
    }

    public static GuiItem createGuiItem(Material material, Component name, GuiAction<InventoryClickEvent> action) {
        return createGuiItem(material, name, null, action);
    }

    public static GuiItem createFiller(Material material) {
        return new GuiItem(createItem(material, Component.empty(), null));
    }
}
