package com.github.orenaidaucac.royItems.extraction;

import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;

import java.util.*;

/**
 * Configuration holder for gui/remove-enchants-gui.yml.
 */
public class ExtractionGuiConfig {

    public static class NavigationItem {
        private int slot = -1;
        private Material material = Material.ARROW;
        private String name = "";
        private List<String> lore = new ArrayList<>();
        private int customModelData = 0;

        public NavigationItem(int slot, Material material, String name, List<String> lore, int customModelData) {
            this.slot = slot;
            this.material = material != null ? material : Material.ARROW;
            this.name = name != null ? name : "";
            if (lore != null) this.lore.addAll(lore);
            this.customModelData = customModelData;
        }

        public int getSlot() { return slot; }
        public Material getMaterial() { return material; }
        public String getName() { return name; }
        public List<String> getLore() { return lore; }
        public int getCustomModelData() { return customModelData; }
    }

    public static class DecorationItem {
        private Material material = Material.GRAY_STAINED_GLASS_PANE;
        private String name = " ";
        private List<String> lore = new ArrayList<>();
        private List<Integer> slots = new ArrayList<>();
        private int customModelData = 0;

        public DecorationItem(Material material, String name, List<String> lore, List<Integer> slots, int customModelData) {
            this.material = material != null ? material : Material.GRAY_STAINED_GLASS_PANE;
            this.name = name != null ? name : " ";
            if (lore != null) this.lore.addAll(lore);
            if (slots != null) this.slots.addAll(slots);
            this.customModelData = customModelData;
        }

        public Material getMaterial() { return material; }
        public String getName() { return name; }
        public List<String> getLore() { return lore; }
        public List<Integer> getSlots() { return slots; }
        public int getCustomModelData() { return customModelData; }
    }

    private String title = "<gradient:#FFD166:#06D6A0>&lEnchantment Extraction</gradient>";
    private int rows = 5;
    private int targetSlot = 4;
    private String targetPreviewName = "<gold>Target: %item_name%";
    private List<String> targetPreviewLore = List.of(
            "&7Extracting enchantments from this equipment.",
            "&7Click an enchantment below to extract it into a book."
    );

    private List<Integer> enchantSlots = new ArrayList<>(List.of(
            19, 20, 21, 22, 23, 24, 25,
            28, 29, 30, 31, 32, 33, 34
    ));

    private Material iconMaterial = Material.ENCHANTED_BOOK;
    private int iconCustomModelData = 0;
    private String iconName = "<gold>%enchantment_name% <yellow>%level_roman%</yellow></gold>";
    private List<String> iconLore = List.of(
            "&7Type: &f%type%",
            "&7Rarity: %rarity%",
            "&7Return Chance: <green>%chance%%</green>",
            "",
            "&e▸ Click to extract this enchantment!"
    );

    private NavigationItem previousPage = new NavigationItem(38, Material.ARROW, "<yellow>&l« Previous Page</yellow>", List.of("&7Click to view previous enchantments."), 0);
    private NavigationItem nextPage = new NavigationItem(42, Material.ARROW, "<yellow>&lNext Page »</yellow>", List.of("&7Click to view more enchantments."), 0);
    private NavigationItem closeButton = new NavigationItem(40, Material.BARRIER, "<red>&lCancel Extraction</red>", List.of("&7Click to cancel and exit.", "&7No items will be consumed."), 0);

    private List<DecorationItem> decorations = new ArrayList<>();

