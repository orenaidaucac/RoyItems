package com.github.orenaidaucac.royItems.gui;

import com.github.orenaidaucac.royItems.core.text.TextUtility;
import net.kyori.adventure.text.Component;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.Plugin;

import java.io.File;
import java.util.*;

/**
 * Loads, manages, and hot-reloads all layout, titles, buttons, slots,
 * materials, pagination settings, and text from gui/editor-gui.yml.
 */
public class EditorGuiConfig {

    public record ButtonConfig(int slot, Material material, String name, List<String> lore, int customModelData) {}

    public record PropertyConfig(int page, int slot, Material material, String name, List<String> lore, int customModelData, boolean visible) {}

    private final Plugin plugin;
    private File file;
    private FileConfiguration config;

    private final Map<String, PropertyConfig> properties = new HashMap<>();

    public EditorGuiConfig(Plugin plugin) {
        this.plugin = plugin;
    }

    public void load() {
        if (!plugin.getDataFolder().exists()) {
            plugin.getDataFolder().mkdirs();
        }
        File guiDir = new File(plugin.getDataFolder(), "gui");
        if (!guiDir.exists()) {
            guiDir.mkdirs();
        }

        file = new File(plugin.getDataFolder(), "gui/editor-gui.yml");
        if (!file.exists()) {
            plugin.saveResource("gui/editor-gui.yml", false);
        }

        config = YamlConfiguration.loadConfiguration(file);
        loadProperties();
    }

    private void loadProperties() {
        properties.clear();
        ConfigurationSection sec = config.getConfigurationSection("detail-editor.properties");
        if (sec == null) return;

        for (String key : sec.getKeys(false)) {
            ConfigurationSection propSec = sec.getConfigurationSection(key);
            if (propSec == null) continue;

            int page = propSec.getInt("page", 1);
            int slot = propSec.getInt("slot", -1);
            String matName = propSec.getString("material", "STONE");
            Material mat = Material.matchMaterial(matName);
            if (mat == null) mat = Material.STONE;

            String name = propSec.getString("name", key);
            List<String> lore = propSec.getStringList("lore");
            int cmd = propSec.getInt("custom-model-data", 0);
            boolean visible = propSec.getBoolean("visible", true);

            properties.put(key.toLowerCase(), new PropertyConfig(page, slot, mat, name, lore, cmd, visible));
        }
    }

    // --- Type Selector Getters ---

    public Component getTypeSelectorTitle() {
        String raw = config.getString("type-selector.title", "<gold><bold>RoyItems</bold></gold> <dark_gray>—</dark_gray> <yellow>Chọn Danh Mục</yellow>");
        return TextUtility.formatGui(raw);
    }

    public int getTypeSelectorRows() {
        return Math.max(1, Math.min(6, config.getInt("type-selector.rows", 6)));
    }

    public Material getTypeSelectorBorder() {
        String matName = config.getString("type-selector.border.material", "BLACK_STAINED_GLASS_PANE");
        Material mat = Material.matchMaterial(matName);
        return mat != null ? mat : Material.BLACK_STAINED_GLASS_PANE;
    }

    public ButtonConfig getTypeSelectorCloseButton() {
        int slot = config.getInt("type-selector.close-button.slot", 49);
        Material mat = resolveMaterial(config.getString("type-selector.close-button.material"), Material.BARRIER);
        String name = config.getString("type-selector.close-button.name", "<red><bold>Đóng Menu</bold></red>");
        List<String> lore = config.getStringList("type-selector.close-button.lore");
        return new ButtonConfig(slot, mat, name, lore, 0);
    }

    public String getTypeItemNameTemplate() {
        return config.getString("type-selector.type-item.name", "<gold><bold>%type%</bold></gold>");
    }

    public List<String> getTypeItemLoreTemplate() {
        List<String> lore = config.getStringList("type-selector.type-item.lore");
        if (lore.isEmpty()) {
            return List.of(
                    "<gray>Phân loại: <white>%description%</white>",
                    "<gray>Tổng số vật phẩm: <green>%count%</green>",
                    "",
                    "<yellow>▶ Click để xem và quản lý danh sách!</yellow>"
            );
        }
        return lore;
    }

    // --- Item Browser Getters ---

    public Component getItemBrowserTitle(String type, int count) {
        String raw = config.getString("item-browser.title", "<gold><bold>RoyItems</bold></gold> <dark_gray>—</dark_gray> <yellow>%type% (%count%)</yellow>")
                .replace("%type%", type)
                .replace("%count%", String.valueOf(count));
        return TextUtility.formatGui(raw);
    }

    public int getItemBrowserRows() {
        return Math.max(1, Math.min(6, config.getInt("item-browser.rows", 6)));
    }

