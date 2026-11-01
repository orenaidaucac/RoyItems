package com.github.orenaidaucac.royItems.station;

import com.github.orenaidaucac.royItems.core.text.TextUtility;
import com.github.orenaidaucac.royItems.gui.GuiHelper;
import dev.triumphteam.gui.guis.Gui;
import dev.triumphteam.gui.guis.GuiItem;
import me.clip.placeholderapi.PlaceholderAPI;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.scheduler.BukkitTask;

import java.util.*;

/**
 * Main Crafting Station GUI powered by TriumphGUI.
 * Supports unlimited pages, dynamic recipe rendering, multi-currency costs,
 * timed crafting countdowns, and quick crafting.
 */
public class CraftingStationGui {

    private final CraftingStationManager manager;
    private final Player player;
    private final CraftingStation station;
    private int currentPage;

    public CraftingStationGui(CraftingStationManager manager, Player player, CraftingStation station, int page) {
        this.manager = manager;
        this.player = player;
        this.station = station;
        this.currentPage = Math.max(1, page);
    }

    public void open() {
        if (station.getPermission() != null && !station.getPermission().isBlank() && !player.hasPermission(station.getPermission())) {
            manager.getPlugin().getMessageManager().send(player, "station-no-permission");
            return;
        }

        // Format Title with PAPI & Color Codes
        String titleStr = station.getTitle().replace("%player%", player.getName());
        if (manager.getIntegration().hasPlaceholderAPI()) {
            titleStr = PlaceholderAPI.setPlaceholders(player, titleStr);
        }

        Gui gui = Gui.gui()
                .title(TextUtility.formatGui(titleStr))
                .rows(station.getRows())
                .disableAllInteractions()
                .create();

        // 1. Render Backgrounds
        renderBackgrounds(gui);

        // 2. Render Recipes for Current Page
        renderRecipes(gui);

        // 3. Render Navigation Controls
        renderNavigation(gui);

        // Set close handler to cancel any active crafting countdown for this player
        gui.setCloseGuiAction(event -> {
            if (manager.isCrafting(player.getUniqueId())) {
                manager.cancelActiveCrafting(player.getUniqueId());
                manager.getPlugin().getMessageManager().send(player, "craft-cancelled");
            }
            manager.untrackOpenGui(player.getUniqueId());
        });

        manager.trackOpenGui(player, gui);
        gui.open(player);
    }

    private void renderBackgrounds(Gui gui) {
        for (StationBackground bg : station.getBackgrounds().values()) {
            ItemStack base = CustomItemProvider.resolve(bg.getMaterial());
            ItemMeta meta = base.getItemMeta();
            if (meta != null) {
                if (bg.getName() != null && !bg.getName().isBlank()) {
                    String formattedName = bg.getName().replace("%player%", player.getName());
                    if (manager.getIntegration().hasPlaceholderAPI()) {
                        formattedName = PlaceholderAPI.setPlaceholders(player, formattedName);
                    }
                    meta.displayName(TextUtility.formatGui(formattedName));
                }
                if (!bg.getLore().isEmpty()) {
                    List<String> lore = new ArrayList<>();
                    for (String l : bg.getLore()) {
                        String replaced = l.replace("%player%", player.getName());
                        if (manager.getIntegration().hasPlaceholderAPI()) {
                            replaced = PlaceholderAPI.setPlaceholders(player, replaced);
                        }
                        lore.add(replaced);
                    }
                    meta.lore(TextUtility.formatGuiLore(lore));
                }
                base.setItemMeta(meta);
            }

            GuiItem guiItem = new GuiItem(base, event -> {
                for (String cmd : bg.getClickCommands()) {
                    executeClickCommand(cmd);
                }
            });

            for (int slot : bg.getSlots()) {
                if (slot >= 0 && slot < station.getSize()) {
                    gui.setItem(slot, guiItem);
                }
            }
        }
    }

