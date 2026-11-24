package com.github.orenaidaucac.royItems.extraction;

import com.github.orenaidaucac.royItems.core.text.TextUtility;
import dev.triumphteam.gui.guis.Gui;
import dev.triumphteam.gui.guis.GuiItem;
import me.clip.placeholderapi.PlaceholderAPI;
import net.kyori.adventure.text.Component;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.*;

/**
 * Enchantment Extraction GUI built with TriumphGUI.
 * Allows players to choose and safely extract a single enchantment from their equipment.
 */
public class EnchantmentExtractionGui {

    private final ExtractionManager manager;
    private final Player player;
    private final ExtractionSession session;
    private final List<ExtractionEntry> entries;
    private final int currentPage;

    public EnchantmentExtractionGui(ExtractionManager manager, Player player,
                                    ExtractionSession session, List<ExtractionEntry> entries, int page) {
        this.manager = manager;
        this.player = player;
        this.session = session;
        this.entries = entries != null ? entries : Collections.emptyList();
        this.currentPage = Math.max(1, page);
    }

    public void open() {
        ExtractionGuiConfig config = manager.getGuiConfig();

        String rawTitle = config.getTitle();
        rawTitle = replaceGeneralPlaceholders(rawTitle, null);
        if (manager.hasPlaceholderAPI()) {
            rawTitle = PlaceholderAPI.setPlaceholders(player, rawTitle);
        }

        Gui gui = Gui.gui()
                .title(TextUtility.formatGui(rawTitle))
                .rows(config.getRows())
                .disableAllInteractions()
                .create();

        // 1. Render Background Decorations
        renderDecorations(gui, config);

        // 2. Render Target Item Preview
        renderTargetPreview(gui, config);

        // 3. Render Enchantment Icons for Current Page
        renderEnchantmentIcons(gui, config);

        // 4. Render Navigation Buttons
        renderNavigation(gui, config);

        // Set close handler
        gui.setCloseGuiAction(event -> {
            manager.untrackOpenGui(player.getUniqueId());
        });

        manager.trackOpenGui(player.getUniqueId(), gui);
        gui.open(player);
    }

    private void renderDecorations(Gui gui, ExtractionGuiConfig config) {
        for (ExtractionGuiConfig.DecorationItem dec : config.getDecorations()) {
            ItemStack item = new ItemStack(dec.getMaterial());
            ItemMeta meta = item.getItemMeta();
            if (meta != null) {
                if (dec.getName() != null && !dec.getName().isEmpty()) {
                    meta.displayName(TextUtility.formatGui(dec.getName()));
                }
                if (!dec.getLore().isEmpty()) {
                    meta.lore(TextUtility.formatGuiLore(dec.getLore()));
                }
                if (dec.getCustomModelData() > 0) {
                    meta.setCustomModelData(dec.getCustomModelData());
                }
                item.setItemMeta(meta);
            }

            GuiItem guiItem = new GuiItem(item, event -> event.setCancelled(true));
            for (int slot : dec.getSlots()) {
                if (slot >= 0 && slot < config.getRows() * 9) {
                    gui.setItem(slot, guiItem);
                }
            }
        }
    }

    private void renderTargetPreview(Gui gui, ExtractionGuiConfig config) {
        int slot = config.getTargetSlot();
        if (slot < 0 || slot >= config.getRows() * 9) return;

        ItemStack preview = session.getTargetSnapshot().clone();
        ItemMeta meta = preview.getItemMeta();
        if (meta != null) {
            String targetName = resolveTargetDisplayName();
            String nameStr = config.getTargetPreviewName().replace("%item_name%", targetName);
            nameStr = replaceGeneralPlaceholders(nameStr, null);
            if (manager.hasPlaceholderAPI()) {
                nameStr = PlaceholderAPI.setPlaceholders(player, nameStr);
            }
            meta.displayName(TextUtility.formatGui(nameStr));

            List<String> rawLore = config.getTargetPreviewLore();
            if (rawLore != null && !rawLore.isEmpty()) {
                List<String> formattedLore = new ArrayList<>();
                for (String line : rawLore) {
                    String l = line.replace("%item_name%", targetName);
                    l = replaceGeneralPlaceholders(l, null);
                    if (manager.hasPlaceholderAPI()) {
                        l = PlaceholderAPI.setPlaceholders(player, l);
                    }
                    formattedLore.add(l);
                }
                meta.lore(TextUtility.formatGuiLore(formattedLore));
            }
            preview.setItemMeta(meta);
        }

        gui.setItem(slot, new GuiItem(preview, event -> event.setCancelled(true)));
    }

    private void renderEnchantmentIcons(Gui gui, ExtractionGuiConfig config) {
        List<Integer> slots = config.getEnchantSlots();
        if (slots.isEmpty()) return;

        int pageSize = slots.size();
        int fromIndex = (currentPage - 1) * pageSize;
        int toIndex = Math.min(fromIndex + pageSize, entries.size());

        if (fromIndex >= entries.size()) return;

        List<ExtractionEntry> pageEntries = entries.subList(fromIndex, toIndex);

        for (int i = 0; i < pageEntries.size(); i++) {
            ExtractionEntry entry = pageEntries.get(i);
            int slot = slots.get(i);
            if (slot < 0 || slot >= config.getRows() * 9) continue;

            ItemStack icon = createEnchantIcon(entry, config);
            GuiItem guiItem = new GuiItem(icon, event -> {
                manager.processExtraction(player, session, entry);
            });

            gui.setItem(slot, guiItem);
        }
    }

