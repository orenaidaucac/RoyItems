package com.github.orenaidaucac.royItems.station;

import org.bukkit.configuration.ConfigurationSection;

import java.util.ArrayList;
import java.util.List;

/**
 * Encapsulates dynamic-lore configuration and rendering rules for recipe icons.
 */
public class StationDynamicLore {

    private List<String> header = new ArrayList<>();
    private String ingredientLine = " %status% %name%: <white>%have%</white><gray>/%need%</gray>";
    private String symbolEnough = "<#06D6A0>✔</#06D6A0>";
    private String symbolMissing = "<#EF476F>✘</#EF476F>";
    private List<String> footer = new ArrayList<>();

    // Custom names for currencies (no hardcoding)
    private String nameVault = "<gold>Money (Vault)</gold>";
    private String nameExp = "<green>Experience</green>";
    private String namePlayerPoints = "<light_purple>PlayerPoints</light_purple>";
    private String nameExcellentEconomy = "<aqua>Currency (%currency%)</aqua>";

    public StationDynamicLore() {
        // Defaults
        header.add("<dark_gray>------------------------</dark_gray>");
        header.add("<yellow><bold>Required Materials:</bold></yellow>");

        footer.add("<dark_gray>------------------------</dark_gray>");
        footer.add("<yellow>• Click</yellow> <gray>to craft x1</gray>");
        footer.add("<yellow>• Shift + Click</yellow> <gray>to batch craft</gray>");
        footer.add("<yellow>• Press Q</yellow> <gray>to inspect recipe</gray>");
    }

    public static StationDynamicLore fromSection(ConfigurationSection sec) {
        StationDynamicLore lore = new StationDynamicLore();
        if (sec == null) return lore;

        ConfigurationSection formatSec = sec.getConfigurationSection("format");
        if (formatSec != null) {
            if (formatSec.contains("header")) {
                lore.header = formatSec.getStringList("header");
            }
            lore.ingredientLine = formatSec.getString("ingredient-line", lore.ingredientLine);
            lore.symbolEnough = formatSec.getString("symbol-enough", lore.symbolEnough);
            lore.symbolMissing = formatSec.getString("symbol-missing", lore.symbolMissing);
            if (formatSec.contains("footer")) {
                lore.footer = formatSec.getStringList("footer");
            }
        }

        ConfigurationSection namesSec = sec.getConfigurationSection("names");
        if (namesSec != null) {
            lore.nameVault = namesSec.getString("vault", lore.nameVault);
            lore.nameExp = namesSec.getString("exp", lore.nameExp);
            lore.namePlayerPoints = namesSec.getString("playerpoints", lore.namePlayerPoints);
            lore.nameExcellentEconomy = namesSec.getString("excellenteconomy", lore.nameExcellentEconomy);
        }

        return lore;
    }

    public List<String> getHeader() { return header; }
    public String getIngredientLine() { return ingredientLine; }
    public String getSymbolEnough() { return symbolEnough; }
    public String getSymbolMissing() { return symbolMissing; }
    public List<String> getFooter() { return footer; }
    public String getNameVault() { return nameVault; }
    public String getNameExp() { return nameExp; }
    public String getNamePlayerPoints() { return namePlayerPoints; }
    public String getNameExcellentEconomy() { return nameExcellentEconomy; }
}
