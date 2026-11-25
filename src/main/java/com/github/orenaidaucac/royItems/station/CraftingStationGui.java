package com.github.orenaidaucac.royItems.station;

import com.github.orenaidaucac.royItems.core.platform.PlatformScheduler;
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
import com.github.orenaidaucac.royItems.integration.IntegrationManager;
import com.github.orenaidaucac.royItems.item.ItemBuilder;
import com.github.orenaidaucac.royItems.item.ItemDefinition;
import com.github.orenaidaucac.royItems.item.ItemPDCManager;
import com.github.orenaidaucac.royItems.item.ItemRegistry;
import com.github.orenaidaucac.royItems.item.ItemType;

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
        if (player == null || !player.isOnline() || !manager.isAcceptingSessions()) return;
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

        // Set close handler to cancel any active crafting countdown for this player and untrack strictly this GUI
        gui.setCloseGuiAction(event -> {
            if (manager.isCrafting(player.getUniqueId())) {
                manager.cancelActiveCrafting(player.getUniqueId());
                manager.getPlugin().getMessageManager().send(player, "craft-cancelled");
            }
            manager.untrackOpenGui(player.getUniqueId(), gui);
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
                if (!manager.isTrackedSessionActive(player, gui)) return;
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
                if (!manager.isTrackedSessionActive(player, gui)) return;

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
            int invHave = ing.getInventoryAmount(player, manager.getRegistry(), manager.getPdcManager(), manager.getIntegration());
            int storageHave = ing.getStorageAmount(player, manager.getIntegration());
            int need = ing.getAmount();
            boolean enough = have >= need;

            String statusSymbol = enough ? loreConfig.getSymbolEnough() : loreConfig.getSymbolMissing();
            Component nameComp = ing.getDisplayNameComponent(loreConfig, manager.getRegistry());

            String lineTemplate = loreConfig.getIngredientLine()
                    .replace("%status%", statusSymbol)
                    .replace("%have%", String.valueOf(have))
                    .replace("%inv_have%", String.valueOf(invHave))
                    .replace("%storage_have%", String.valueOf(storageHave))
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
        if (!manager.isTrackedSessionActive(player, gui)) return;

        // Verify ingredients for at least 1 craft
        if (!hasAllIngredients(recipe)) {
            playSound(station.getSoundFail());
            manager.getPlugin().getMessageManager().send(player, "craft-failed-ingredients");
            return;
        }

        int craftingTime = recipe.getCraftingTime();

        // 1. Instant Crafting (crafting-time = 0)
        if (craftingTime <= 0) {
            int targetCrafts;
            if (isQuick) {
                targetCrafts = calculateMaxCrafts(recipe);
                if (targetCrafts <= 0) {
                    playSound(station.getSoundFail());
                    manager.getPlugin().getMessageManager().send(player, "craft-failed-inventory-full");
                    return;
                }
            } else {
                targetCrafts = 1;
                if (!simulateCanFit(recipe, 1)) {
                    playSound(station.getSoundFail());
                    manager.getPlugin().getMessageManager().send(player, "craft-failed-inventory-full");
                    return;
                }
            }

            if (!manager.isTrackedSessionActive(player, gui)) return;

            // Consume inputs for targetCrafts atomically
            if (!consumeInputs(recipe, targetCrafts)) {
                playSound(station.getSoundFail());
                manager.getPlugin().getMessageManager().send(player, "craft-failed-ingredients");
                return;
            }

            // Grant outputs for targetCrafts (zero-drop guarantee)
            grantOutputs(recipe, targetCrafts);

            playSound(station.getSoundSuccess());
            manager.getPlugin().getMessageManager().send(player, "craft-success", Map.of(
                    "#amount#", String.valueOf(targetCrafts),
                    "#recipe#", recipe.getId()
            ));
            if (manager.isTrackedSessionActive(player, gui)) {
                open(); // Refresh counts in dynamic lore
            }
            return;
        }

        // 2. Timed Crafting Countdown (crafting-time > 0)
        if (!simulateCanFit(recipe, 1)) {
            playSound(station.getSoundFail());
            manager.getPlugin().getMessageManager().send(player, "craft-failed-inventory-full");
            return;
        }
        startCraftingCountdown(recipe, gui);
    }

    private void startCraftingCountdown(StationRecipe recipe, Gui gui) {
        if (!manager.isTrackedSessionActive(player, gui)) return;

        int totalSeconds = recipe.getCraftingTime();
        final int[] remaining = {totalSeconds};

        manager.getPlugin().getMessageManager().send(player, "craft-countdown-start", Map.of(
                "#recipe#", recipe.getId(),
                "#time#", String.valueOf(totalSeconds)
        ));

        PlatformScheduler.TaskHandle task = manager.getPlugin().getPlatformScheduler().runEntityTimer(player, handle -> {
            if (!player.isOnline() || !player.isValid() || !manager.isTrackedSessionActive(player, gui)) {
                if (handle != null) handle.cancel();
                manager.cancelActiveCrafting(player.getUniqueId());
                return;
            }

            if (remaining[0] <= 0) {
                // Completed!
                if (handle != null) handle.cancel();
                manager.cancelActiveCrafting(player.getUniqueId());

                if (!manager.isTrackedSessionActive(player, gui)) {
                    return;
                }

                // Double check inventory space and ingredients
                if (!simulateCanFit(recipe, 1)) {
                    playSound(station.getSoundFail());
                    manager.getPlugin().getMessageManager().send(player, "craft-failed-inventory-full");
                    if (manager.isTrackedSessionActive(player, gui)) {
                        open();
                    }
                    return;
                }

                if (hasAllIngredients(recipe)) {
                    if (consumeInputs(recipe, 1)) {
                        grantOutputs(recipe, 1);
                        playSound(station.getSoundSuccess());
                        manager.getPlugin().getMessageManager().send(player, "craft-completed", Map.of(
                                "#recipe#", recipe.getId()
                        ));
                    }
                } else {
                    playSound(station.getSoundFail());
                    manager.getPlugin().getMessageManager().send(player, "craft-failed");
                }
                if (manager.isTrackedSessionActive(player, gui)) {
                    open(); // Refresh view
                }
                return;
            }

            // Play countdown sound each second
            playSound(station.getSoundCountdown());
            remaining[0]--;
        }, 20L, 20L);

        manager.setActiveCraftingTask(player.getUniqueId(), task);
    }

    private boolean hasAllIngredients(StationRecipe recipe) {
        return hasAllIngredients(recipe, 1);
    }

    private boolean hasAllIngredients(StationRecipe recipe, int craftCount) {
        for (RecipeIngredient ing : recipe.getInputs()) {
            if (!ing.hasEnough(player, manager.getRegistry(), manager.getPdcManager(), manager.getIntegration(), craftCount)) {
                return false;
            }
        }
        return true;
    }

    private boolean consumeInputs(StationRecipe recipe) {
        return consumeInputs(recipe, 1);
    }

    private boolean consumeInputs(StationRecipe recipe, int craftCount) {
        if (craftCount <= 0) return false;
        // Strict pre-check on all ingredients to prevent partial deductions
        for (RecipeIngredient ing : recipe.getInputs()) {
            if (!ing.hasEnough(player, manager.getRegistry(), manager.getPdcManager(), manager.getIntegration(), craftCount)) {
                return false;
            }
        }
        for (RecipeIngredient ing : recipe.getInputs()) {
            if (!ing.take(player, manager.getRegistry(), manager.getPdcManager(), manager.getIntegration(), craftCount)) {
                return false;
            }
        }
        return true;
    }

    private void grantOutputs(StationRecipe recipe) {
        grantOutputs(recipe, 1);
    }

    private void grantOutputs(StationRecipe recipe, int craftCount) {
        for (RecipeIngredient out : recipe.getOutputs()) {
            out.give(player, manager.getRegistry(), manager.getPdcManager(), manager.getItemBuilder(), manager.getIntegration(), craftCount);
        }
    }

    public boolean simulateCanFit(StationRecipe recipe, int craftCount) {
        return simulateCanFit(player, recipe, craftCount, manager.getRegistry(), manager.getPdcManager(), manager.getItemBuilder(), manager.getIntegration());
    }

    public int calculateMaxCrafts(StationRecipe recipe) {
        return calculateMaxCrafts(player, recipe, manager.getRegistry(), manager.getPdcManager(), manager.getItemBuilder(), manager.getIntegration());
    }

    private static class SimulatedSlot {
        ItemStack original;
        int amount;
        int maxStackSize;

        SimulatedSlot(ItemStack item) {
            if (item != null && item.getType() != Material.AIR) {
                int amt = 0;
                try { amt = item.getAmount(); } catch (Throwable ignored) { amt = 1; }
                if (amt > 0) {
                    this.original = item;
                    this.amount = amt;
                    int max = 64;
                    try { max = item.getMaxStackSize() > 0 ? item.getMaxStackSize() : 64; } catch (Throwable ignored) {}
                    this.maxStackSize = max;
                } else {
                    this.original = null;
                    this.amount = 0;
                    this.maxStackSize = 64;
                }
            } else {
                this.original = null;
                this.amount = 0;
                this.maxStackSize = 64;
            }
        }

        boolean isEmpty() {
            return original == null || amount <= 0;
        }
    }

    public static boolean simulateCanFit(Player player, StationRecipe recipe, int craftCount,
                                         ItemRegistry registry, ItemPDCManager pdc,
                                         ItemBuilder builder, IntegrationManager integration) {
        if (player == null || recipe == null || craftCount <= 0) return false;
        if (player.getInventory() == null) return false;

        ItemStack[] storage = player.getInventory().getStorageContents();
        if (storage == null) return false;

        SimulatedSlot[] sim = new SimulatedSlot[storage.length];
        for (int i = 0; i < storage.length; i++) {
            sim[i] = new SimulatedSlot(storage[i]);
        }

        // 1. Simulate taking inputs from inventory
        for (RecipeIngredient ing : recipe.getInputs()) {
            long totalNeedLong = (long) ing.getAmount() * craftCount;
            int totalNeed = totalNeedLong > Integer.MAX_VALUE ? Integer.MAX_VALUE : (int) totalNeedLong;

            if (ing instanceof RecipeIngredient.RoyItemIngredient royIng) {
                int invAmount = royIng.getInventoryAmount(player, registry, pdc, integration);
                int toDeduct = Math.min(invAmount, totalNeed);
                if (toDeduct > 0) {
                    for (SimulatedSlot slot : sim) {
                        if (!slot.isEmpty() && pdc != null && pdc.isRoyItem(slot.original)) {
                            String royId = pdc.getId(slot.original);
                            ItemType rType = pdc.getType(slot.original);
                            if (royIng.getId().equalsIgnoreCase(royId) && (royIng.getItemType() == null || royIng.getItemType() == rType)) {
                                if (slot.amount <= toDeduct) {
                                    toDeduct -= slot.amount;
                                    slot.amount = 0;
                                    slot.original = null;
                                } else {
                                    slot.amount -= toDeduct;
                                    toDeduct = 0;
                                    break;
                                }
                                if (toDeduct <= 0) break;
                            }
                        }
                    }
                }
            } else if (ing instanceof RecipeIngredient.VanillaIngredient vanIng) {
                int invAmount = vanIng.getInventoryAmount(player, registry, pdc, integration);
                int toDeduct = Math.min(invAmount, totalNeed);
                if (toDeduct > 0) {
                    for (SimulatedSlot slot : sim) {
                        if (!slot.isEmpty() && slot.original.getType() == vanIng.getMaterial()) {
                            if (pdc != null && pdc.isRoyItem(slot.original)) continue;
                            if (slot.amount <= toDeduct) {
                                toDeduct -= slot.amount;
                                slot.amount = 0;
                                slot.original = null;
                            } else {
                                slot.amount -= toDeduct;
                                toDeduct = 0;
                                break;
                            }
                            if (toDeduct <= 0) break;
                        }
                    }
                }
            }
        }

        // 2. Simulate adding outputs to sim
        for (RecipeIngredient out : recipe.getOutputs()) {
            if (out instanceof RecipeIngredient.RoyItemIngredient royOut) {
                ItemDefinition def = registry != null ? registry.get(royOut.getItemType(), royOut.getId()) : null;
                ItemStack sample = (def != null && builder != null) ? builder.build(def, 1) : null;
                int maxStack = 64;
                if (sample != null) {
                    try { if (sample.getMaxStackSize() > 0) maxStack = sample.getMaxStackSize(); } catch (Throwable ignored) {}
                } else if (def != null && def.getMaterial() != null) {
                    Material m = Material.matchMaterial(def.getMaterial());
                    if (m != null && m.getMaxStackSize() > 0) maxStack = m.getMaxStackSize();
                }

                long totalToAddLong = (long) royOut.getAmount() * craftCount;
                if (totalToAddLong > Integer.MAX_VALUE) return false;
                int totalToAdd = (int) totalToAddLong;

                // Phase 2a: Merge into matching partial stacks
                for (SimulatedSlot slot : sim) {
                    if (totalToAdd <= 0) break;
                    if (!slot.isEmpty() && sample != null) {
                        boolean match = false;
                        try { match = slot.original.isSimilar(sample); } catch (Throwable ignored) {}
                        if (match) {
                            int space = slot.maxStackSize - slot.amount;
                            if (space > 0) {
                                int add = Math.min(space, totalToAdd);
                                slot.amount += add;
                                totalToAdd -= add;
                            }
                        }
                    }
                }

                // Phase 2b: Fill empty slots
                for (SimulatedSlot slot : sim) {
                    if (totalToAdd <= 0) break;
                    if (slot.isEmpty()) {
                        int add = Math.min(maxStack, totalToAdd);
                        slot.original = sample;
                        slot.amount = add;
                        slot.maxStackSize = maxStack;
                        totalToAdd -= add;
                    }
                }

                if (totalToAdd > 0) {
                    return false;
                }
            } else if (out instanceof RecipeIngredient.VanillaIngredient vanOut) {
                Material mat = vanOut.getMaterial();
                ItemStack sample = null;
                try {
                    sample = new ItemStack(mat, 1);
                    if (vanOut.getDisplayName() != null || vanOut.getLore() != null) {
                        ItemMeta meta = sample.getItemMeta();
                        if (meta != null) {
                            if (vanOut.getDisplayName() != null && !vanOut.getDisplayName().isBlank()) {
                                meta.displayName(TextUtility.formatGui(vanOut.getDisplayName()));
                            }
                            if (vanOut.getLore() != null && !vanOut.getLore().isBlank()) {
                                List<String> loreLines = Arrays.asList(vanOut.getLore().split("\\\\n|\\n"));
                                meta.lore(TextUtility.formatGuiLore(loreLines));
                            }
                            sample.setItemMeta(meta);
                        }
                    }
                } catch (Throwable ignored) {}

                int maxStack = mat.getMaxStackSize() > 0 ? mat.getMaxStackSize() : 64;
                long totalToAddLong = (long) vanOut.getAmount() * craftCount;
                if (totalToAddLong > Integer.MAX_VALUE) return false;
                int totalToAdd = (int) totalToAddLong;

                // Phase 2a: Merge into matching partial stacks
                for (SimulatedSlot slot : sim) {
                    if (totalToAdd <= 0) break;
                    if (!slot.isEmpty()) {
                        boolean match = false;
                        if (sample != null) {
                            try { match = slot.original.isSimilar(sample); } catch (Throwable ignored) {}
                        } else if (slot.original.getType() == mat) {
                            match = (pdc == null || !pdc.isRoyItem(slot.original));
                        }
                        if (match) {
                            int space = slot.maxStackSize - slot.amount;
                            if (space > 0) {
                                int add = Math.min(space, totalToAdd);
                                slot.amount += add;
                                totalToAdd -= add;
                            }
                        }
                    }
                }

                // Phase 2b: Fill empty slots
                for (SimulatedSlot slot : sim) {
                    if (totalToAdd <= 0) break;
                    if (slot.isEmpty()) {
                        int add = Math.min(maxStack, totalToAdd);
                        slot.original = sample;
                        slot.amount = add;
                        slot.maxStackSize = maxStack;
                        totalToAdd -= add;
                    }
                }

                if (totalToAdd > 0) {
                    return false;
                }
            }
        }

        return true;
    }

    public static int calculateMaxCrafts(Player player, StationRecipe recipe,
                                         ItemRegistry registry, ItemPDCManager pdc,
                                         ItemBuilder builder, IntegrationManager integration) {
        if (player == null || recipe == null) return 0;

        // 1. Bound by available ingredients across inventory and storage
        int maxFromIngredients = Integer.MAX_VALUE;
        for (RecipeIngredient ing : recipe.getInputs()) {
            int need = ing.getAmount();
            if (need > 0) {
                int have = ing.getPlayerAmount(player, registry, pdc, integration);
                maxFromIngredients = Math.min(maxFromIngredients, have / need);
            }
        }
        if (maxFromIngredients <= 0) {
            return 0;
        }

        // 2. Check if recipe has physical item outputs
        boolean hasItemOutputs = false;
        for (RecipeIngredient out : recipe.getOutputs()) {
            if (out.getType() == RecipeIngredient.Type.ROYITEMS || out.getType() == RecipeIngredient.Type.VANILLA) {
                hasItemOutputs = true;
                break;
            }
        }
        if (!hasItemOutputs) {
            return maxFromIngredients;
        }

        // 3. Compute realistic upper bound based on storage slots
        int storageSlots = (player.getInventory() != null && player.getInventory().getStorageContents() != null)
                ? player.getInventory().getStorageContents().length : 36;
        int maxUpper = maxFromIngredients;
        for (RecipeIngredient out : recipe.getOutputs()) {
            if (out.getType() != RecipeIngredient.Type.ROYITEMS && out.getType() != RecipeIngredient.Type.VANILLA) {
                continue;
            }
            int outAmount = Math.max(1, out.getAmount());
            int maxStack = 64;
            if (out instanceof RecipeIngredient.RoyItemIngredient royOut) {
                ItemDefinition def = registry != null ? registry.get(royOut.getItemType(), royOut.getId()) : null;
                if (def != null && builder != null) {
                    ItemStack sample = builder.build(def, 1);
                    if (sample != null && sample.getMaxStackSize() > 0) {
                        maxStack = sample.getMaxStackSize();
                    }
                }
            } else if (out instanceof RecipeIngredient.VanillaIngredient vanOut) {
                maxStack = vanOut.getMaterial().getMaxStackSize() > 0 ? vanOut.getMaterial().getMaxStackSize() : 64;
            }
            long maxForThisOut = ((long) storageSlots * maxStack) / outAmount + 64;
            if (maxForThisOut < maxUpper) {
                maxUpper = (int) maxForThisOut;
            }
        }
        maxUpper = Math.max(1, Math.min(maxUpper, maxFromIngredients));

        // 4. Test maxUpper first
        if (simulateCanFit(player, recipe, maxUpper, registry, pdc, builder, integration)) {
            return maxUpper;
        }

        // 5. Gather transition points where input stacks in inventory are completely exhausted
        Set<Integer> criticalPoints = new TreeSet<>();
        ItemStack[] storage = player.getInventory() != null ? player.getInventory().getStorageContents() : null;
        if (storage != null) {
            for (RecipeIngredient ing : recipe.getInputs()) {
                int needPerCraft = ing.getAmount();
                if (needPerCraft <= 0) continue;
                int cumCount = 0;
                for (ItemStack item : storage) {
                    if (item == null || item.getType() == Material.AIR) continue;
                    boolean match = false;
                    if (ing instanceof RecipeIngredient.RoyItemIngredient royIng && pdc != null && pdc.isRoyItem(item)) {
                        String royId = pdc.getId(item);
                        ItemType rType = pdc.getType(item);
                        if (royIng.getId().equalsIgnoreCase(royId) && (royIng.getItemType() == null || royIng.getItemType() == rType)) {
                            match = true;
                        }
                    } else if (ing instanceof RecipeIngredient.VanillaIngredient vanIng && item.getType() == vanIng.getMaterial()) {
                        if (pdc == null || !pdc.isRoyItem(item)) {
                            match = true;
                        }
                    }
                    if (match) {
                        cumCount += item.getAmount();
                        int critCraft = cumCount / needPerCraft;
                        if (critCraft >= 1 && critCraft <= maxUpper) {
                            criticalPoints.add(critCraft);
                        }
                    }
                }
            }
        }

        int best = 0;
        List<Integer> sortedCrits = new ArrayList<>(criticalPoints);
        Collections.reverse(sortedCrits);
        for (int crit : sortedCrits) {
            if (crit > best && simulateCanFit(player, recipe, crit, registry, pdc, builder, integration)) {
                best = crit;
                break;
            }
        }

        int low = Math.max(1, best);
        int high = maxUpper;
        while (low <= high) {
            int mid = (low + high) >>> 1;
            if (simulateCanFit(player, recipe, mid, registry, pdc, builder, integration)) {
                if (mid > best) best = mid;
                low = mid + 1;
            } else {
                high = mid - 1;
            }
        }

        return best;
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
                        if (!manager.isTrackedSessionActive(player, gui)) return;
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
                        if (!manager.isTrackedSessionActive(player, gui)) return;
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
                event -> player.closeInventory()
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