    public void load(FileConfiguration config) {
        if (config == null) return;

        this.title = config.getString("title", this.title);
        this.rows = Math.max(1, Math.min(6, config.getInt("rows", 5)));
        this.targetSlot = config.getInt("target-item-slot", 4);

        if (config.contains("target-preview")) {
            this.targetPreviewName = config.getString("target-preview.name", this.targetPreviewName);
            if (config.isList("target-preview.lore")) {
                this.targetPreviewLore = config.getStringList("target-preview.lore");
            }
        }

        if (config.isList("enchant-slots")) {
            this.enchantSlots = config.getIntegerList("enchant-slots");
        }

        if (config.contains("enchant-icon")) {
            String matName = config.getString("enchant-icon.material", "ENCHANTED_BOOK");
            Material mat = Material.matchMaterial(matName);
            this.iconMaterial = (mat != null) ? mat : Material.ENCHANTED_BOOK;
            this.iconCustomModelData = config.getInt("enchant-icon.custom-model-data", 0);
            this.iconName = config.getString("enchant-icon.name", this.iconName);
            if (config.isList("enchant-icon.lore")) {
                this.iconLore = config.getStringList("enchant-icon.lore");
            }
        }

        if (config.contains("navigation")) {
            ConfigurationSection nav = config.getConfigurationSection("navigation");
            if (nav != null) {
                if (nav.contains("previous-page")) {
                    this.previousPage = parseNavigationItem(nav.getConfigurationSection("previous-page"), Material.ARROW, "<yellow>&l« Previous Page</yellow>");
                }
                if (nav.contains("next-page")) {
                    this.nextPage = parseNavigationItem(nav.getConfigurationSection("next-page"), Material.ARROW, "<yellow>&lNext Page »</yellow>");
                }
                if (nav.contains("close")) {
                    this.closeButton = parseNavigationItem(nav.getConfigurationSection("close"), Material.BARRIER, "<red>&lCancel Extraction</red>");
                }
            }
        }

        this.decorations.clear();
        if (config.contains("decorations")) {
            ConfigurationSection decSec = config.getConfigurationSection("decorations");
            if (decSec != null) {
                for (String key : decSec.getKeys(false)) {
                    ConfigurationSection itemSec = decSec.getConfigurationSection(key);
                    if (itemSec != null) {
                        String matStr = itemSec.getString("material", "GRAY_STAINED_GLASS_PANE");
                        Material m = Material.matchMaterial(matStr);
                        if (m == null) m = Material.GRAY_STAINED_GLASS_PANE;
                        String name = itemSec.getString("name", " ");
                        List<String> lore = itemSec.getStringList("lore");
                        List<Integer> slots = itemSec.getIntegerList("slots");
                        int cmd = itemSec.getInt("custom-model-data", 0);
                        this.decorations.add(new DecorationItem(m, name, lore, slots, cmd));
                    }
                }
            }
        }
    }

    private NavigationItem parseNavigationItem(ConfigurationSection sec, Material defMat, String defName) {
        if (sec == null) return new NavigationItem(-1, defMat, defName, Collections.emptyList(), 0);
        int slot = sec.getInt("slot", -1);
        String matStr = sec.getString("material", defMat.name());
        Material m = Material.matchMaterial(matStr);
        if (m == null) m = defMat;
        String name = sec.getString("name", defName);
        List<String> lore = sec.getStringList("lore");
        int cmd = sec.getInt("custom-model-data", 0);
        return new NavigationItem(slot, m, name, lore, cmd);
    }

    public String getTitle() { return title; }
    public int getRows() { return rows; }
    public int getTargetSlot() { return targetSlot; }
    public String getTargetPreviewName() { return targetPreviewName; }
    public List<String> getTargetPreviewLore() { return targetPreviewLore; }
    public List<Integer> getEnchantSlots() { return enchantSlots; }
    public Material getIconMaterial() { return iconMaterial; }
    public int getIconCustomModelData() { return iconCustomModelData; }
    public String getIconName() { return iconName; }
    public List<String> getIconLore() { return iconLore; }
    public NavigationItem getPreviousPage() { return previousPage; }
    public NavigationItem getNextPage() { return nextPage; }
    public NavigationItem getCloseButton() { return closeButton; }
    public List<DecorationItem> getDecorations() { return decorations; }
}
