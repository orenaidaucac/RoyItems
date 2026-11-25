package com.github.orenaidaucac.royItems.station;

import com.github.orenaidaucac.royItems.RoyItems;
import com.github.orenaidaucac.royItems.core.platform.PlatformScheduler;
import com.github.orenaidaucac.royItems.core.text.TextUtility;
import com.github.orenaidaucac.royItems.integration.IntegrationManager;
import com.github.orenaidaucac.royItems.item.ItemBuilder;
import com.github.orenaidaucac.royItems.item.ItemPDCManager;
import com.github.orenaidaucac.royItems.item.ItemRegistry;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandMap;
import org.bukkit.command.CommandSender;
import org.bukkit.command.defaults.BukkitCommand;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
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

    public enum CloseReason {
        RELOAD_CONFIG,
        PLUGIN_DISABLE
    }

    /**
     * Tracks an active Crafting Station GUI session for a player, allowing atomic
     * close/reload locking and exact GUI holder ownership verification.
     */
    public static final class StationGuiSession {
        private final InventoryHolder gui;
        private volatile boolean closing;

        public StationGuiSession(InventoryHolder gui) {
            this.gui = gui;
            this.closing = false;
        }

        public InventoryHolder getGui() {
            return gui;
        }

        public boolean isClosing() {
            return closing;
        }

        public void markClosing() {
            this.closing = true;
        }
    }

    private final Map<String, CraftingStation> stations = new LinkedHashMap<>();
    private final Map<UUID, StationGuiSession> openGuis = new ConcurrentHashMap<>();
    private final Map<UUID, PlatformScheduler.TaskHandle> activeCraftingTasks = new ConcurrentHashMap<>();
    private final List<String> registeredCommandAliases = new ArrayList<>();
    private volatile boolean shuttingDownOrReloading = false;

    private Listener fancyNpcsListener;
    private Listener citizensListener;
    private Listener phoenixNpcsListener;

    public CraftingStationManager(RoyItems plugin, ItemRegistry registry, ItemPDCManager pdcManager,
                                  ItemBuilder itemBuilder, IntegrationManager integration) {
        this.plugin = plugin;
        this.registry = registry;
        this.pdcManager = pdcManager;
        this.itemBuilder = itemBuilder;
        this.integration = integration;
    }

    private java.util.logging.Logger getLogger() {
        return plugin != null ? plugin.getLogger() : java.util.logging.Logger.getLogger("RoyItems");
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
                    if (isAcceptingSessions()) {
                        openStation(event.getPlayer(), station.getId(), 1);
                    }
                    return;
                }
            }
        }
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onStationInventoryClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) return;
        Inventory top = event.getView() != null ? event.getView().getTopInventory() : event.getInventory();
        if (top == null) return;
        InventoryHolder topHolder = top.getHolder();
        if (topHolder == null) return;

        StationGuiSession session = openGuis.get(player.getUniqueId());
        if (session != null && session.getGui() == topHolder) {
            if (session.isClosing() || shuttingDownOrReloading || !isPluginEnabledSafe()) {
                event.setCancelled(true);
                event.setResult(Event.Result.DENY);
            }
        }
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onStationInventoryDrag(InventoryDragEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) return;
        Inventory top = event.getView() != null ? event.getView().getTopInventory() : event.getInventory();
        if (top == null) return;
        InventoryHolder topHolder = top.getHolder();
        if (topHolder == null) return;

        StationGuiSession session = openGuis.get(player.getUniqueId());
        if (session != null && session.getGui() == topHolder) {
            if (session.isClosing() || shuttingDownOrReloading || !isPluginEnabledSafe()) {
                event.setCancelled(true);
                event.setResult(Event.Result.DENY);
            }
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onStationInventoryClose(InventoryCloseEvent event) {
        if (!(event.getPlayer() instanceof Player player)) return;
        Inventory inv = event.getInventory();
        if (inv != null && inv.getHolder() != null) {
            InventoryHolder closedHolder = inv.getHolder();
            UUID uuid = player.getUniqueId();
            StationGuiSession session = openGuis.get(uuid);
            if (session != null && session.getGui() == closedHolder) {
                cancelActiveCrafting(uuid);
                untrackOpenGui(uuid, closedHolder);
            }
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerQuit(PlayerQuitEvent event) {
        UUID uuid = event.getPlayer().getUniqueId();
        cancelActiveCrafting(uuid);
        openGuis.remove(uuid);
    }

    public synchronized void loadAll() {
        shuttingDownOrReloading = true;
        closeAllOpenGuis(CloseReason.RELOAD_CONFIG);
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
                    getLogger().log(Level.SEVERE, "Failed to load crafting station " + file.getName() + ": " + e.getMessage(), e);
                }
            }
        }

        validateNpcConfigurations();
        getLogger().info("Successfully loaded " + stations.size() + " crafting stations.");
        registerCommandAliases();
        if (isPluginEnabledSafe()) {
            shuttingDownOrReloading = false;
        }
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
            cfg.set("phoenixnpcs", new ArrayList<>());

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
            getLogger().log(Level.SEVERE, "Failed to create station file " + cleanId + ".yml: " + e.getMessage(), e);
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
        if (player == null || !player.isOnline() || stationId == null || !isAcceptingSessions()) return;
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

    public synchronized void registerNpcListeners() {
        registerFancyNpcsListener();
        registerCitizensListener();
        registerPhoenixNpcsListener();
    }

    public synchronized void registerFancyNpcsListener() {
        if (fancyNpcsListener != null || !integration.hasFancyNpcs()) return;
        try {
            fancyNpcsListener = new Listener() {
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
            };
            Bukkit.getPluginManager().registerEvents(fancyNpcsListener, plugin);
            getLogger().info("Registered FancyNpcs interaction listener for Crafting Stations.");
        } catch (Throwable e) {
            getLogger().warning("Could not register FancyNpcs listener: " + e.getMessage());
            fancyNpcsListener = null;
        }
    }

    public synchronized void registerCitizensListener() {
        if (citizensListener != null || !integration.hasCitizens()) return;
        try {
            citizensListener = new Listener() {
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
            };
            Bukkit.getPluginManager().registerEvents(citizensListener, plugin);
            getLogger().info("Registered Citizens NPC interaction listener for Crafting Stations.");
        } catch (Throwable e) {
            getLogger().warning("Could not register Citizens listener: " + e.getMessage());
            citizensListener = null;
        }
    }

    public synchronized void registerPhoenixNpcsListener() {
        if (phoenixNpcsListener != null || !integration.hasPhoenixNpcs()) return;
        try {
            phoenixNpcsListener = new Listener() {
                @EventHandler(priority = EventPriority.NORMAL, ignoreCancelled = true)
                public void onPhoenixNpcInteract(com.phoenixplugins.phoenixnpcs.api.events.NpcInteractEvent event) {
                    if (handlePhoenixNpcInteract(event.getPlayer(), event.getNpc())) {
                        event.setCancelled(true);
                    }
                }
            };
            Bukkit.getPluginManager().registerEvents(phoenixNpcsListener, plugin);
            getLogger().info("Registered PhoenixNPCs interaction listener for Crafting Stations.");
        } catch (Throwable e) {
            getLogger().warning("Could not register PhoenixNPCs listener: " + e.getMessage());
            phoenixNpcsListener = null;
        }
    }

    public synchronized void unregisterPhoenixNpcsListener() {
        if (phoenixNpcsListener != null) {
            HandlerList.unregisterAll(phoenixNpcsListener);
            phoenixNpcsListener = null;
        }
    }

    public synchronized void unregisterFancyNpcsListener() {
        if (fancyNpcsListener != null) {
            HandlerList.unregisterAll(fancyNpcsListener);
            fancyNpcsListener = null;
        }
    }

    public synchronized void unregisterCitizensListener() {
        if (citizensListener != null) {
            HandlerList.unregisterAll(citizensListener);
            citizensListener = null;
        }
    }

    /**
     * Handles an interaction with a PhoenixNPC.
     * Verifies NPC validity (enabled, visible to player, valid identifier),
     * and if matched to a configured Crafting Station, opens it.
     *
     * @param player The interacting player
     * @param npc    The interacted PhoenixNPC
     * @return true if a station was matched and opened; false otherwise
     */
    public boolean handlePhoenixNpcInteract(Player player, com.phoenixplugins.phoenixnpcs.api.Npc npc) {
        if (player == null || !player.isOnline() || npc == null) return false;
        String npcId = npc.getIdentifier();
        if (npcId == null || npcId.isBlank()) return false;
        if (!npc.isEnabled()) return false;
        try {
            if (!npc.isVisibleTo(player)) return false;
        } catch (Throwable ignored) {}

        String npcName = npc.getDisplayName();

        for (CraftingStation station : stations.values()) {
            for (String target : station.getPhoenixNpcs()) {
                if (target.equalsIgnoreCase(npcId) || (npcName != null && target.equalsIgnoreCase(npcName))) {
                    openStation(player, station.getId(), 1);
                    return true;
                }
            }
        }
        return false;
    }

    /**
     * Validates NPC bindings across all loaded crafting stations.
     * Detects duplicate NPC IDs across different stations and warns about missing PhoenixNPCs.
     */
    public void validateNpcConfigurations() {
        // 1. Detect duplicate PhoenixNPC IDs across different crafting stations
        Map<String, String> registeredPhoenixNpcs = new HashMap<>();
        for (CraftingStation station : stations.values()) {
            for (String npcId : new ArrayList<>(station.getPhoenixNpcs())) {
                String existingStation = registeredPhoenixNpcs.putIfAbsent(npcId.toLowerCase(), station.getId());
                if (existingStation != null) {
                    getLogger().warning("[CraftingStation] Duplicate PhoenixNPC ID '" + npcId +
                            "' detected in station '" + station.getId() + "'! Already assigned to station '" +
                            existingStation + "'. Removing duplicate binding from '" + station.getId() + "'.");
                    station.getPhoenixNpcs().remove(npcId);
                }
            }
        }

        // 2. Detect duplicate FancyNpcs across different crafting stations
        Map<String, String> registeredFancyNpcs = new HashMap<>();
        for (CraftingStation station : stations.values()) {
            for (String npcId : new ArrayList<>(station.getFancyNpcs())) {
                String existingStation = registeredFancyNpcs.putIfAbsent(npcId.toLowerCase(), station.getId());
                if (existingStation != null) {
                    getLogger().warning("[CraftingStation] Duplicate FancyNpc ID/Name '" + npcId +
                            "' detected in station '" + station.getId() + "'! Already assigned to station '" +
                            existingStation + "'. Removing duplicate binding from '" + station.getId() + "'.");
                    station.getFancyNpcs().remove(npcId);
                }
            }
        }

        // 3. Detect duplicate Citizens across different crafting stations
        Map<Integer, String> registeredCitizens = new HashMap<>();
        for (CraftingStation station : stations.values()) {
            for (Integer npcId : new ArrayList<>(station.getCitizenNpcs())) {
                String existingStation = registeredCitizens.putIfAbsent(npcId, station.getId());
                if (existingStation != null) {
                    getLogger().warning("[CraftingStation] Duplicate Citizens NPC ID '" + npcId +
                            "' detected in station '" + station.getId() + "'! Already assigned to station '" +
                            existingStation + "'. Removing duplicate binding from '" + station.getId() + "'.");
                    station.getCitizenNpcs().remove(npcId);
                }
            }
        }

        // 4. Validate existence of configured PhoenixNPCs if PhoenixNPCs is currently available
        if (integration != null && integration.hasPhoenixNpcs()) {
            try {
                var api = com.phoenixplugins.phoenixnpcs.api.PhoenixNPCsAPI.get();
                if (api != null) {
                    for (CraftingStation station : stations.values()) {
                        for (String npcId : station.getPhoenixNpcs()) {
                            if (api.getNpc(npcId).isEmpty()) {
                                getLogger().warning("[CraftingStation] Station '" + station.getId() +
                                        "' configured PhoenixNPC ID '" + npcId + "', but no NPC with this ID was found in PhoenixNPCs.");
                            }
                        }
                    }
                }
            } catch (Throwable ignored) {}
        }
    }

    // =========================================================================
    // GUI TRACKING & SAFE SHUTDOWN / RELOAD
    // =========================================================================

    public boolean isPluginEnabledSafe() {
        if (plugin == null) return true;
        try {
            return plugin.isEnabled();
        } catch (Throwable ignored) {
            return true;
        }
    }

    public boolean isShuttingDownOrReloading() {
        return shuttingDownOrReloading;
    }

    public void setShuttingDownOrReloading(boolean shuttingDownOrReloading) {
        this.shuttingDownOrReloading = shuttingDownOrReloading;
    }

    public boolean isAcceptingSessions() {
        return !shuttingDownOrReloading && isPluginEnabledSafe();
    }

    public void trackOpenGui(Player player, InventoryHolder gui) {
        if (player != null && gui != null) {
            openGuis.put(player.getUniqueId(), new StationGuiSession(gui));
        }
    }

    public void untrackOpenGui(UUID uuid) {
        if (uuid != null) {
            openGuis.remove(uuid);
        }
    }

    /**
     * Atomically untracks the GUI only if the currently tracked session still matches expectedGui.
     * Prevents page switches, refreshes, or detail transitions from erasing the newly opened GUI session.
     */
    public void untrackOpenGui(UUID uuid, InventoryHolder expectedGui) {
        if (uuid == null || expectedGui == null) return;
        openGuis.computeIfPresent(uuid, (k, session) -> session.getGui() == expectedGui ? null : session);
    }

    public boolean isTrackedSessionActive(UUID uuid, InventoryHolder expectedGui) {
        if (uuid == null || expectedGui == null || !isAcceptingSessions()) return false;
        StationGuiSession session = openGuis.get(uuid);
        return session != null && session.getGui() == expectedGui && !session.isClosing();
    }

    public boolean isTrackedSessionActive(Player player, InventoryHolder expectedGui) {
        if (player == null || !player.isOnline() || !player.isValid()) return false;
        if (!isTrackedSessionActive(player.getUniqueId(), expectedGui)) return false;
        try {
            var view = player.getOpenInventory();
            if (view != null && view.getTopInventory() != null) {
                return view.getTopInventory().getHolder() == expectedGui;
            }
        } catch (Throwable ignored) {}
        return true;
    }

    public boolean isGuiClosing(UUID uuid, InventoryHolder gui) {
        if (!isAcceptingSessions()) return true;
        if (uuid == null || gui == null) return false;
        StationGuiSession session = openGuis.get(uuid);
        return session != null && session.getGui() == gui && session.isClosing();
    }

    public int getTrackedGuiCount() {
        return openGuis.size();
    }

    public int getActiveCraftingCount() {
        return activeCraftingTasks.size();
    }

    public void closeAllOpenGuis() {
        closeAllOpenGuis(CloseReason.RELOAD_CONFIG);
    }

    /**
     * Safely closes every open Crafting Station GUI across all platforms (Bukkit/Spigot/Paper/Purpur/Folia/Leaf).
     * Cancels active crafting countdowns before touching inventories, verifies exact InventoryHolder ownership,
     * closes synchronously (avoiding TriumphGUI's 2-tick delayed close task), and notifies players.
     */
    public void closeAllOpenGuis(CloseReason reason) {
        this.shuttingDownOrReloading = true;

        // 1. Collect all sessions not yet marked closing and lock them immediately
        List<Map.Entry<UUID, StationGuiSession>> toClose = new ArrayList<>();
        for (Map.Entry<UUID, StationGuiSession> entry : openGuis.entrySet()) {
            StationGuiSession session = entry.getValue();
            if (session != null && !session.isClosing()) {
                session.markClosing();
                toClose.add(entry);
            }
        }

        // 2. Cancel all active crafting countdowns atomically before closing inventories
        Set<UUID> cancelledCraftingPlayers = new HashSet<>();
        for (UUID uuid : new ArrayList<>(activeCraftingTasks.keySet())) {
            PlatformScheduler.TaskHandle handle = activeCraftingTasks.remove(uuid);
            if (handle != null) {
                cancelledCraftingPlayers.add(uuid);
                try {
                    handle.cancel();
                } catch (Throwable ignored) {}
            }
        }

        // 3. Close each verified station GUI on the player's owning thread/region
        for (Map.Entry<UUID, StationGuiSession> entry : toClose) {
            UUID uuid = entry.getKey();
            StationGuiSession session = entry.getValue();
            InventoryHolder trackedGui = session.getGui();

            Player player = null;
            try {
                if (Bukkit.getServer() != null) {
                    player = Bukkit.getPlayer(uuid);
                }
            } catch (Throwable ignored) {}

            if (player == null || !player.isOnline()) {
                untrackOpenGui(uuid, trackedGui);
                cancelledCraftingPlayers.remove(uuid);
                continue;
            }

            boolean hadActiveCraft = cancelledCraftingPlayers.remove(uuid);
            Player targetPlayer = player;
            Runnable closeTask = () -> closeVerifiedStationInventory(targetPlayer, trackedGui, hadActiveCraft, reason);

            PlatformScheduler scheduler = plugin != null ? plugin.getPlatformScheduler() : null;
            if (scheduler != null) {
                if (reason == CloseReason.PLUGIN_DISABLE && !isPluginEnabledSafe() && !scheduler.isOwnedByCurrentThread(targetPlayer)) {
                    untrackOpenGui(uuid, trackedGui);
                    continue;
                }
                scheduler.runEntityNowOrSchedule(targetPlayer, closeTask);
            } else {
                closeTask.run();
            }
        }

        // 4. Notify any player whose crafting countdown was cancelled outside a tracked GUI entry
        for (UUID uuid : cancelledCraftingPlayers) {
            try {
                Player p = Bukkit.getServer() != null ? Bukkit.getPlayer(uuid) : null;
                if (p != null && p.isOnline() && p.isValid() && plugin != null && plugin.getMessageManager() != null) {
                    PlatformScheduler scheduler = plugin.getPlatformScheduler();
                    if (scheduler != null) {
                        scheduler.runEntityNowOrSchedule(p, () -> plugin.getMessageManager().send(p, "craft-cancelled"));
                    } else {
                        plugin.getMessageManager().send(p, "craft-cancelled");
                    }
                }
            } catch (Throwable ignored) {}
        }
    }

    private void closeVerifiedStationInventory(Player player, InventoryHolder trackedGui, boolean hadActiveCraft, CloseReason reason) {
        try {
            if (player == null || !player.isOnline() || !player.isValid()) {
                return;
            }
            var view = player.getOpenInventory();
            if (view == null || view.getTopInventory() == null) {
                return;
            }
            // Strict session ownership check: NEVER close another plugin's GUI or Vanilla inventory
            if (view.getTopInventory().getHolder() != trackedGui) {
                return;
            }

            if (hadActiveCraft && plugin != null && plugin.getMessageManager() != null) {
                plugin.getMessageManager().send(player, "craft-cancelled");
            }

            // Close synchronously on the owning thread/region (avoids TriumphGUI's 2-tick delayed task)
            player.closeInventory();

            if (plugin != null && plugin.getMessageManager() != null) {
                plugin.getMessageManager().send(player, "station-closed-reload",
                        "&eYour Crafting Station menu was closed safely because the server is reloading RoyItems.");
            }
        } catch (Throwable ignored) {
        } finally {
            if (player != null) {
                untrackOpenGui(player.getUniqueId(), trackedGui);
            }
        }
    }

    public void cleanup() {
        shuttingDownOrReloading = true;
        closeAllOpenGuis(CloseReason.PLUGIN_DISABLE);
        openGuis.clear();
        activeCraftingTasks.clear();
        unregisterCommandAliases();
        unregisterPhoenixNpcsListener();
        unregisterFancyNpcsListener();
        unregisterCitizensListener();
        try {
            HandlerList.unregisterAll(this);
        } catch (Throwable ignored) {}
    }

    public boolean isCrafting(UUID uuid) {
        return uuid != null && activeCraftingTasks.containsKey(uuid);
    }

    public void setActiveCraftingTask(UUID uuid, PlatformScheduler.TaskHandle task) {
        if (uuid == null) return;
        cancelActiveCrafting(uuid);
        if (task != null) {
            activeCraftingTasks.put(uuid, task);
        }
    }

    public void setActiveCraftingTask(UUID uuid, BukkitTask task) {
        setActiveCraftingTask(uuid, task != null ? task::cancel : null);
    }

    public void cancelActiveCrafting(UUID uuid) {
        if (uuid == null) return;
        PlatformScheduler.TaskHandle task = activeCraftingTasks.remove(uuid);
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