    private void renderRecipes(Gui gui) {
        List<StationRecipe> pageRecipes = station.getRecipesForPage(currentPage);
        StationDynamicLore loreConfig = station.getDynamicLore();

        for (StationRecipe recipe : pageRecipes) {
            ItemStack iconItem = recipe.getIcon().createItemStack(
                    manager.getRegistry(),
                    manager.getItemBuilder(),
                    recipe.isHideDisplayName(),
                    recipe.isHideLore(),
                    recipe.isHideAttributes()
            );

            // Append Dynamic Lore
            appendDynamicLore(iconItem, recipe, loreConfig);

            GuiItem guiItem = new GuiItem(iconItem, event -> {
                ClickType click = event.getClick();

                // Press Q to inspect detailed recipe
                if (click == ClickType.DROP || click == ClickType.CONTROL_DROP) {
                    new CraftingDetailGui(manager, player, station, recipe, currentPage).open();
                    return;
                }

                // Check active countdown
                if (manager.isCrafting(player.getUniqueId())) {
                    manager.getPlugin().getMessageManager().send(player, "craft-in-progress");
                    return;
                }

                boolean isQuick = event.isShiftClick();
                handleCrafting(recipe, isQuick, gui);
            });

            for (int slot : recipe.getSlots()) {
                if (slot >= 0 && slot < station.getSize()) {
                    gui.setItem(slot, guiItem);
                }
            }
        }
    }

    private void appendDynamicLore(ItemStack item, StationRecipe recipe, StationDynamicLore loreConfig) {
        ItemMeta meta = item.getItemMeta();
        if (meta == null) return;

        List<Component> currentLore = meta.hasLore() && !recipe.isHideLore()
                ? new ArrayList<>(meta.lore())
                : new ArrayList<>();

        // Header
        for (String h : loreConfig.getHeader()) {
            currentLore.add(TextUtility.formatGui(h));
        }

        // Ingredients
        for (RecipeIngredient ing : recipe.getInputs()) {
            int have = ing.getPlayerAmount(player, manager.getRegistry(), manager.getPdcManager(), manager.getIntegration());
            int need = ing.getAmount();
            boolean enough = have >= need;

            String statusSymbol = enough ? loreConfig.getSymbolEnough() : loreConfig.getSymbolMissing();
            Component nameComp = ing.getDisplayNameComponent(loreConfig, manager.getRegistry());

            String lineTemplate = loreConfig.getIngredientLine()
                    .replace("%status%", statusSymbol)
                    .replace("%have%", String.valueOf(have))
                    .replace("%need%", String.valueOf(need));

            Component lineComp = TextUtility.formatLineWithComponent(lineTemplate, "name", nameComp);
            currentLore.add(lineComp);
        }

        // Footer
        for (String f : loreConfig.getFooter()) {
            currentLore.add(TextUtility.formatGui(f));
        }

        meta.lore(currentLore);
        item.setItemMeta(meta);
    }

    private void handleCrafting(StationRecipe recipe, boolean isQuick, Gui gui) {
        // Verify ingredients
        if (!hasAllIngredients(recipe)) {
            playSound(station.getSoundFail());
            manager.getPlugin().getMessageManager().send(player, "craft-failed-ingredients");
            return;
        }

        int craftingTime = recipe.getCraftingTime();

        // 1. Instant Crafting (crafting-time = 0)
        if (craftingTime <= 0) {
            int maxCrafts = isQuick ? calculateMaxCrafts(recipe) : 1;
            int actualCrafts = 0;
            for (int i = 0; i < maxCrafts; i++) {
                if (!consumeInputs(recipe)) break;
                actualCrafts++;
            }

            if (actualCrafts > 0) {
                for (RecipeIngredient out : recipe.getOutputs()) {
                    out.give(player, manager.getRegistry(), manager.getPdcManager(), manager.getItemBuilder(), manager.getIntegration(), actualCrafts);
                }

                playSound(station.getSoundSuccess());
                manager.getPlugin().getMessageManager().send(player, "craft-success", Map.of(
                        "#amount#", String.valueOf(actualCrafts),
                        "#recipe#", recipe.getId()
                ));
                open(); // Refresh counts in dynamic lore
            } else {
                playSound(station.getSoundFail());
                manager.getPlugin().getMessageManager().send(player, "craft-failed-ingredients");
            }
            return;
        }

        // 2. Timed Crafting Countdown (crafting-time > 0)
        startCraftingCountdown(recipe, gui);
    }

