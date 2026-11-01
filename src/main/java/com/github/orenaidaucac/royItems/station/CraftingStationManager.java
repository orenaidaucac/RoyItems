package com.github.orenaidaucac.royItems.station;

import com.github.orenaidaucac.royItems.RoyItems;
import com.github.orenaidaucac.royItems.core.text.TextUtility;
import com.github.orenaidaucac.royItems.integration.IntegrationManager;
import com.github.orenaidaucac.royItems.item.ItemBuilder;
import com.github.orenaidaucac.royItems.item.ItemPDCManager;
import com.github.orenaidaucac.royItems.item.ItemRegistry;
import dev.triumphteam.gui.guis.BaseGui;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandMap;
import org.bukkit.command.CommandSender;
import org.bukkit.command.defaults.BukkitCommand;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
import org.bukkit.scheduler.BukkitTask;

import java.io.File;
import java.io.IOException;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;

/**
 * Manages all Crafting Stations, dynamic shortcut commands, NPC hooks,
 * and safety cleanup on reload/disable.
 */
public class CraftingStationManager implements Listener {

    private final RoyItems plugin;
    private final ItemRegistry registry;
    private final ItemPDCManager pdcManager;
    private final ItemBuilder itemBuilder;
    private final IntegrationManager integration;

    private final Map<String, CraftingStation> stations = new LinkedHashMap<>();
    private final Map<UUID, BaseGui> openGuis = new ConcurrentHashMap<>();
    private final Map<UUID, BukkitTask> activeCraftingTasks = new ConcurrentHashMap<>();
    private final List<String> registeredCommandAliases = new ArrayList<>();

    public CraftingStationManager(RoyItems plugin, ItemRegistry registry, ItemPDCManager pdcManager,
                                  ItemBuilder itemBuilder, IntegrationManager integration) {
        this.plugin = plugin;
        this.registry = registry;
        this.pdcManager = pdcManager;
        this.itemBuilder = itemBuilder;
        this.integration = integration;
    }

