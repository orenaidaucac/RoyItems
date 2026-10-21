package com.github.orenaidaucac.royItems.core.config;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.Plugin;

import java.io.File;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.List;
import java.util.logging.Level;

/**
 * Robust Configuration Migration Engine for RoyItems.
 * Automatically checks 'config-version' in disk files vs internal jar resources.
 * If outdated or missing keys exist:
 * 1. Creates an automatic backup: <filename>.v<version>.bak
 * 2. Deep merges missing keys/sections from the embedded resource while preserving all user customizations
 * 3. Preserves / copies comments where supported
 * 4. Updates 'config-version' and saves back to disk
 * 5. Logs migration details to the console
 */
public class ConfigMigrator {

    private final Plugin plugin;

    public ConfigMigrator(Plugin plugin) {
        this.plugin = plugin;
    }

    /**
     * Migrates all core configuration files:
     * - config.yml
     * - lang/message.yml
     * - gui/editor-gui.yml
     */
    public void migrateAll() {
        migrateFile("config.yml", new File(plugin.getDataFolder(), "config.yml"));
        migrateFile("lang/message.yml", new File(plugin.getDataFolder(), "lang/message.yml"));
        migrateFile("gui/editor-gui.yml", new File(plugin.getDataFolder(), "gui/editor-gui.yml"));
    }

    /**
     * Migrates a single configuration file against its embedded jar resource.
     *
     * @param resourcePath Path inside the jar resources, e.g. "lang/message.yml"
     * @param targetFile   File on the server disk
     * @return true if file was updated / migrated
     */
    public boolean migrateFile(String resourcePath, File targetFile) {
        try {
            if (targetFile.getParentFile() != null && !targetFile.getParentFile().exists()) {
                targetFile.getParentFile().mkdirs();
            }

            // If file does not exist on disk, save the default resource cleanly
            if (!targetFile.exists()) {
                plugin.saveResource(resourcePath, false);
                return false;
            }

            // Load default configuration from jar resource
            InputStream resourceStream = plugin.getResource(resourcePath);
            if (resourceStream == null) {
                return false;
            }

            YamlConfiguration defConfig = YamlConfiguration.loadConfiguration(
                    new InputStreamReader(resourceStream, StandardCharsets.UTF_8)
            );

            // Load existing configuration from disk
            YamlConfiguration diskConfig = YamlConfiguration.loadConfiguration(targetFile);

            int diskVersion = diskConfig.getInt("config-version", 0);
            int defVersion = defConfig.getInt("config-version", 1);

            boolean versionUpgrade = diskVersion < defVersion;
            boolean keysAdded = false;

            // If version upgraded, back up original configuration
            if (versionUpgrade) {
                File backupFile = new File(targetFile.getParentFile(), targetFile.getName() + ".v" + diskVersion + ".bak");
                Files.copy(targetFile.toPath(), backupFile.toPath(), StandardCopyOption.REPLACE_EXISTING);
                plugin.getLogger().info("[ConfigMigrator] Backed up '" + targetFile.getName() + "' to '" + backupFile.getName() + "' before migration.");
            }

            // Deep recursive merge of missing keys
            keysAdded = deepMerge(defConfig, diskConfig, "");

            if (versionUpgrade || keysAdded) {
                diskConfig.set("config-version", defVersion);
                diskConfig.save(targetFile);

                if (versionUpgrade) {
                    plugin.getLogger().info("[ConfigMigrator] Successfully migrated '" + targetFile.getName() + "' from v" + diskVersion + " to v" + defVersion + ".");
                } else {
                    plugin.getLogger().info("[ConfigMigrator] Synchronized missing configuration keys in '" + targetFile.getName() + "'.");
                }
                return true;
            }

            return false;
        } catch (Exception e) {
            plugin.getLogger().log(Level.SEVERE, "[ConfigMigrator] Failed to migrate '" + resourcePath + "': " + e.getMessage(), e);
            return false;
        }
    }

    /**
     * Recursively merges missing keys and sections from source into target.
     * Preserves existing user values.
     */
    public static boolean deepMerge(ConfigurationSection source, ConfigurationSection target, String currentPath) {
        boolean modified = false;

        for (String key : source.getKeys(false)) {
            if (key.equals("config-version")) continue;

            String fullPath = currentPath.isEmpty() ? key : currentPath + "." + key;

            if (source.isConfigurationSection(key)) {
                ConfigurationSection sourceChild = source.getConfigurationSection(key);
                ConfigurationSection targetChild = target.getConfigurationSection(key);

                if (targetChild == null) {
                    targetChild = target.createSection(key);
                    modified = true;
                }

                // Copy header/comments if supported
                try {
                    List<String> comments = source.getComments(key);
                    if (comments != null && !comments.isEmpty() && target.getComments(key).isEmpty()) {
                        target.setComments(key, comments);
                    }
                } catch (Throwable ignored) {}

                if (deepMerge(sourceChild, targetChild, fullPath)) {
                    modified = true;
                }
            } else {
                if (!target.contains(key)) {
                    target.set(key, source.get(key));

                    // Copy comments if supported
                    try {
                        List<String> comments = source.getComments(key);
                        if (comments != null && !comments.isEmpty()) {
                            target.setComments(key, comments);
                        }
                        List<String> inlineComments = source.getInlineComments(key);
                        if (inlineComments != null && !inlineComments.isEmpty()) {
                            target.setInlineComments(key, inlineComments);
                        }
                    } catch (Throwable ignored) {}

                    modified = true;
                }
            }
        }

        return modified;
    }
}
