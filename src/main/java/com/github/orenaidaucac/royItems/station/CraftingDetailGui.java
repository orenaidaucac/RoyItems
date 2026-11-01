package com.github.orenaidaucac.royItems.station;

import com.github.orenaidaucac.royItems.core.text.TextUtility;
import com.github.orenaidaucac.royItems.gui.GuiHelper;
import dev.triumphteam.gui.guis.Gui;
import dev.triumphteam.gui.guis.GuiItem;
import org.bukkit.Material;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.io.File;
import java.util.List;

/**
 * GUI showing the complete recipe details (all inputs and outputs) when pressing Q.
 */
public class CraftingDetailGui {

    private final CraftingStationManager manager;
    private final Player player;
    private final CraftingStation station;
    private final StationRecipe recipe;
    private final int returnPage;

    public CraftingDetailGui(CraftingStationManager manager, Player player,
                             CraftingStation station, StationRecipe recipe, int returnPage) {
        this.manager = manager;
        this.player = player;
        this.station = station;
        this.recipe = recipe;
        this.returnPage = returnPage;
    }

    public void open() {
        File file = new File(manager.getPlugin().getDataFolder(), "gui/crafting-station-detail.yml");
        FileConfiguration config = file.exists() ? YamlConfiguration.loadConfiguration(file) : new YamlConfiguration();

        String rawTitle = config.getString("title", "<gold><bold>Recipe Details</bold></gold> <dark_gray>—</dark_gray> <yellow>%recipe_id%</yellow>");
        String title = rawTitle.replace("%recipe_id%", recipe.getId())
                .replace("%station_id%", station.getId())
                .replace("%player%", player.getName());

        int rows = config.getInt("rows", 5);
        if (rows < 3) rows = 3;
        if (rows > 6) rows = 6;

        Gui gui = Gui.gui()
                .title(TextUtility.formatGui(title))
                .rows(rows)
                .disableAllInteractions()
                .create();

        // Border
        String borderMat = config.getString("border.material", "BLACK_STAINED_GLASS_PANE");
        gui.getFiller().fillBorder(GuiHelper.createFiller(Material.matchMaterial(borderMat) != null
                ? Material.matchMaterial(borderMat) : Material.BLACK_STAINED_GLASS_PANE));

        // Indicator
        int indicatorSlot = config.getInt("indicator.slot", 22);
        String indMat = config.getString("indicator.material", "SPECTRAL_ARROW");
        String indName = config.getString("indicator.name", "<#06D6A0><bold>➜ Craft ➜</bold></#06D6A0>");
        List<String> indLore = config.getStringList("indicator.lore");
        List<String> formattedIndLore = indLore.stream()
                .map(l -> l.replace("%time%", String.valueOf(recipe.getCraftingTime())))
                .toList();
        gui.setItem(indicatorSlot, GuiHelper.createGuiItem(
                Material.matchMaterial(indMat) != null ? Material.matchMaterial(indMat) : Material.SPECTRAL_ARROW,
                TextUtility.formatGui(indName),
                TextUtility.formatGuiLore(formattedIndLore),
                event -> {}
        ));

        // Inputs
        List<Integer> inSlots = config.getIntegerList("input-slots");
        if (inSlots.isEmpty()) inSlots = List.of(10, 11, 12, 19, 20, 21, 28, 29, 30);
        List<RecipeIngredient> inputs = recipe.getInputs();
        for (int i = 0; i < inputs.size() && i < inSlots.size(); i++) {
            RecipeIngredient ing = inputs.get(i);
            ItemStack stack = ing.toPreviewItemStack(manager.getRegistry(), manager.getItemBuilder(), station.getDynamicLore());
            gui.setItem(inSlots.get(i), new GuiItem(stack));
        }

        // Outputs
        List<Integer> outSlots = config.getIntegerList("output-slots");
        if (outSlots.isEmpty()) outSlots = List.of(14, 15, 16, 23, 24, 25, 32, 33, 34);
        List<RecipeIngredient> outputs = recipe.getOutputs();
        for (int i = 0; i < outputs.size() && i < outSlots.size(); i++) {
            RecipeIngredient ing = outputs.get(i);
            ItemStack stack = ing.toPreviewItemStack(manager.getRegistry(), manager.getItemBuilder(), station.getDynamicLore());
            gui.setItem(outSlots.get(i), new GuiItem(stack));
        }

        // Back button
        int backSlot = config.getInt("back-button.slot", 40);
        String backMat = config.getString("back-button.material", "ARROW");
        String backName = config.getString("back-button.name", "<#FFD166><bold>◀ Back to Station</bold></#FFD166>");
        List<String> backLore = config.getStringList("back-button.lore");
        gui.setItem(backSlot, GuiHelper.createGuiItem(
                Material.matchMaterial(backMat) != null ? Material.matchMaterial(backMat) : Material.ARROW,
                TextUtility.formatGui(backName),
                TextUtility.formatGuiLore(backLore),
                event -> manager.openStation(player, station.getId(), returnPage)
        ));

        // Close button
        int closeSlot = config.getInt("close-button.slot", 44);
        String closeMat = config.getString("close-button.material", "BARRIER");
        String closeName = config.getString("close-button.name", "<#EF476F><bold>Close Menu</bold></#EF476F>");
        List<String> closeLore = config.getStringList("close-button.lore");
        gui.setItem(closeSlot, GuiHelper.createGuiItem(
                Material.matchMaterial(closeMat) != null ? Material.matchMaterial(closeMat) : Material.BARRIER,
                TextUtility.formatGui(closeName),
                TextUtility.formatGuiLore(closeLore),
                event -> gui.close(player)
        ));

        manager.trackOpenGui(player, gui);
        gui.open(player);
    }
}
