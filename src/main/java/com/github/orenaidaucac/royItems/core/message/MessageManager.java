package com.github.orenaidaucac.royItems.core.message;

import com.github.orenaidaucac.royItems.core.text.TextUtility;
import com.github.orenaidaucac.royItems.core.update.UpdateChecker;
import com.github.orenaidaucac.royItems.item.LiveUpdateReport;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.Plugin;

import java.io.File;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.*;

/**
 * Centralized internationalization (i18n) and message manager.
 * Loads localized messages from lang/<language>.yml (en-US, vi-VN, de-DE, ja-JP, ko-KR, ru-RU, es-ES)
 * with automatic fallback to en-US.
 */
public class MessageManager {

    public static final List<String> SUPPORTED_LANGUAGES = List.of(
            "en-US", "vi-VN", "de-DE", "ja-JP", "ko-KR", "ru-RU", "es-ES"
    );

    private final Plugin plugin;
    private FileConfiguration config;
    private FileConfiguration fallbackConfig;
    private String activeLanguage = "en-US";
    private String prefix = "&6RoyItems &8» &f";

    public MessageManager(Plugin plugin) {
        this.plugin = plugin;
        loadMessages();
    }

    public void loadMessages() {
        File folder = new File(plugin.getDataFolder(), "lang");
        if (!folder.exists()) {
            folder.mkdirs();
        }

        // 1. Ensure all default language files exist in the plugins/RoyItems/lang directory
        for (String langCode : SUPPORTED_LANGUAGES) {
            File langFile = new File(folder, langCode + ".yml");
            if (!langFile.exists()) {
                try {
                    plugin.saveResource("lang/" + langCode + ".yml", false);
                } catch (Exception ignored) {}
            }
        }

        // 2. Load en-US embedded resource as universal fallback
        try {
            InputStream in = plugin.getResource("lang/en-US.yml");
            if (in != null) {
                this.fallbackConfig = YamlConfiguration.loadConfiguration(new InputStreamReader(in, StandardCharsets.UTF_8));
            } else {
                File enFile = new File(folder, "en-US.yml");
                if (enFile.exists()) {
                    this.fallbackConfig = YamlConfiguration.loadConfiguration(enFile);
                }
            }
        } catch (Exception ignored) {}

        // 3. Resolve active language from config.yml
        String requestedLang = plugin.getConfig().getString("language", "en-US");
        this.activeLanguage = normalizeLanguageCode(requestedLang);

        File activeFile = new File(folder, activeLanguage + ".yml");
        if (!activeFile.exists()) {
            // Check if user specified a file directly, e.g. "lang/custom.yml"
            File customFile = new File(folder, requestedLang + ".yml");
            if (customFile.exists()) {
                activeFile = customFile;
            } else {
                activeFile = new File(folder, "en-US.yml");
                this.activeLanguage = "en-US";
            }
        }

        this.config = YamlConfiguration.loadConfiguration(activeFile);
        this.prefix = config.getString("prefix", fallbackConfig != null ? fallbackConfig.getString("prefix", "&6RoyItems &8» &f") : "&6RoyItems &8» &f");
        plugin.getLogger().info("[MessageManager] Loaded localization language: " + activeLanguage + " (" + activeFile.getName() + ")");
    }

    /**
     * Normalizes user-specified language tags into canonical filenames.
     */
    public static String normalizeLanguageCode(String input) {
        if (input == null || input.isBlank()) return "en-US";
        String clean = input.trim().replace("_", "-").toLowerCase(Locale.ROOT);

        if (clean.startsWith("vi") || clean.contains("vietnam")) return "vi-VN";
        if (clean.startsWith("de") || clean.contains("deutsch") || clean.contains("german")) return "de-DE";
        if (clean.startsWith("ja") || clean.contains("japan") || clean.contains("nihon")) return "ja-JP";
        if (clean.startsWith("ko") || clean.contains("korea") || clean.contains("hangul")) return "ko-KR";
        if (clean.startsWith("ru") || clean.contains("russia")) return "ru-RU";
        if (clean.startsWith("es") || clean.contains("spanish") || clean.contains("espanol")) return "es-ES";
        if (clean.startsWith("en") || clean.contains("english")) return "en-US";

        for (String sup : SUPPORTED_LANGUAGES) {
            if (sup.equalsIgnoreCase(clean)) return sup;
        }
        return "en-US";
    }

