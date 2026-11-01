package com.github.orenaidaucac.royItems.station;

import com.github.orenaidaucac.royItems.core.text.TextUtility;
import com.github.orenaidaucac.royItems.gui.GuiHelper;
import dev.triumphteam.gui.guis.Gui;
import dev.triumphteam.gui.guis.GuiItem;
import dev.triumphteam.gui.guis.PaginatedGui;
import org.bukkit.Material;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;

import java.io.File;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/**
 * Interactive GUI listing all loaded Crafting Stations for Admins (/ri station list).
 */
public class CraftingStationListGui {

    private final CraftingStationManager manager;
    private final Player player;

    public CraftingStationListGui(CraftingStationManager manager, Player player) {
        this.manager = manager;
        this.player = player;
    }

    public void open() {
        File file = new File(manager.getPlugin().getDataFolder(), "gui/crafting-station-list.yml");
        FileConfiguration config = file.exists() ? YamlConfiguration.loadConfiguration(file) : new YamlConfiguration();

        String title = config.getString("title", "<gold><bold>RoyItems</bold></gold> <dark_gray>—</dark_gray> <yellow>Crafting Stations</yellow>");
        int rows = config.getInt("rows", 6);
        int pageSize = config.getInt("page-size", 28);

        PaginatedGui gui = Gui.paginated()
                .title(TextUtility.formatGui(title))
                .rows(rows)
                .pageSize(pageSize)
                .disableAllInteractions()
                .create();

        // Border
        String borderMat = config.getString("border.material", "BLACK_STAINED_GLASS_PANE");
        gui.getFiller().fillBorder(GuiHelper.createFiller(Material.matchMaterial(borderMat) != null
                ? Material.matchMaterial(borderMat) : Material.BLACK_STAINED_GLASS_PANE));

        // Station items
        String itemMat = config.getString("station-item.material", "ANVIL");
        Material mat = Material.matchMaterial(itemMat) != null ? Material.matchMaterial(itemMat) : Material.ANVIL;
        String nameTpl = config.getString("station-item.name", "<#FFD166><bold>%title%</bold></#FFD166> <gray>(%id%)</gray>");
        List<String> loreTpl = config.getStringList("station-item.lore");

        Collection<CraftingStation> stations = manager.getStations();
        for (CraftingStation station : stations) {
            String formattedName = nameTpl
                    .replace("%title%", station.getTitle())
                    .replace("%id%", station.getId());

            List<String> formattedLore = new ArrayList<>();
            for (String l : loreTpl) {
                formattedLore.add(l
                        .replace("%title%", station.getTitle())
                        .replace("%id%", station.getId())
                        .replace("%size%", String.valueOf(station.getSize()))
                        .replace("%recipes%", String.valueOf(station.getRecipes().size()))
                        .replace("%permission%", station.getPermission() != null && !station.getPermission().isBlank() ? station.getPermission() : "None")
                        .replace("%commands%", station.getCommands().isEmpty() ? "None" : String.join(", ", station.getCommands()))
                );
            }

            GuiItem stationItem = GuiHelper.createGuiItem(
                    mat,
                    TextUtility.formatGui(formattedName),
                    TextUtility.formatGuiLore(formattedLore),
                    event -> manager.openStation(player, station.getId(), 1)
            );
            gui.addItem(stationItem);
        }

        // Previous page button
        int prevSlot = config.getInt("buttons.prev-page.slot", 48);
        String prevMat = config.getString("buttons.prev-page.material", "SPECTRAL_ARROW");
        String prevName = config.getString("buttons.prev-page.name", "<#FFD166>◀ Previous Page</#FFD166>");
        gui.setItem(prevSlot, GuiHelper.createGuiItem(
                Material.matchMaterial(prevMat) != null ? Material.matchMaterial(prevMat) : Material.SPECTRAL_ARROW,
                TextUtility.formatGui(prevName),
                List.of(),
                event -> gui.previous()
        ));

        // Next page button
        int nextSlot = config.getInt("buttons.next-page.slot", 50);
        String nextMat = config.getString("buttons.next-page.material", "SPECTRAL_ARROW");
        String nextName = config.getString("buttons.next-page.name", "<#FFD166>Next Page ▶</#FFD166>");
        gui.setItem(nextSlot, GuiHelper.createGuiItem(
                Material.matchMaterial(nextMat) != null ? Material.matchMaterial(nextMat) : Material.SPECTRAL_ARROW,
                TextUtility.formatGui(nextName),
                List.of(),
                event -> gui.next()
        ));

        // Close button
        int closeSlot = config.getInt("buttons.close.slot", 49);
        String closeMat = config.getString("buttons.close.material", "BARRIER");
        String closeName = config.getString("buttons.close.name", "<#EF476F><bold>Close Menu</bold></#EF476F>");
        List<String> closeLore = config.getStringList("buttons.close.lore");
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