    private void startCraftingCountdown(StationRecipe recipe, Gui gui) {
        int totalSeconds = recipe.getCraftingTime();
        final int[] remaining = {totalSeconds};

        manager.getPlugin().getMessageManager().send(player, "craft-countdown-start", Map.of(
                "#recipe#", recipe.getId(),
                "#time#", String.valueOf(totalSeconds)
        ));

        BukkitTask task = Bukkit.getScheduler().runTaskTimer(manager.getPlugin(), () -> {
            if (!player.isOnline()) {
                manager.cancelActiveCrafting(player.getUniqueId());
                return;
            }

            if (remaining[0] <= 0) {
                // Completed!
                manager.cancelActiveCrafting(player.getUniqueId());

                // Double check ingredients
                if (hasAllIngredients(recipe)) {
                    if (consumeInputs(recipe)) {
                        grantOutputs(recipe);
                        playSound(station.getSoundSuccess());
                        manager.getPlugin().getMessageManager().send(player, "craft-completed", Map.of(
                                "#recipe#", recipe.getId()
                        ));
                    }
                } else {
                    playSound(station.getSoundFail());
                    manager.getPlugin().getMessageManager().send(player, "craft-failed");
                }
                open(); // Refresh view
                return;
            }

            // Play countdown sound each second
            playSound(station.getSoundCountdown());
            remaining[0]--;
        }, 20L, 20L);

        manager.setActiveCraftingTask(player.getUniqueId(), task);
    }

    private boolean hasAllIngredients(StationRecipe recipe) {
        for (RecipeIngredient ing : recipe.getInputs()) {
            if (!ing.hasEnough(player, manager.getRegistry(), manager.getPdcManager(), manager.getIntegration())) {
                return false;
            }
        }
        return true;
    }

    private boolean consumeInputs(StationRecipe recipe) {
        if (!hasAllIngredients(recipe)) return false;
        for (RecipeIngredient ing : recipe.getInputs()) {
            if (!ing.take(player, manager.getRegistry(), manager.getPdcManager(), manager.getIntegration())) {
                return false;
            }
        }
        return true;
    }

    private void grantOutputs(StationRecipe recipe) {
        for (RecipeIngredient out : recipe.getOutputs()) {
            out.give(player, manager.getRegistry(), manager.getPdcManager(), manager.getItemBuilder(), manager.getIntegration());
        }
    }

    private int calculateMaxCrafts(StationRecipe recipe) {
        int max = 64;
        for (RecipeIngredient ing : recipe.getInputs()) {
            int have = ing.getPlayerAmount(player, manager.getRegistry(), manager.getPdcManager(), manager.getIntegration());
            int need = ing.getAmount();
            if (need > 0) {
                max = Math.min(max, have / need);
            }
        }
        return Math.max(1, max);
    }

