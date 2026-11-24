package com.github.orenaidaucac.royItems.core.message;

import com.github.orenaidaucac.royItems.core.text.TextUtility;
import com.github.orenaidaucac.royItems.core.update.UpdateChecker;
import com.github.orenaidaucac.royItems.item.LiveUpdateReport;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.Plugin;

import java.io.File;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.StandardOpenOption;
import java.util.*;
import java.util.regex.Pattern;

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
    private final Set<String> warnedMissingKeys = Collections.synchronizedSet(new HashSet<>());

    public MessageManager(Plugin plugin) {
        this.plugin = plugin;
        loadMessages();
    }

    public void loadMessages() {
        warnedMissingKeys.clear();
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

        // 2. Perform automatic language migration on existing files on disk
        migrateLanguageFiles();

        // 3. Load en-US embedded resource as universal fallback
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

        // 4. Resolve active language from config.yml
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
     * Automatic Language Migration: Scans all .yml language files in plugins/RoyItems/lang/,
     * identifies missing keys compared to embedded JAR template, and safely appends them
     * while 100% preserving existing translations, custom colors, placeholders, and file structure.
     */
    public void migrateLanguageFiles() {
        File folder = new File(plugin.getDataFolder(), "lang");
        if (!folder.exists() || !folder.isDirectory()) return;

        File[] files = folder.listFiles((dir, name) -> name.toLowerCase(Locale.ROOT).endsWith(".yml"));
        if (files == null) return;

        for (File file : files) {
            try {
                migrateFile(file);
            } catch (Exception e) {
                if (plugin != null && plugin.getLogger() != null) {
                    plugin.getLogger().warning("[MessageManager] Failed to migrate language file " + file.getName() + ": " + e.getMessage());
                }
            }
        }
    }

    public void migrateFile(File file) {
        if (file == null || !file.exists() || !file.isFile()) return;

        String fileName = file.getName();
        String baseName = fileName.substring(0, fileName.length() - 4);
        String langCode = normalizeLanguageCode(baseName);

        // 1. Obtain reference template resource
        InputStream in = plugin.getResource("lang/" + langCode + ".yml");
        if (in == null) {
            in = plugin.getResource("lang/en-US.yml");
        }
        if (in == null) return;

        String templateText;
        try {
            templateText = new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } catch (Exception e) {
            return;
        }

        YamlConfiguration templateConfig = new YamlConfiguration();
        try {
            templateConfig.loadFromString(templateText);
        } catch (Exception e) {
            return;
        }

        ConfigurationSection tSec = templateConfig.getConfigurationSection("messages");
        if (tSec == null) return;

        // 2. Load disk file configuration
        YamlConfiguration diskConfig = YamlConfiguration.loadConfiguration(file);

        // 3. Detect missing keys under messages
        List<String> missingKeys = new ArrayList<>();
        for (String key : tSec.getKeys(false)) {
            if (!diskConfig.contains("messages." + key) && !diskConfig.contains(key)) {
                missingKeys.add(key);
            }
        }

        if (missingKeys.isEmpty()) {
            return; // 100% synchronized, nothing to migrate!
        }

        // 4. Construct migration snippet preserving comments and indentation
        StringBuilder snippet = new StringBuilder();
        snippet.append("\n  # ----------------------------------------------------------------------------\n");
        snippet.append("  # Automatically migrated keys (RoyItems v2026.1.10)\n");
        snippet.append("  # ----------------------------------------------------------------------------\n");

        for (String key : missingKeys) {
            String block = extractKeyBlockFromTemplate(templateText, key);
            if (block != null && !block.isBlank()) {
                snippet.append(block).append("\n");
            } else {
                Object val = tSec.get(key);
                if (val instanceof List<?> list) {
                    snippet.append("  ").append(key).append(":\n");
                    for (Object o : list) {
                        snippet.append("    - \"").append(o.toString().replace("\"", "\\\"")).append("\"\n");
                    }
                } else if (val != null) {
                    snippet.append("  ").append(key).append(": \"").append(val.toString().replace("\"", "\\\"")).append("\"\n");
                }
            }
        }

        // 5. Safely append or insert snippet into disk file
        try {
            List<String> diskLines = Files.readAllLines(file.toPath(), StandardCharsets.UTF_8);
            boolean hasMessagesKey = false;
            int insertLineIdx = -1;

            for (int i = 0; i < diskLines.size(); i++) {
                String line = diskLines.get(i);
                if (line.matches("^messages\\s*:.*")) {
                    hasMessagesKey = true;
                    for (int j = i + 1; j < diskLines.size(); j++) {
                        String subLine = diskLines.get(j);
                        if (!subLine.isBlank() && !subLine.startsWith(" ") && !subLine.startsWith("\t") && !subLine.startsWith("#")) {
                            insertLineIdx = j;
                            break;
                        }
                    }
                    break;
                }
            }

            if (!hasMessagesKey) {
                Files.writeString(file.toPath(),
                        "\nmessages:" + snippet.toString(),
                        StandardCharsets.UTF_8,
                        StandardOpenOption.APPEND);
            } else if (insertLineIdx != -1) {
                diskLines.add(insertLineIdx, snippet.toString());
                Files.write(file.toPath(), diskLines, StandardCharsets.UTF_8);
            } else {
                Files.writeString(file.toPath(),
                        snippet.toString(),
                        StandardCharsets.UTF_8,
                        StandardOpenOption.APPEND);
            }

            if (plugin != null && plugin.getLogger() != null) {
                plugin.getLogger().info("[MessageManager] Migrated " + missingKeys.size() + " missing message key(s) into lang/" + file.getName());
            }
        } catch (Exception e) {
            if (plugin != null && plugin.getLogger() != null) {
                plugin.getLogger().warning("[MessageManager] Failed to write migrated keys into lang/" + file.getName() + ": " + e.getMessage());
            }
        }
    }

    private String extractKeyBlockFromTemplate(String templateText, String key) {
        if (templateText == null || key == null) return null;
        String[] lines = templateText.split("\r?\n");

        Pattern keyPattern = Pattern.compile("^\\s{2}" + Pattern.quote(key) + "\\s*:.*");
        int startLine = -1;
        for (int i = 0; i < lines.length; i++) {
            if (keyPattern.matcher(lines[i]).matches()) {
                startLine = i;
                break;
            }
        }

        if (startLine == -1) return null;

        int commentStart = startLine;
        while (commentStart > 0 && lines[commentStart - 1].matches("^\\s{2}#.*")) {
            commentStart--;
        }

        StringBuilder block = new StringBuilder();
        for (int i = commentStart; i < startLine; i++) {
            block.append(lines[i]).append("\n");
        }

        block.append(lines[startLine]);

        for (int i = startLine + 1; i < lines.length; i++) {
            String next = lines[i];
            if (next.matches("^\\s{4,}.*")) {
                block.append("\n").append(next);
            } else {
                break;
            }
        }

        return block.toString();
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
        if (config != null) {
            if (config.contains("messages." + key)) {
                return config.getString("messages." + key, def);
            }
            if (config.contains(key)) {
                return config.getString(key, def);
            }
        }
        if (fallbackConfig != null) {
            String fallbackVal = null;
            if (fallbackConfig.contains("messages." + key)) {
                fallbackVal = fallbackConfig.getString("messages." + key, def);
            } else if (fallbackConfig.contains(key)) {
                fallbackVal = fallbackConfig.getString(key, def);
            }
            if (fallbackVal != null) {
                if (warnedMissingKeys.add(key)) {
                    if (plugin != null && plugin.getLogger() != null) {
                        plugin.getLogger().warning("[MessageManager] Missing key '" + key + "' in language '"
                                + activeLanguage + "', falling back to default en-US.");
                    }
                }
                return fallbackVal;
            }
        }
        return def;
    }

    public List<String> getRawList(String key, List<String> def) {
        if (config != null) {
            if (config.isList("messages." + key)) {
                return config.getStringList("messages." + key);
            }
            if (config.isList(key)) {
                return config.getStringList(key);
            }
        }
        if (fallbackConfig != null) {
            List<String> fallbackVal = null;
            if (fallbackConfig.isList("messages." + key)) {
                fallbackVal = fallbackConfig.getStringList("messages." + key);
            } else if (fallbackConfig.isList(key)) {
                fallbackVal = fallbackConfig.getStringList(key);
            }
            if (fallbackVal != null) {
                if (warnedMissingKeys.add(key)) {
                    if (plugin != null && plugin.getLogger() != null) {
                        plugin.getLogger().warning("[MessageManager] Missing list key '" + key + "' in language '"
                                + activeLanguage + "', falling back to default en-US.");
                    }
                }
                return fallbackVal;
            }
        }
        return def;
    }

    public String format(String key, String def, Map<String, String> placeholders) {
        String msg = getRawMessage(key, def);
        if (msg == null || msg.isEmpty()) return "";

        msg = msg.replace("%prefix%", prefix).replace("#prefix#", prefix);
        if (placeholders != null) {
            for (Map.Entry<String, String> entry : placeholders.entrySet()) {
                String k = entry.getKey();
                String cleanKey = k.replaceAll("^[#%]+|[#%]+$", "");
                msg = msg.replace("%" + cleanKey + "%", entry.getValue());
                msg = msg.replace("#" + cleanKey + "#", entry.getValue());
                msg = msg.replace(k, entry.getValue());
            }
        }
        return msg;
    }

    public void send(CommandSender sender, String key) {
        send(sender, key, "", Collections.emptyMap());
    }

    public void send(CommandSender sender, String key, String def) {
        send(sender, key, def, Collections.emptyMap());
    }

    public void send(CommandSender sender, String key, Map<String, String> placeholders) {
        send(sender, key, "", placeholders);
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
                    .replace("#url#", downloadUrl != null ? downloadUrl : "https://modrinth.com/plugin/royitems")
                    .replace("%current%", currentVersion != null ? currentVersion : "Unknown")
                    .replace("%latest%", latestVersion != null ? latestVersion : "Unknown")
                    .replace("%url%", downloadUrl != null ? downloadUrl : "https://modrinth.com/plugin/royitems");
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