    public String getActiveLanguage() {
        return activeLanguage;
    }

    public String getPrefix() {
        return prefix;
    }

    public String getRawMessage(String key, String def) {
        if (config != null && config.contains("messages." + key)) {
            return config.getString("messages." + key, def);
        }
        if (fallbackConfig != null && fallbackConfig.contains("messages." + key)) {
            return fallbackConfig.getString("messages." + key, def);
        }
        return def;
    }

    public List<String> getRawList(String key, List<String> def) {
        if (config != null && config.isList("messages." + key)) {
            return config.getStringList("messages." + key);
        }
        if (fallbackConfig != null && fallbackConfig.isList("messages." + key)) {
            return fallbackConfig.getStringList("messages." + key);
        }
        return def;
    }

    public String format(String key, String def, Map<String, String> placeholders) {
        String msg = getRawMessage(key, def);
        if (msg == null || msg.isEmpty()) return "";

        msg = msg.replace("%prefix%", prefix).replace("#prefix#", prefix);
        if (placeholders != null) {
            for (Map.Entry<String, String> entry : placeholders.entrySet()) {
                msg = msg.replace("%" + entry.getKey() + "%", entry.getValue());
                msg = msg.replace("#" + entry.getKey() + "#", entry.getValue());
            }
        }
        return msg;
    }

    public void send(CommandSender sender, String key, String def, Map<String, String> placeholders) {
        if (sender == null) return;
        String formatted = format(key, def, placeholders);
        if (!formatted.isEmpty()) {
            sender.sendMessage(TextUtility.formatMessage(formatted));
        }
    }

    /**
     * Sends the professional LiveUpdate reload report to the specified sender.
     */
    public void sendReloadReport(CommandSender sender, LiveUpdateReport report) {
        if (sender == null || report == null) return;

        Map<String, String> ph = new HashMap<>();
        ph.put("inventory", String.valueOf(report.getInventoryCount()));
        ph.put("enderchest", String.valueOf(report.getEnderChestCount()));
        ph.put("total", String.valueOf(report.getTotalCount()));
        ph.put("failed", String.valueOf(report.getFailedCount()));
        ph.put("players", String.valueOf(report.getOnlinePlayersCount()));
        ph.put("count", String.valueOf(report.getTotalCount()));

        // 1. Success message
        send(sender, "reload-success", "&aAll configurations, items, and lore templates reloaded successfully!", ph);

        // 2. Report header
        send(sender, "reload-report-header", "&e&m-----------------&r &6RoyItems LiveUpdate &e&m-----------------", ph);

        // 3. Inventory line
        Map<String, String> invPh = new HashMap<>(ph);
        invPh.put("count", String.valueOf(report.getInventoryCount()));
        send(sender, "reload-report-inventory", " &7• &fPlayer Inventories: &a#count# &7items updated", invPh);

        // 4. EnderChest line
        Map<String, String> ecPh = new HashMap<>(ph);
        ecPh.put("count", String.valueOf(report.getEnderChestCount()));
        send(sender, "reload-report-enderchest", " &7• &fEnder Chests: &a#count# &7items updated", ecPh);

        // 5. Total line
        send(sender, "reload-report-total", " &7• &fTotal Synchronized: &e#count# &7items across &b#players# &7online players", ph);

        // 6. Failed line (if any failures)
        if (report.getFailedCount() > 0) {
            Map<String, String> failPh = new HashMap<>(ph);
            failPh.put("count", String.valueOf(report.getFailedCount()));
            send(sender, "reload-report-failed", " &7• &fFailed: &c#count#", failPh);
        }

        // 7. Report footer
        send(sender, "reload-report-footer", "&e&m----------------------------------------------------", ph);
    }