    private void renderNavigation(Gui gui) {
        int maxPages = station.getMaxPages();

        // Previous Page
        if (currentPage > 1) {
            StationBackground prev = station.getPrevPageBtn();
            int slot = (prev != null && !prev.getSlots().isEmpty()) ? prev.getSlots().get(0) : (station.getSize() - 6);
            String matStr = prev != null ? prev.getMaterial() : "SPECTRAL_ARROW";
            String name = prev != null && prev.getName() != null
                    ? prev.getName().replace("%prev_page%", String.valueOf(currentPage - 1)).replace("%max_pages%", String.valueOf(maxPages))
                    : "<#FFD166>◀ Previous Page</#FFD166>";
            List<String> lore = prev != null ? prev.getLore() : List.of();

            gui.setItem(slot, GuiHelper.createGuiItem(
                    CustomItemProvider.resolve(matStr).getType(),
                    TextUtility.formatGui(name),
                    TextUtility.formatGuiLore(lore),
                    event -> {
                        this.currentPage--;
                        open();
                    }
            ));
        }

        // Next Page
        if (currentPage < maxPages) {
            StationBackground next = station.getNextPageBtn();
            int slot = (next != null && !next.getSlots().isEmpty()) ? next.getSlots().get(0) : (station.getSize() - 4);
            String matStr = next != null ? next.getMaterial() : "SPECTRAL_ARROW";
            String name = next != null && next.getName() != null
                    ? next.getName().replace("%next_page%", String.valueOf(currentPage + 1)).replace("%max_pages%", String.valueOf(maxPages))
                    : "<#FFD166>Next Page ▶</#FFD166>";
            List<String> lore = next != null ? next.getLore() : List.of();

            gui.setItem(slot, GuiHelper.createGuiItem(
                    CustomItemProvider.resolve(matStr).getType(),
                    TextUtility.formatGui(name),
                    TextUtility.formatGuiLore(lore),
                    event -> {
                        this.currentPage++;
                        open();
                    }
            ));
        }

        // Close Button
        StationBackground close = station.getCloseBtn();
        int closeSlot = (close != null && !close.getSlots().isEmpty()) ? close.getSlots().get(0) : (station.getSize() - 5);
        String closeMatStr = close != null ? close.getMaterial() : "BARRIER";
        String closeName = close != null && close.getName() != null ? close.getName() : "<#EF476F><bold>Close Menu</bold></#EF476F>";
        List<String> closeLore = close != null ? close.getLore() : List.of();

        gui.setItem(closeSlot, GuiHelper.createGuiItem(
                CustomItemProvider.resolve(closeMatStr).getType(),
                TextUtility.formatGui(closeName),
                TextUtility.formatGuiLore(closeLore),
                event -> gui.close(player)
        ));
    }

    private void executeClickCommand(String rawCmd) {
        if (rawCmd == null || rawCmd.isBlank()) return;
        String line = rawCmd.trim();

        if (line.toLowerCase().startsWith("[player]")) {
            String cmd = line.substring(8).trim().replace("%player%", player.getName());
            if (manager.getIntegration().hasPlaceholderAPI()) {
                cmd = PlaceholderAPI.setPlaceholders(player, cmd);
            }
            player.performCommand(cmd);
        } else if (line.toLowerCase().startsWith("[console]")) {
            String cmd = line.substring(9).trim().replace("%player%", player.getName());
            if (manager.getIntegration().hasPlaceholderAPI()) {
                cmd = PlaceholderAPI.setPlaceholders(player, cmd);
            }
            Bukkit.dispatchCommand(Bukkit.getConsoleSender(), cmd);
        } else if (line.toLowerCase().startsWith("[sound]")) {
            String soundName = line.substring(7).trim();
            playSound(soundName);
        }
    }

    private void playSound(String soundKey) {
        if (soundKey == null || soundKey.isBlank()) return;
        try {
            // Try standard bukkit Sound enum
            String enumName = soundKey.toUpperCase().replace('.', '_').replace('-', '_');
            Sound sound = Sound.valueOf(enumName);
            player.playSound(player.getLocation(), sound, 1.0f, 1.0f);
        } catch (IllegalArgumentException e) {
            try {
                // Try playing as raw sound string
                player.playSound(player.getLocation(), soundKey.toLowerCase(), 1.0f, 1.0f);
            } catch (Exception ignored) {}
        }
    }
}
