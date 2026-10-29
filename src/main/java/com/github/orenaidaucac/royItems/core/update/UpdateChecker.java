package com.github.orenaidaucac.royItems.core.update;

import com.github.orenaidaucac.royItems.RoyItems;
import com.github.orenaidaucac.royItems.core.platform.PlatformScheduler;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.function.Consumer;

/**
 * Asynchronous update checker for RoyItems via Modrinth Labrinth API.
 * Safely queries Modrinth API without blocking the main server thread or Folia region threads.
 *
 * Enforces strict configuration integrity: Only 'enabled' and 'notify-admins-on-join'
 * are customizable by administrators; any unauthorized edits are immediately reverted upon reload or restart.
 */
public class UpdateChecker {

    public static final String MODRINTH_SLUG = "royitems";
    public static final String MODRINTH_PROJECT_URL = "https://modrinth.com/plugin/royitems";
    public static final String MODRINTH_API_URL = "https://api.modrinth.com/v2/project/" + MODRINTH_SLUG + "/version";

    public record UpdateCheckResult(
            boolean success,
            boolean updateAvailable,
            String currentVersion,
            String latestVersion,
            int versionsBehind,
            String downloadUrl,
            String errorMessage
    ) {}

    private final RoyItems plugin;
    private final PlatformScheduler scheduler;

    private boolean enabled = true;
    private boolean notifyAdminsOnJoin = true;

    private final String currentVersion;
    private String latestVersion;
    private String downloadUrl;
    private int versionsBehind = 0;
    private volatile boolean updateAvailable = false;
    private volatile boolean checkCompleted = false;

    public UpdateChecker(RoyItems plugin, PlatformScheduler scheduler) {
        this.plugin = plugin;
        this.scheduler = scheduler;
        this.currentVersion = plugin.getPluginMeta().getVersion();
        this.latestVersion = currentVersion;
        this.downloadUrl = MODRINTH_PROJECT_URL;
        loadConfig();
    }

    public void loadConfig() {
        if (plugin == null) return;
        FileConfiguration config = plugin.getConfig();

        // 1. Read allowed customizable parameters
        this.enabled = config.getBoolean("update-checker.enabled", true);
        this.notifyAdminsOnJoin = config.getBoolean("update-checker.notify-admins-on-join", true);

        // 2. Strict enforcement: only 'enabled' and 'notify-admins-on-join' are editable.
        // If an administrator attempts to modify any other property, it is immediately reverted.
        boolean needsRevert = enforceConfigIntegrity(config);
        if (needsRevert) {
            plugin.saveConfig();
            plugin.getLogger().warning("[UpdateChecker] Detected unauthorized modifications in 'update-checker' config. Reverted to default settings. Only 'enabled' and 'notify-admins-on-join' can be configured.");
        }
    }