    private ItemStack createEnchantIcon(ExtractionEntry entry, ExtractionGuiConfig config) {
        ItemStack item = new ItemStack(config.getIconMaterial());
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            String name = config.getIconName();
            name = replacePlaceholders(name, entry);
            if (manager.hasPlaceholderAPI()) {
                name = PlaceholderAPI.setPlaceholders(player, name);
            }
            meta.displayName(TextUtility.formatGui(name));

            List<String> loreList = new ArrayList<>();
            for (String line : config.getIconLore()) {
                String l = replacePlaceholders(line, entry);
                if (manager.hasPlaceholderAPI()) {
                    l = PlaceholderAPI.setPlaceholders(player, l);
                }
                loreList.add(l);
            }
            meta.lore(TextUtility.formatGuiLore(loreList));

            if (config.getIconCustomModelData() > 0) {
                meta.setCustomModelData(config.getIconCustomModelData());
            }

            item.setItemMeta(meta);
        }
        return item;
    }

    private void renderNavigation(Gui gui, ExtractionGuiConfig config) {
        int pageSize = config.getEnchantSlots().size();
        int totalPages = Math.max(1, (int) Math.ceil((double) entries.size() / pageSize));

        // 1. Previous Page
        ExtractionGuiConfig.NavigationItem prev = config.getPreviousPage();
        if (prev != null && prev.getSlot() >= 0 && prev.getSlot() < config.getRows() * 9) {
            if (currentPage > 1) {
                ItemStack item = createNavigationItemStack(prev);
                gui.setItem(prev.getSlot(), new GuiItem(item, event -> {
                    new EnchantmentExtractionGui(manager, player, session, entries, currentPage - 1).open();
                }));
            }
        }

        // 2. Next Page
        ExtractionGuiConfig.NavigationItem next = config.getNextPage();
        if (next != null && next.getSlot() >= 0 && next.getSlot() < config.getRows() * 9) {
            if (currentPage < totalPages) {
                ItemStack item = createNavigationItemStack(next);
                gui.setItem(next.getSlot(), new GuiItem(item, event -> {
                    new EnchantmentExtractionGui(manager, player, session, entries, currentPage + 1).open();
                }));
            }
        }

        // 3. Close Button
        ExtractionGuiConfig.NavigationItem close = config.getCloseButton();
        if (close != null && close.getSlot() >= 0 && close.getSlot() < config.getRows() * 9) {
            ItemStack item = createNavigationItemStack(close);
            gui.setItem(close.getSlot(), new GuiItem(item, event -> {
                gui.close(player);
                manager.getPlugin().getMessageManager().send(player, "extract-cancelled");
            }));
        }
    }

    private ItemStack createNavigationItemStack(ExtractionGuiConfig.NavigationItem nav) {
        ItemStack item = new ItemStack(nav.getMaterial());
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            String name = nav.getName();
            name = replaceGeneralPlaceholders(name, null);
            if (manager.hasPlaceholderAPI()) {
                name = PlaceholderAPI.setPlaceholders(player, name);
            }
            meta.displayName(TextUtility.formatGui(name));

            if (!nav.getLore().isEmpty()) {
                List<String> lore = new ArrayList<>();
                for (String l : nav.getLore()) {
                    String formatted = replaceGeneralPlaceholders(l, null);
                    if (manager.hasPlaceholderAPI()) {
                        formatted = PlaceholderAPI.setPlaceholders(player, formatted);
                    }
                    lore.add(formatted);
                }
                meta.lore(TextUtility.formatGuiLore(lore));
            }
            if (nav.getCustomModelData() > 0) {
                meta.setCustomModelData(nav.getCustomModelData());
            }
            item.setItemMeta(meta);
        }
        return item;
    }

    private String replacePlaceholders(String input, ExtractionEntry entry) {
        if (input == null) return "";
        String s = replaceGeneralPlaceholders(input, entry);
        if (entry != null) {
            String chanceStr = String.format(Locale.ROOT, "%.1f", entry.getChance()).replaceAll("\\.0$", "");
            s = s.replace("%enchantment%", entry.getId())
                 .replace("%enchantment_name%", entry.getName())
                 .replace("%level%", String.valueOf(entry.getLevel()))
                 .replace("%level_roman%", entry.getRomanLevel())
                 .replace("%target_level%", String.valueOf(entry.getTargetLevel()))
                 .replace("%target_level_roman%", entry.getTargetRomanLevel())
                 .replace("%book_level%", String.valueOf(entry.getBookLevel()))
                 .replace("%book_level_roman%", entry.getRomanBookLevel())
                 .replace("%rarity%", entry.getRarityDisplayName())
                 .replace("%type%", entry.getTypeName())
                 .replace("%chance%", chanceStr);
        }
        return s;
    }

    private String replaceGeneralPlaceholders(String input, ExtractionEntry entry) {
        if (input == null) return "";
        String targetName = resolveTargetDisplayName();
        String targetId = resolveTargetId();
        return input.replace("%player%", player.getName())
                    .replace("%item_name%", targetName)
                    .replace("%item%", targetId);
    }

    private String resolveTargetDisplayName() {
        ItemStack target = session.getTargetSnapshot();
        if (target == null) return "Item";
        if (target.hasItemMeta() && target.getItemMeta().hasDisplayName()) {
            return net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer.legacySection()
                    .serialize(target.getItemMeta().displayName());
        }
        return target.getType().name().replace('_', ' ');
    }

    private String resolveTargetId() {
        ItemStack target = session.getTargetSnapshot();
        if (target == null) return "";
        if (manager.getPdcManager() != null && manager.getPdcManager().isRoyItem(target)) {
            return manager.getPdcManager().getId(target);
        }
        return target.getType().name().toLowerCase(Locale.ROOT);
    }
}
