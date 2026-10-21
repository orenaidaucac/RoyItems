package com.github.orenaidaucac.royItems.core.update;

import com.github.orenaidaucac.royItems.RoyItems;
import com.github.orenaidaucac.royItems.core.platform.PlatformScheduler;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.bukkit.configuration.file.FileConfiguration;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.function.Consumer;

/**
 * Asynchronous update checker for RoyItems.
 * Safely queries GitHub Releases API without blocking the main server thread or Folia region threads.
 */
public class UpdateChecker {

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
    private String repository = "orenaidaucac/RoyItems";

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
        this.downloadUrl = "https://github.com/" + repository + "/releases/latest";
        loadConfig();
    }

    public void loadConfig() {
        FileConfiguration config = plugin.getConfig();
        this.enabled = config.getBoolean("update-checker.enabled", true);
        this.notifyAdminsOnJoin = config.getBoolean("update-checker.notify-admins-on-join", true);
        this.repository = config.getString("update-checker.repository", "orenaidaucac/RoyItems");
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
        return downloadUrl != null ? downloadUrl : "https://github.com/" + repository + "/releases/latest";
    }

    /**
     * Executes the asynchronous update check.
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
                // Query release list to accurately compute how many versions ahead
                URL apiUrl = URI.create("https://api.github.com/repos/" + repository + "/releases?per_page=30").toURL();
                HttpURLConnection connection = (HttpURLConnection) apiUrl.openConnection();
                connection.setRequestMethod("GET");
                connection.setRequestProperty("User-Agent", "RoyItems-UpdateChecker");
                connection.setRequestProperty("Accept", "application/vnd.github.v3+json");
                connection.setConnectTimeout(4000);
                connection.setReadTimeout(4000);

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
                    String newestTag = null;
                    String newestUrl = null;

                    if (parsed.isJsonArray()) {
                        JsonArray releases = parsed.getAsJsonArray();
                        for (JsonElement el : releases) {
                            if (!el.isJsonObject()) continue;
                            JsonObject rel = el.getAsJsonObject();
                            if (rel.has("tag_name")) {
                                String tag = rel.get("tag_name").getAsString().trim();
                                if (isNewerVersion(currentVersion, tag)) {
                                    behindCount++;
                                    if (newestTag == null) {
                                        newestTag = tag;
                                        newestUrl = rel.has("html_url")
                                                ? rel.get("html_url").getAsString()
                                                : "https://github.com/" + repository + "/releases/tag/" + tag;
                                    }
                                }
                            }
                        }
                    } else if (parsed.isJsonObject()) {
                        JsonObject obj = parsed.getAsJsonObject();
                        if (obj.has("tag_name")) {
                            String tag = obj.get("tag_name").getAsString().trim();
                            if (isNewerVersion(currentVersion, tag)) {
                                behindCount = 1;
                                newestTag = tag;
                                newestUrl = obj.has("html_url") ? obj.get("html_url").getAsString() : null;
                            }
                        }
                    }

                    this.versionsBehind = behindCount;
                    this.updateAvailable = behindCount > 0;
                    if (newestTag != null) {
                        this.latestVersion = newestTag;
                        this.downloadUrl = newestUrl != null ? newestUrl : "https://github.com/" + repository + "/releases/latest";
                    } else {
                        this.latestVersion = currentVersion;
                    }

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
                        plugin.getLogger().info("[Update] A newer version of RoyItems is available: " + this.latestVersion + " (" + behindCount + " release(s) ahead, Current: v" + currentVersion + ")");
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
