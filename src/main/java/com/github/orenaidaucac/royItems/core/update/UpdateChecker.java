package com.github.orenaidaucac.royItems.core.update;

import com.github.orenaidaucac.royItems.RoyItems;
import com.github.orenaidaucac.royItems.core.platform.PlatformScheduler;
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

    private final RoyItems plugin;
    private final PlatformScheduler scheduler;

    private boolean enabled = true;
    private boolean notifyAdminsOnJoin = true;
    private String repository = "orenaidaucac/RoyItems";

    private String currentVersion;
    private String latestVersion = null;
    private String downloadUrl = null;
    private volatile boolean updateAvailable = false;
    private volatile boolean checkCompleted = false;

    public UpdateChecker(RoyItems plugin, PlatformScheduler scheduler) {
        this.plugin = plugin;
        this.scheduler = scheduler;
        this.currentVersion = plugin.getPluginMeta().getVersion();
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

    public String getDownloadUrl() {
        return downloadUrl != null ? downloadUrl : "https://github.com/" + repository + "/releases/latest";
    }

    /**
     * Executes the asynchronous update check.
     */
    public void checkForUpdates(Consumer<Boolean> callback) {
        if (!enabled) {
            if (callback != null) callback.accept(false);
            return;
        }

        scheduler.runAsync(() -> {
            boolean result = false;
            try {
                URL apiUrl = URI.create("https://api.github.com/repos/" + repository + "/releases/latest").toURL();
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

                    JsonObject json = JsonParser.parseString(response.toString()).getAsJsonObject();
                    if (json.has("tag_name")) {
                        String remoteTag = json.get("tag_name").getAsString().trim();
                        String htmlUrl = json.has("html_url")
                                ? json.get("html_url").getAsString()
                                : "https://github.com/" + repository + "/releases/latest";

                        this.latestVersion = remoteTag;
                        this.downloadUrl = htmlUrl;

                        if (isNewerVersion(currentVersion, remoteTag)) {
                            this.updateAvailable = true;
                            result = true;
                            plugin.getLogger().info("[Update] A newer version of RoyItems is available: " + remoteTag + " (Current: v" + currentVersion + ")");
                            plugin.getLogger().info("[Update] Download at: " + htmlUrl);
                        } else {
                            this.updateAvailable = false;
                        }
                    }
                }
                connection.disconnect();
            } catch (Exception ignored) {
                // Silently ignore network / API rate limit errors so server operations are unaffected
            } finally {
                this.checkCompleted = true;
                if (callback != null) {
                    final boolean finalResult = result;
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