    public int getItemBrowserPageSize() {
        return Math.max(1, Math.min(45, config.getInt("item-browser.page-size", 28)));
    }

    public Material getItemBrowserBorder() {
        return resolveMaterial(config.getString("item-browser.border.material"), Material.BLACK_STAINED_GLASS_PANE);
    }

    public ButtonConfig getItemBrowserButton(String key, ButtonConfig fallback) {
        ConfigurationSection sec = config.getConfigurationSection("item-browser.buttons." + key);
        if (sec == null) return fallback;

        int slot = sec.getInt("slot", fallback.slot());
        Material mat = resolveMaterial(sec.getString("material"), fallback.material());
        String name = sec.getString("name", fallback.name());
        List<String> lore = sec.isList("lore") ? sec.getStringList("lore") : fallback.lore();
        int cmd = sec.getInt("custom-model-data", fallback.customModelData());
        return new ButtonConfig(slot, mat, name, lore, cmd);
    }

    public List<String> getItemBrowserActionLore() {
        List<String> lore = config.getStringList("item-browser.item-actions-lore");
        if (lore.isEmpty()) {
            return List.of(
                    "",
                    "<yellow>Chuột trái: <white>Chỉnh sửa chi tiết thuộc tính</white></yellow>",
                    "<aqua>Chuột phải: <white>Lấy 1x vật phẩm vào túi đồ</white></aqua>",
                    "<red>Shift + Chuột phải: <white>Xóa vĩnh viễn vật phẩm</white></red>"
            );
        }
        return lore;
    }

    // --- Detail Editor Getters (MMOItems-style Paginated) ---

    public Component getDetailEditorTitle(String id, int page, int maxPages) {
        String raw = config.getString("detail-editor.title", "<gold><bold>RoyItems</bold></gold> <dark_gray>—</dark_gray> <yellow>%id%</yellow> <gray>(%page%/%max_pages%)</gray>")
                .replace("%id%", id)
                .replace("%page%", String.valueOf(page))
                .replace("%max_pages%", String.valueOf(maxPages));
        return TextUtility.formatGui(raw);
    }

    public int getDetailEditorRows() {
        return Math.max(1, Math.min(6, config.getInt("detail-editor.rows", 6)));
    }

    public int getDetailEditorMaxPages() {
        return Math.max(1, config.getInt("detail-editor.max-pages", 3));
    }

    public Material getDetailEditorBorder() {
        return resolveMaterial(config.getString("detail-editor.border.material"), Material.BLACK_STAINED_GLASS_PANE);
    }

    public Material getDetailEditorFiller() {
        return resolveMaterial(config.getString("detail-editor.filler.material"), Material.GRAY_STAINED_GLASS_PANE);
    }

    public int getPreviewSlot() {
        return config.getInt("detail-editor.preview.slot", 4);
    }

    public List<String> getPreviewLoreHint() {
        List<String> hints = config.getStringList("detail-editor.preview.lore-hint");
        return hints.isEmpty() ? List.of("", "<green>▶ Click để lấy 1x bản sao vào túi đồ!</green>") : hints;
    }

    public ButtonConfig getDetailNavigationButton(String key, ButtonConfig fallback) {
        ConfigurationSection sec = config.getConfigurationSection("detail-editor.navigation." + key);
        if (sec == null) return fallback;

        int slot = sec.getInt("slot", fallback.slot());
        Material mat = resolveMaterial(sec.getString("material"), fallback.material());
        String name = sec.getString("name", fallback.name());
        List<String> lore = sec.isList("lore") ? sec.getStringList("lore") : fallback.lore();
        int cmd = sec.getInt("custom-model-data", fallback.customModelData());
        return new ButtonConfig(slot, mat, name, lore, cmd);
    }

    public PropertyConfig getPropertyConfig(String key, PropertyConfig fallback) {
        return properties.getOrDefault(key.toLowerCase(), fallback);
    }

    public List<Component> formatPropertyLore(List<String> rawLines, Map<String, String> placeholders) {
        List<String> replaced = new ArrayList<>();
        for (String line : rawLines) {
            String temp = line;
            for (Map.Entry<String, String> entry : placeholders.entrySet()) {
                temp = temp.replace(entry.getKey(), entry.getValue() != null ? entry.getValue() : "");
            }
            replaced.add(temp);
        }
        return TextUtility.formatGuiLore(replaced);
    }

    private Material resolveMaterial(String name, Material fallback) {
        if (name == null || name.isEmpty()) return fallback;
        Material mat = Material.matchMaterial(name);
        return mat != null ? mat : fallback;
    }
}