    /**
     * Enforces that only 'enabled' and 'notify-admins-on-join' exist in the update-checker section.
     * All unauthorized or obsolete keys (such as repository, platform, url) are immediately removed.
     *
     * @param config The FileConfiguration to validate
     * @return true if the configuration was modified and needs to be saved to disk
     */
    public static boolean enforceConfigIntegrity(FileConfiguration config) {
        if (config == null) return false;

        boolean modified = false;

        if (!config.isConfigurationSection("update-checker")) {
            config.set("update-checker.enabled", true);
            config.set("update-checker.notify-admins-on-join", true);
            return true;
        }

        ConfigurationSection section = config.getConfigurationSection("update-checker");
        if (section == null) return false;

        // Remove any unauthorized / obsolete keys (e.g. repository, platform, url, etc.)
        for (String key : section.getKeys(false)) {
            if (!key.equalsIgnoreCase("enabled") && !key.equalsIgnoreCase("notify-admins-on-join")) {
                config.set("update-checker." + key, null);
                modified = true;
            }
        }

        // Ensure both allowed keys exist with valid booleans
        if (!config.contains("update-checker.enabled")) {
            config.set("update-checker.enabled", true);
            modified = true;
        }
        if (!config.contains("update-checker.notify-admins-on-join")) {
            config.set("update-checker.notify-admins-on-join", true);
            modified = true;
        }

        return modified;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public boolean isNotifyAdminsOnJoin() {
        return notifyAdminsOnJoin;
    }

    public boolean isUpdateAvailable() {
        return updateAvailable;
    }

    public boolean isCheckCompleted() {
        return checkCompleted;
    }

    public String getCurrentVersion() {
        return currentVersion;
    }

    public String getLatestVersion() {
        return latestVersion != null ? latestVersion : currentVersion;
    }

    public int getVersionsBehind() {
        return versionsBehind;
    }

    public String getDownloadUrl() {
        return downloadUrl != null ? downloadUrl : MODRINTH_PROJECT_URL;
    }

    /**
     * Executes the asynchronous update check against the official Modrinth API.
     */
    public void checkForUpdates(Consumer<UpdateCheckResult> callback) {
        if (!enabled) {
            UpdateCheckResult res = new UpdateCheckResult(true, false, currentVersion, currentVersion, 0, getDownloadUrl(), null);
            if (callback != null) callback.accept(res);
            return;
        }

        scheduler.runAsync(() -> {
            UpdateCheckResult result = null;
            try {
                URL apiUrl = URI.create(MODRINTH_API_URL).toURL();
                HttpURLConnection connection = (HttpURLConnection) apiUrl.openConnection();
                connection.setRequestMethod("GET");
                connection.setRequestProperty("User-Agent", "RoyMC/RoyItems/" + currentVersion + " (" + MODRINTH_PROJECT_URL + ")");
                connection.setRequestProperty("Accept", "application/json");
                connection.setConnectTimeout(5000);
                connection.setReadTimeout(5000);

                int responseCode = connection.getResponseCode();
                if (responseCode == 200) {
                    StringBuilder response = new StringBuilder();
                    try (BufferedReader reader = new BufferedReader(new InputStreamReader(connection.getInputStream(), StandardCharsets.UTF_8))) {
                        String line;
                        while ((line = reader.readLine()) != null) {
                            response.append(line);
                        }
                    }

                    JsonElement parsed = JsonParser.parseString(response.toString());
                    int behindCount = 0;
                    String newestVer = null;

                    if (parsed.isJsonArray()) {
                        JsonArray versions = parsed.getAsJsonArray();
                        for (JsonElement el : versions) {
                            if (!el.isJsonObject()) continue;
                            JsonObject ver = el.getAsJsonObject();
                            if (ver.has("version_number")) {
                                String verNum = ver.get("version_number").getAsString().trim();
                                if (isNewerVersion(currentVersion, verNum)) {
                                    behindCount++;
                                    if (newestVer == null) {
                                        newestVer = verNum;
                                    }
                                }
                            }
                        }
                    }

                    this.versionsBehind = behindCount;
                    this.updateAvailable = behindCount > 0;
                    if (newestVer != null) {
                        this.latestVersion = newestVer;
                    } else {
                        this.latestVersion = currentVersion;
                    }
                    this.downloadUrl = MODRINTH_PROJECT_URL;

                    result = new UpdateCheckResult(
                            true,
                            this.updateAvailable,
                            this.currentVersion,
                            this.latestVersion,
                            this.versionsBehind,
                            getDownloadUrl(),
                            null
                    );

                    if (this.updateAvailable) {
                        plugin.getLogger().info("[Update] A newer version of RoyItems is available on Modrinth: v" + this.latestVersion + " (" + behindCount + " release(s) ahead, Current: v" + currentVersion + ")");
                        plugin.getLogger().info("[Update] Download at: " + getDownloadUrl());
                    }
                } else {
                    result = new UpdateCheckResult(false, false, currentVersion, currentVersion, 0, getDownloadUrl(), "HTTP " + responseCode);
                }
                connection.disconnect();
            } catch (Exception e) {
                result = new UpdateCheckResult(false, false, currentVersion, currentVersion, 0, getDownloadUrl(), e.getMessage());
            } finally {
                this.checkCompleted = true;
                if (callback != null) {
                    final UpdateCheckResult finalResult = result;
                    scheduler.runGlobal(() -> callback.accept(finalResult));
                }
            }
        });
    }

    /**
     * Determines whether the remote version is strictly newer than the current version.
     */
    public static boolean isNewerVersion(String current, String remote) {
        if (current == null || remote == null) return false;

        String cClean = current.replaceAll("^[vV]", "").trim();
        String rClean = remote.replaceAll("^[vV]", "").trim();

        if (cClean.equalsIgnoreCase(rClean)) return false;

        String[] cParts = cClean.split("[.-]");
        String[] rParts = rClean.split("[.-]");

        int maxLen = Math.max(cParts.length, rParts.length);
        for (int i = 0; i < maxLen; i++) {
            int cNum = 0;
            int rNum = 0;

            if (i < cParts.length) {
                try {
                    cNum = Integer.parseInt(cParts[i]);
                } catch (NumberFormatException e) {
                    cNum = 0;
                }
            }

            if (i < rParts.length) {
                try {
                    rNum = Integer.parseInt(rParts[i]);
                } catch (NumberFormatException e) {
                    rNum = 0;
                }
            }

            if (rNum > cNum) {
                return true;
            } else if (rNum < cNum) {
                return false;
            }
        }

        return false;
    }
}
