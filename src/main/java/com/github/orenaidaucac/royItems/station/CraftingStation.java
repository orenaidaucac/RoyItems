package com.github.orenaidaucac.royItems.station;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;

import java.util.*;

/**
 * Data model for a Crafting Station loaded from crafting-station/<station-id>.yml.
 */
public class CraftingStation {

    private final String id;
    private String title = "<gold>Crafting Station</gold>";
    private int size = 54;
    private String permission;
    private final List<String> commands = new ArrayList<>();
    private final List<String> fancyNpcs = new ArrayList<>();
    private final List<Integer> citizenNpcs = new ArrayList<>();

    private final Map<String, StationBackground> backgrounds = new LinkedHashMap<>();
    private final Map<String, StationRecipe> recipes = new LinkedHashMap<>();

    private StationDynamicLore dynamicLore = new StationDynamicLore();

    // Navigation & Buttons
    private StationBackground prevPageBtn;
    private StationBackground nextPageBtn;
    private StationBackground closeBtn;

    // Sounds
    private String soundSuccess = "entity.player.levelup";
    private String soundFail = "entity.villager.no";
    private String soundCountdown = "block.note_block.hat";

    public CraftingStation(String id) {
        this.id = id;
    }

    public static CraftingStation fromConfig(String id, FileConfiguration config) {
        CraftingStation station = new CraftingStation(id);
        if (config == null) return station;

        station.title = config.getString("title", "<gold>Crafting Station</gold>");
        int rawSize = config.getInt("size", 54);
        if (rawSize <= 6) rawSize = rawSize * 9; // allow configuring 1-6 rows or 9-54 slots
        if (rawSize < 9) rawSize = 9;
        if (rawSize > 54) rawSize = 54;
        station.size = (rawSize / 9) * 9;

        station.permission = config.getString("permission");
        if (station.permission != null && station.permission.isBlank()) {
            station.permission = null;
        }

        station.commands.addAll(config.getStringList("commands"));

        // FancyNpcs
        if (config.isList("fancynpcs")) {
            for (Object obj : config.getList("fancynpcs")) {
                if (obj != null) station.fancyNpcs.add(obj.toString().trim());
            }
        }

        // Citizens
        if (config.isList("citizen")) {
            for (Object obj : config.getList("citizen")) {
                if (obj instanceof Number num) {
                    station.citizenNpcs.add(num.intValue());
                } else if (obj != null) {
                    try {
                        station.citizenNpcs.add(Integer.parseInt(obj.toString().trim()));
                    } catch (NumberFormatException ignored) {}
                }
            }
        }

        // Backgrounds
        ConfigurationSection bgSec = config.getConfigurationSection("backgrounds");
        if (bgSec != null) {
            for (String bgId : bgSec.getKeys(false)) {
                ConfigurationSection itemSec = bgSec.getConfigurationSection(bgId);
                if (itemSec != null) {
                    station.backgrounds.put(bgId, StationBackground.fromSection(bgId, itemSec));
                }
            }
        }

        // Recipes
        ConfigurationSection recipeSec = config.getConfigurationSection("recipes");
        if (recipeSec != null) {
            for (String recId : recipeSec.getKeys(false)) {
                ConfigurationSection itemSec = recipeSec.getConfigurationSection(recId);
                if (itemSec != null) {
                    station.recipes.put(recId, StationRecipe.fromSection(recId, itemSec));
                }
            }
        }

        // Dynamic Lore
        ConfigurationSection dynLoreSec = config.getConfigurationSection("dynamic-lore");
        if (dynLoreSec != null) {
            station.dynamicLore = StationDynamicLore.fromSection(dynLoreSec);
        }

        // Navigation
        ConfigurationSection navSec = config.getConfigurationSection("navigation");
        if (navSec != null) {
            ConfigurationSection prevSec = navSec.getConfigurationSection("prev-page");
            if (prevSec != null) {
                station.prevPageBtn = StationBackground.fromSection("prev-page", prevSec);
            }
            ConfigurationSection nextSec = navSec.getConfigurationSection("next-page");
            if (nextSec != null) {
                station.nextPageBtn = StationBackground.fromSection("next-page", nextSec);
            }
            ConfigurationSection closeSec = navSec.getConfigurationSection("close");
            if (closeSec != null) {
                station.closeBtn = StationBackground.fromSection("close", closeSec);
            }
        }

        // Sounds
        station.soundSuccess = config.getString("sounds.craft-success", station.soundSuccess);
        station.soundFail = config.getString("sounds.craft-fail", station.soundFail);
        station.soundCountdown = config.getString("sounds.craft-countdown", station.soundCountdown);

        return station;
    }

    public int getMaxPages() {
        int max = 1;
        for (StationRecipe recipe : recipes.values()) {
            if (recipe.getPage() > max) {
                max = recipe.getPage();
            }
        }
        return max;
    }

    public List<StationRecipe> getRecipesForPage(int page) {
        List<StationRecipe> list = new ArrayList<>();
        for (StationRecipe r : recipes.values()) {
            if (r.getPage() == page) {
                list.add(r);
            }
        }
        return list;
    }

    public String getId() { return id; }
    public String getTitle() { return title; }
    public int getSize() { return size; }
    public int getRows() { return size / 9; }
    public String getPermission() { return permission; }
    public List<String> getCommands() { return commands; }
    public List<String> getFancyNpcs() { return fancyNpcs; }
    public List<Integer> getCitizenNpcs() { return citizenNpcs; }
    public Map<String, StationBackground> getBackgrounds() { return backgrounds; }
    public Map<String, StationRecipe> getRecipes() { return recipes; }
    public StationDynamicLore getDynamicLore() { return dynamicLore; }
    public StationBackground getPrevPageBtn() { return prevPageBtn; }
    public StationBackground getNextPageBtn() { return nextPageBtn; }
    public StationBackground getCloseBtn() { return closeBtn; }
    public String getSoundSuccess() { return soundSuccess; }
    public String getSoundFail() { return soundFail; }
    public String getSoundCountdown() { return soundCountdown; }
}