    public void initialize() {
        // Save default GUI configs if missing
        saveResourceIfNotExists("gui/crafting-station-list.yml");
        saveResourceIfNotExists("gui/crafting-station-detail.yml");

        // Save default sample station if folder empty
        File stationFolder = new File(plugin.getDataFolder(), "crafting-station");
        if (!stationFolder.exists()) {
            stationFolder.mkdirs();
        }
        File[] files = stationFolder.listFiles((dir, name) -> name.endsWith(".yml"));
        if (files == null || files.length == 0) {
            saveResourceIfNotExists("crafting-station/blacksmith.yml");
        }

        loadAll();

        // Register NPC listeners if plugins are available
        registerNpcListeners();

        // Register event listener for custom command shortcuts fallback
        Bukkit.getPluginManager().registerEvents(this, plugin);
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onPlayerCommandPreprocess(PlayerCommandPreprocessEvent event) {
        String msg = event.getMessage();
        if (msg == null || !msg.startsWith("/")) return;
        String raw = msg.substring(1).trim();
        if (raw.isEmpty()) return;
        String cmd = raw.split(" ")[0].toLowerCase();
        for (CraftingStation station : stations.values()) {
            for (String alias : station.getCommands()) {
                if (alias != null && alias.trim().equalsIgnoreCase(cmd)) {
                    event.setCancelled(true);
                    openStation(event.getPlayer(), station.getId(), 1);
                    return;
                }
            }
        }
    }

    public synchronized void loadAll() {
        closeAllOpenGuis();
        unregisterCommandAliases();
        stations.clear();

        File stationFolder = new File(plugin.getDataFolder(), "crafting-station");
        if (!stationFolder.exists()) {
            stationFolder.mkdirs();
        }

        File[] files = stationFolder.listFiles((dir, name) -> name.endsWith(".yml"));
        if (files != null) {
            for (File file : files) {
                String id = file.getName().substring(0, file.getName().length() - 4);
                try {
                    FileConfiguration cfg = YamlConfiguration.loadConfiguration(file);
                    CraftingStation station = CraftingStation.fromConfig(id, cfg);
                    stations.put(id.toLowerCase(), station);
                } catch (Exception e) {
                    plugin.getLogger().log(Level.SEVERE, "Failed to load crafting station " + file.getName() + ": " + e.getMessage(), e);
                }
            }
        }

        plugin.getLogger().info("Successfully loaded " + stations.size() + " crafting stations.");
        registerCommandAliases();
    }

    public boolean createStation(String id) {
        if (id == null || id.isBlank()) return false;
        String cleanId = id.trim().toLowerCase();
        File file = new File(plugin.getDataFolder(), "crafting-station/" + cleanId + ".yml");
        if (file.exists()) return false;

        try {
            // Write sample station config
            FileConfiguration cfg = new YamlConfiguration();
            cfg.set("title", "<gold><bold>" + id.toUpperCase() + " Station</bold></gold>");
            cfg.set("size", 54);
            cfg.set("permission", "royitems.station." + cleanId);
            cfg.set("commands", List.of(cleanId));
            cfg.set("fancynpcs", new ArrayList<>());
            cfg.set("citizen", new ArrayList<>());

            // Sample background
            cfg.set("backgrounds.border.material", "BLACK_STAINED_GLASS_PANE");
            cfg.set("backgrounds.border.name", " ");
            cfg.set("backgrounds.border.slots", List.of("0-8", "45-53", "9", "17", "18", "26", "27", "35", "36", "44"));

            // Sample recipe
            cfg.set("recipes.sample_recipe.input", List.of("vanilla[type=DIAMOND,amount=1]"));
            cfg.set("recipes.sample_recipe.output", List.of("money=1000"));
            cfg.set("recipes.sample_recipe.icon", List.of("vanilla[type=DIAMOND,amount=1]"));
            cfg.set("recipes.sample_recipe.slots", List.of(22));
            cfg.set("recipes.sample_recipe.page", 1);
            cfg.set("recipes.sample_recipe.options.crafting-time", 0);

            cfg.save(file);

            CraftingStation station = CraftingStation.fromConfig(cleanId, cfg);
            stations.put(cleanId, station);
            registerCommandsForStation(station);
            syncCommandsSafely();
            return true;
        } catch (IOException e) {
            plugin.getLogger().log(Level.SEVERE, "Failed to create station file " + cleanId + ".yml: " + e.getMessage(), e);
            return false;
        }
    }

    public boolean deleteStation(String id) {
        if (id == null) return false;
        String cleanId = id.trim().toLowerCase();
        CraftingStation station = stations.remove(cleanId);
        if (station != null) {
            for (String cmd : station.getCommands()) {
                unregisterCommand(cmd);
            }
        }

        File file = new File(plugin.getDataFolder(), "crafting-station/" + cleanId + ".yml");
        if (file.exists()) {
            boolean deleted = file.delete();
            syncCommandsSafely();
            return deleted;
        }
        return false;
    }

    public CraftingStation getStation(String id) {
        if (id == null) return null;
        return stations.get(id.trim().toLowerCase());
    }

    public Collection<CraftingStation> getStations() {
        return Collections.unmodifiableCollection(stations.values());
    }

    public void openStation(Player player, String stationId, int page) {
        if (player == null || stationId == null) return;
        CraftingStation station = getStation(stationId);
        if (station == null) {
            plugin.getMessageManager().send(player, "station-not-found", Map.of("#station#", stationId));
            return;
        }
        if (station.getPermission() != null && !station.getPermission().isBlank()) {
            if (!player.hasPermission(station.getPermission()) && !player.hasPermission("royitems.admin")) {
                plugin.getMessageManager().send(player, "station-no-permission");
                return;
            }
        }
        new CraftingStationGui(this, player, station, page).open();
    }

    // =========================================================================
    // DYNAMIC COMMAND REGISTRATION
    // =========================================================================

    private void registerCommandAliases() {
        for (CraftingStation station : stations.values()) {
            registerCommandsForStation(station);
        }
        syncCommandsSafely();
    }

    private void registerCommandsForStation(CraftingStation station) {
        CommandMap map = getCommandMap();
        if (map == null) return;

        for (String alias : station.getCommands()) {
            if (alias == null || alias.isBlank()) continue;
            String cleanAlias = alias.trim().toLowerCase();

            StationCommand cmd = new StationCommand(cleanAlias, station.getId(), this);
            map.register("royitems", cmd);
            registeredCommandAliases.add(cleanAlias);
        }
    }

    private void unregisterCommandAliases() {
        CommandMap map = getCommandMap();
        if (map == null) return;

        for (String alias : registeredCommandAliases) {
            unregisterCommand(alias);
        }
        registeredCommandAliases.clear();
    }

    @SuppressWarnings("unchecked")
    private void unregisterCommand(String cmdName) {
        CommandMap map = getCommandMap();
        if (map == null) return;
        try {
            Field knownCommandsField = null;
            Class<?> clazz = map.getClass();
            while (clazz != null && clazz != Object.class) {
                try {
                    knownCommandsField = clazz.getDeclaredField("knownCommands");
                    knownCommandsField.setAccessible(true);
                    break;
                } catch (NoSuchFieldException e) {
                    clazz = clazz.getSuperclass();
                }
            }
            if (knownCommandsField != null) {
                Map<String, Command> known = (Map<String, Command>) knownCommandsField.get(map);
                if (known != null) {
                    Command cmd = known.remove(cmdName.toLowerCase());
                    if (cmd != null) {
                        cmd.unregister(map);
                    }
                    Command cmdPrefixed = known.remove("royitems:" + cmdName.toLowerCase());
                    if (cmdPrefixed != null) {
                        cmdPrefixed.unregister(map);
                    }
                }
            }
        } catch (Throwable ignored) {}
    }

    private CommandMap getCommandMap() {
        try {
            return Bukkit.getServer().getCommandMap();
        } catch (Throwable t) {
            try {
                Field f = Bukkit.getServer().getClass().getDeclaredField("commandMap");
                f.setAccessible(true);
                return (CommandMap) f.get(Bukkit.getServer());
            } catch (Exception e) {
                return null;
            }
        }
    }

    private void syncCommandsSafely() {
        if (!plugin.isEnabled()) return;
        try {
            plugin.getPlatformScheduler().runGlobalDelayed(() -> {
                if (!plugin.isEnabled()) return;
                boolean synced = false;
                try {
                    Method syncMethod = null;
                    Class<?> clazz = Bukkit.getServer().getClass();
                    while (clazz != null && clazz != Object.class) {
                        try {
                            syncMethod = clazz.getDeclaredMethod("syncCommands");
                            syncMethod.setAccessible(true);
                            break;
                        } catch (NoSuchMethodException e) {
                            clazz = clazz.getSuperclass();
                        }
                    }
                    if (syncMethod != null) {
                        syncMethod.invoke(Bukkit.getServer());
                        synced = true;
                    }
                } catch (Throwable ignored) {}

                if (!synced) {
                    for (Player p : Bukkit.getOnlinePlayers()) {
                        try {
                            p.updateCommands();
                        } catch (Throwable ignored) {}
                    }
                }
            }, 1L);
        } catch (Throwable ignored) {}
    }

    private static class StationCommand extends BukkitCommand {
        private final String stationId;
        private final CraftingStationManager manager;

        public StationCommand(String name, String stationId, CraftingStationManager manager) {
            super(name);
            this.stationId = stationId;
            this.manager = manager;
            this.description = "Open crafting station " + stationId;
            this.usageMessage = "/" + name;
        }

        @Override
        public boolean execute(CommandSender sender, String commandLabel, String[] args) {
            if (sender instanceof Player player) {
                manager.openStation(player, stationId, 1);
            } else {
                manager.getPlugin().getMessageManager().send(sender, "player-only");
            }
            return true;
        }

        @Override
        public List<String> tabComplete(CommandSender sender, String alias, String[] args) {
            return Collections.emptyList();
        }
    }

    // =========================================================================
    // NPC INTEGRATION LISTENERS
    // =========================================================================

    private void registerNpcListeners() {
        // FancyNpcs Listener
        if (integration.hasFancyNpcs()) {
            try {
                Bukkit.getPluginManager().registerEvents(new Listener() {
                    @EventHandler(priority = EventPriority.NORMAL, ignoreCancelled = true)
                    public void onFancyNpcInteract(de.oliver.fancynpcs.api.events.NpcInteractEvent event) {
                        if (event.getNpc() == null || event.getNpc().getData() == null) return;
                        String npcId = event.getNpc().getData().getId();
                        String npcName = event.getNpc().getData().getName();

                        for (CraftingStation station : stations.values()) {
                            for (String target : station.getFancyNpcs()) {
                                if (target.equalsIgnoreCase(npcId) || target.equalsIgnoreCase(npcName)) {
                                    event.setCancelled(true);
                                    openStation(event.getPlayer(), station.getId(), 1);
                                    return;
                                }
                            }
                        }
                    }
                }, plugin);
                plugin.getLogger().info("Registered FancyNpcs interaction listener for Crafting Stations.");
            } catch (Throwable e) {
                plugin.getLogger().warning("Could not register FancyNpcs listener: " + e.getMessage());
            }
        }

        // Citizens Listener
        if (integration.hasCitizens()) {
            try {
                Bukkit.getPluginManager().registerEvents(new Listener() {
                    @EventHandler(priority = EventPriority.NORMAL, ignoreCancelled = true)
                    public void onCitizensInteract(net.citizensnpcs.api.event.NPCRightClickEvent event) {
                        if (event.getNPC() == null) return;
                        int npcId = event.getNPC().getId();

                        for (CraftingStation station : stations.values()) {
                            if (station.getCitizenNpcs().contains(npcId)) {
                                event.setCancelled(true);
                                openStation(event.getClicker(), station.getId(), 1);
                                return;
                            }
                        }
                    }
                }, plugin);
                plugin.getLogger().info("Registered Citizens NPC interaction listener for Crafting Stations.");
            } catch (Throwable e) {
                plugin.getLogger().warning("Could not register Citizens listener: " + e.getMessage());
            }
        }
    }

    // =========================================================================
    // GUI TRACKING & SAFE SHUTDOWN / RELOAD
    // =========================================================================

    public void trackOpenGui(Player player, BaseGui gui) {
        if (player != null && gui != null) {
            openGuis.put(player.getUniqueId(), gui);
        }
    }

    public void untrackOpenGui(UUID uuid) {
        if (uuid != null) {
            openGuis.remove(uuid);
        }
    }

    public void closeAllOpenGuis() {
        // Cancel all crafting countdowns
        for (BukkitTask task : activeCraftingTasks.values()) {
            if (task != null) task.cancel();
        }
        activeCraftingTasks.clear();

        // Close all open TriumphGUIs tracked by station
        for (Map.Entry<UUID, BaseGui> entry : openGuis.entrySet()) {
            Player p = Bukkit.getPlayer(entry.getKey());
            if (p != null && p.isOnline()) {
                try {
                    entry.getValue().close(p);
                } catch (Throwable ignored) {}
            }
        }
        openGuis.clear();
    }

    public boolean isCrafting(UUID uuid) {
        return activeCraftingTasks.containsKey(uuid);
    }

    public void setActiveCraftingTask(UUID uuid, BukkitTask task) {
        cancelActiveCrafting(uuid);
        activeCraftingTasks.put(uuid, task);
    }

    public void cancelActiveCrafting(UUID uuid) {
        BukkitTask task = activeCraftingTasks.remove(uuid);
        if (task != null) {
            try {
                task.cancel();
            } catch (Throwable ignored) {}
        }
    }

    private void saveResourceIfNotExists(String resourcePath) {
        try {
            File file = new File(plugin.getDataFolder(), resourcePath);
            if (!file.exists()) {
                file.getParentFile().mkdirs();
                plugin.saveResource(resourcePath, false);
            }
        } catch (Exception ignored) {}
    }

    public RoyItems getPlugin() { return plugin; }
    public ItemRegistry getRegistry() { return registry; }
    public ItemPDCManager getPdcManager() { return pdcManager; }
    public ItemBuilder getItemBuilder() { return itemBuilder; }
    public IntegrationManager getIntegration() { return integration; }
}