    /**
     * Delivers the on-join / reload update notification without aggressive bolding.
     */
    public void sendUpdateNotification(CommandSender sender, String currentVersion, String latestVersion, String downloadUrl) {
        if (sender == null) return;
        List<String> lines = getRawList("update-available", List.of(
                "<gradient:#FFD166:#06D6A0>RoyItems</gradient> <dark_gray>»</dark_gray> <green>A new version is available!</green>",
                " <gray>• Current version: <red>v#current#</red></gray>",
                " <gray>• Latest version: <green>v#latest#</green></gray>",
                " <gray>• Download: <click:open_url:'#url#'><hover:show_text:'<gray>Click to open release page'><underlined><aqua>#url#</aqua></underlined></hover></click></gray>",
                " <gray>• <italic>Please consider updating to ensure maximum stability and enjoy the latest features!</italic></gray>"
        ));

        for (String line : lines) {
            String processed = line
                    .replace("#current#", currentVersion != null ? currentVersion : "Unknown")
                    .replace("#latest#", latestVersion != null ? latestVersion : "Unknown")
                    .replace("#url#", downloadUrl != null ? downloadUrl : "https://github.com/orenaidaucac/RoyItems/releases/latest")
                    .replace("%current%", currentVersion != null ? currentVersion : "Unknown")
                    .replace("%latest%", latestVersion != null ? latestVersion : "Unknown")
                    .replace("%url%", downloadUrl != null ? downloadUrl : "https://github.com/orenaidaucac/RoyItems/releases/latest");
            sender.sendMessage(TextUtility.formatMessage(processed));
        }
    }

    /**
     * Sends the results of /ri checkupdate in a friendly, professional manner without overusing bold styling.
     */
    public void sendCheckUpdateResult(CommandSender sender, UpdateChecker.UpdateCheckResult result) {
        if (sender == null || result == null) return;

        if (!result.success()) {
            send(sender, "checkupdate-error",
                    "<gradient:#FFD166:#06D6A0>RoyItems</gradient> <dark_gray>»</dark_gray> <red>Could not check for updates. Please verify network connection or try again later.</red>",
                    Map.of("error", result.errorMessage() != null ? result.errorMessage() : "Unknown error"));
            return;
        }

        if (!result.updateAvailable()) {
            List<String> lines = getRawList("checkupdate-up-to-date", List.of(
                    "<gradient:#FFD166:#06D6A0>RoyItems</gradient> <dark_gray>»</dark_gray> <green>You are running the latest version of RoyItems.</green>",
                    " <gray>• Current version: <aqua>v#current#</aqua></gray>",
                    " <gray>• Status: <green>Up to date</green></gray>",
                    " <gray>• Everything is running smoothly with no pending updates.</gray>"
            ));
            for (String line : lines) {
                String processed = line
                        .replace("#current#", result.currentVersion())
                        .replace("%current%", result.currentVersion());
                sender.sendMessage(TextUtility.formatMessage(processed));
            }
        } else {
            List<String> lines = getRawList("checkupdate-update-found", List.of(
                    "<gradient:#FFD166:#06D6A0>RoyItems</gradient> <dark_gray>»</dark_gray> <yellow>A new version of RoyItems is available!</yellow>",
                    " <gray>• Current version: <red>v#current#</red></gray>",
                    " <gray>• Latest version: <green>v#latest#</green></gray>",
                    " <gray>• Releases ahead: <gold>#behind#</gold> new version(s)</gray>",
                    " <gray>• Download: <click:open_url:'#url#'><hover:show_text:'<gray>Click to open release page'><underlined><aqua>#url#</aqua></underlined></hover></click></gray>",
                    " <gray>• Please consider updating soon to enjoy the latest features and optimizations.</gray>"
            ));
            for (String line : lines) {
                String processed = line
                        .replace("#current#", result.currentVersion())
                        .replace("#latest#", result.latestVersion())
                        .replace("#behind#", String.valueOf(result.versionsBehind()))
                        .replace("#url#", result.downloadUrl())
                        .replace("%current%", result.currentVersion())
                        .replace("%latest%", result.latestVersion())
                        .replace("%behind%", String.valueOf(result.versionsBehind()))
                        .replace("%url%", result.downloadUrl());
                sender.sendMessage(TextUtility.formatMessage(processed));
            }
        }
    }
}
