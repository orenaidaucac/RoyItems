package com.github.orenaidaucac.royItems.core.message;

import com.github.orenaidaucac.royItems.core.text.TextUtility;
import com.github.orenaidaucac.royItems.item.LiveUpdateReport;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.Plugin;

import java.io.File;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Centralized message manager loading and formatting messages from lang/message.yml.
 */
public class MessageManager {

    private final Plugin plugin;
    private FileConfiguration config;
    private String prefix = "&6&lRoyItems &8» &f";

    public MessageManager(Plugin plugin) {
        this.plugin = plugin;
        loadMessages();
    }

    public void loadMessages() {
        File folder = new File(plugin.getDataFolder(), "lang");
        if (!folder.exists()) {
            folder.mkdirs();
        }

        File file = new File(folder, "message.yml");
        if (!file.exists()) {
            try {
                plugin.saveResource("lang/message.yml", false);
            } catch (Exception ignored) {}
        }

        this.config = YamlConfiguration.loadConfiguration(file);
        this.prefix = config.getString("prefix", "&6&lRoyItems &8» &f");
    }

    public String getPrefix() {
        return prefix;
    }

    public String getRawMessage(String key, String def) {
        if (config == null) return def;
        return config.getString("messages." + key, def);
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
        send(sender, "reload-report-header", "&e&m-----------------&r &6&lRoyItems LiveUpdate &e&m-----------------", ph);

        // 3. Inventory line
        Map<String, String> invPh = new HashMap<>(ph);
        invPh.put("count", String.valueOf(report.getInventoryCount()));
        send(sender, "reload-report-inventory", " &7• &fPlayer Inventories: &a#count# &7items updated", invPh);

        // 4. EnderChest line
        Map<String, String> ecPh = new HashMap<>(ph);
        ecPh.put("count", String.valueOf(report.getEnderChestCount()));
        send(sender, "reload-report-enderchest", " &7• &fEnder Chests: &a#count# &7items updated", ecPh);

        // 5. Total line
        send(sender, "reload-report-total", " &7• &fTotal Synchronized: &e&l#count# &7items across &b#players# &7online players", ph);

        // 6. Failed line (if any failures or configured to show)
        if (report.getFailedCount() > 0) {
            Map<String, String> failPh = new HashMap<>(ph);
            failPh.put("count", String.valueOf(report.getFailedCount()));
            send(sender, "reload-report-failed", " &7• &fFailed: &c#count#", failPh);
        }

        // 7. Report footer
        send(sender, "reload-report-footer", "&e&m----------------------------------------------------", ph);
    }

    /**
     * Sends the professional, friendly update notification to an administrator.
     */
    public void sendUpdateNotification(CommandSender sender, String currentVersion, String latestVersion, String downloadUrl) {
        if (sender == null) return;
        List<String> lines = config.getStringList("messages.update-available");
        if (lines == null || lines.isEmpty()) {
            lines = List.of(
                    "<gradient:#FFD166:#06D6A0><bold>RoyItems</bold></gradient> <dark_gray>»</dark_gray> <green>A new version is available!</green>",
                    " <gray>• Current Version: <red>#current#</red>",
                    " <gray>• Latest Version: <green><bold>#latest#</bold></green>",
                    " <gray>• Download: <click:open_url:'#url#'><hover:show_text:'<gray>Click to open release page'><underlined><aqua>#url#</aqua></underlined></hover></click>",
                    " <gray>• <italic>Please consider updating to ensure maximum stability and enjoy the latest features!</italic>"
            );
        }

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
}
