package com.github.orenaidaucac.royItems.item;

import com.github.orenaidaucac.royItems.core.platform.PlatformScheduler;
import com.github.orenaidaucac.royItems.core.text.TextUtility;
import com.github.orenaidaucac.royItems.lore.DynamicLoreEngine;
import com.github.orenaidaucac.royItems.lore.LoreFormat;
import com.github.orenaidaucac.royItems.stat.StatManager;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.inventory.meta.Damageable;
import org.bukkit.inventory.meta.EnchantmentStorageMeta;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.plugin.Plugin;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Consumer;

/**
 * High-performance LiveUpdate Service: Synchronizes custom items across player inventories,
 * equipment, off-hand, and ender chests when administrators reload or save edits.
 *
 * Optimizations:
 * - Tick-budgeted batch throttling (max-players-per-tick, max-items-per-tick, tick-interval)
 *   to prevent sudden TPS drops on large servers.
 * - Single-ItemMeta PDC inspection (~90% fewer CraftMetaItem clones per item).
 * - O(1) config-fingerprint skip (royitems:item_sync_hash) for unchanged items, avoiding
 *   unnecessary rebuilds, inventory packet spam, and client inventory flicker.
 * - Pre-built base template cache per reload generation.
 * - Atomic slot-level updates (setItem only on modified slots) and strict generation checks
 *   for safe consecutive reloads, PlugmanX disable, and Folia region-thread execution.
 */
public class LiveUpdateService {

    private final ItemRegistry registry;
    private final ItemPDCManager pdcManager;
    private final ItemBuilder itemBuilder;
    private final PlatformScheduler scheduler;
    private final LiveUpdateConfig config = new LiveUpdateConfig();
    private StatManager statManager;

    // Reload generation tracking to abort stale batches on consecutive reloads or plugin disable
    private final AtomicLong activeGeneration = new AtomicLong(0L);
    private volatile PlatformScheduler.TaskHandle activeBatchTask;
    private final Set<PlatformScheduler.TaskHandle> activeFoliaTasks = ConcurrentHashMap.newKeySet();

    private record CachedTemplate(ItemStack item, Component displayName) {}
    private record BaseSignature(Material material, Component displayName) {}

    // Per-generation caches (cleared on loadConfig, cancelActiveUpdates, and start of batch reload)
    private final Map<String, Integer> definitionHashCache = new ConcurrentHashMap<>();
    private final Map<String, CachedTemplate> baseTemplateCache = new ConcurrentHashMap<>();
    private final Map<String, BaseSignature> baseSignatureCache = new ConcurrentHashMap<>();

    public LiveUpdateService(ItemRegistry registry, ItemPDCManager pdcManager, ItemBuilder itemBuilder, PlatformScheduler scheduler) {
        this.registry = registry;
        this.pdcManager = pdcManager;
        this.itemBuilder = itemBuilder;
        this.scheduler = scheduler;
    }

    public void setStatManager(StatManager statManager) {
        this.statManager = statManager;
    }

    public LiveUpdateConfig getConfig() {
        return config;
    }

    public void loadConfig(FileConfiguration fileConfig) {
        config.load(fileConfig);
        invalidateCaches();
    }

    public boolean isEnabled() {
        return config.isEnabled();
    }

    public boolean isAutoUpdateOnReload() {
        return config.isAutoUpdateOnReload();
    }

    public long getActiveGeneration() {
        return activeGeneration.get();
    }

    public int getCachedTemplateCount() {
        return baseTemplateCache.size();
    }

    public int getCachedHashCount() {
        return definitionHashCache.size();
    }

    public void invalidateCaches() {
        definitionHashCache.clear();
        baseTemplateCache.clear();
        baseSignatureCache.clear();
    }

    /**
     * Cancels any in-flight batched LiveUpdate tasks and clears cached templates/hashes.
     * Safe to invoke during consecutive reloads or plugin disable (e.g. PlugmanX).
     */
    public void cancelActiveUpdates() {
        activeGeneration.incrementAndGet();

        PlatformScheduler.TaskHandle batch = this.activeBatchTask;
        this.activeBatchTask = null;
        if (batch != null) {
            try {
                batch.cancel();
            } catch (Throwable ignored) {}
        }

        if (!activeFoliaTasks.isEmpty()) {
            for (PlatformScheduler.TaskHandle h : new ArrayList<>(activeFoliaTasks)) {
                activeFoliaTasks.remove(h);
                if (h != null) {
                    try {
                        h.cancel();
                    } catch (Throwable ignored) {}
                }
            }
        }

        invalidateCaches();
    }

    /**
     * Updates all online players' inventories, equipment, and ender chests across ticks according
     * to the configured tick budget, invoking the callback upon completion of the active generation.
     */
    public void updateAllOnlinePlayers(Consumer<LiveUpdateReport> callback) {
        Collection<? extends Player> onlinePlayers = Bukkit.getServer() != null ? Bukkit.getOnlinePlayers() : Collections.emptyList();
        updatePlayersBatched(onlinePlayers, uuid -> Bukkit.getServer() != null ? Bukkit.getPlayer(uuid) : null, callback);
    }

    /**
     * Executes batched LiveUpdate across the given player collection with a custom player lookup.
     */
    public void updatePlayersBatched(Collection<? extends Player> onlinePlayers,
                                     java.util.function.Function<UUID, Player> playerLookup,
                                     Consumer<LiveUpdateReport> callback) {
        cancelActiveUpdates();
        final long myGen = activeGeneration.get();

        if (!config.isEnabled()) {
            if (callback != null) {
                callback.accept(new LiveUpdateReport(0, 0, 0, 0));
            }
            return;
        }

        if (onlinePlayers == null || onlinePlayers.isEmpty()) {
            if (callback != null) {
                callback.accept(new LiveUpdateReport(0, 0, 0, 0));
            }
            return;
        }

        List<UUID> playerUuids = new ArrayList<>(onlinePlayers.size());
        for (Player p : onlinePlayers) {
            if (p != null && p.isOnline()) {
                playerUuids.add(p.getUniqueId());
            }
        }

        if (playerUuids.isEmpty()) {
            if (callback != null) {
                callback.accept(new LiveUpdateReport(0, 0, 0, 0));
            }
            return;
        }

        final int totalPlayers = playerUuids.size();
        final AtomicInteger invTotal = new AtomicInteger(0);
        final AtomicInteger ecTotal = new AtomicInteger(0);
        final AtomicInteger failTotal = new AtomicInteger(0);

        final int maxPlayersPerTick = config.getMaxPlayersPerTick() <= 0 ? Integer.MAX_VALUE : config.getMaxPlayersPerTick();
        final int maxItemsPerTick = config.getMaxItemsPerTick() <= 0 ? Integer.MAX_VALUE : config.getMaxItemsPerTick();
        final int tickInterval = Math.max(1, config.getTickInterval());

        if (scheduler != null && scheduler.isFolia()) {
            dispatchFoliaBatchedUpdates(playerUuids, playerLookup, myGen, maxPlayersPerTick, maxItemsPerTick, tickInterval,
                    invTotal, ecTotal, failTotal, totalPlayers, callback);
        } else {
            dispatchGlobalBatchedUpdates(playerUuids, playerLookup, myGen, maxPlayersPerTick, maxItemsPerTick, tickInterval,
                    invTotal, ecTotal, failTotal, totalPlayers, callback);
        }
    }

    private static final class PlayerUpdateCursor {
        final UUID uuid;
        int phase = 0; // 0 = storage, 1 = armor, 2 = offhand, 3 = enderChest, 4 = done
        int slot = 0;

        PlayerUpdateCursor(UUID uuid) {
            this.uuid = uuid;
        }
    }

    private void dispatchGlobalBatchedUpdates(List<UUID> playerUuids,
                                              java.util.function.Function<UUID, Player> playerLookup,
                                              long myGen,
                                              int maxPlayersPerTick, int maxItemsPerTick, int tickInterval,
                                              AtomicInteger invTotal, AtomicInteger ecTotal, AtomicInteger failTotal,
                                              int totalPlayers, Consumer<LiveUpdateReport> callback) {
        final ArrayDeque<PlayerUpdateCursor> queue = new ArrayDeque<>(playerUuids.size());
        for (UUID uuid : playerUuids) {
            queue.add(new PlayerUpdateCursor(uuid));
        }
        final PlatformScheduler.TaskHandle[] selfHandle = {null};

        Runnable step = () -> {
            if (myGen != activeGeneration.get() || !isPluginEnabledSafe()) {
                if (selfHandle[0] != null) selfHandle[0].cancel();
                return;
            }

            int playersThisTick = 0;
            AtomicInteger itemsRebuiltThisTick = new AtomicInteger(0);

            while (!queue.isEmpty()) {
                if (playersThisTick >= maxPlayersPerTick || itemsRebuiltThisTick.get() >= maxItemsPerTick) {
                    break;
                }

                PlayerUpdateCursor cursor = queue.peek();
                if (cursor == null) break;

                Player player = playerLookup.apply(cursor.uuid);
                if (player == null || !player.isOnline() || !player.isValid()) {
                    queue.poll();
                    continue;
                }

                try {
                    boolean finished = advanceCursorStep(player, cursor, maxItemsPerTick,
                            invTotal, ecTotal, failTotal, itemsRebuiltThisTick);
                    if (finished) {
                        queue.poll();
                        playersThisTick++;
                    } else {
                        // Item rebuild budget exhausted mid-player; resume exact slot on next tick
                        break;
                    }
                } catch (Throwable t) {
                    failTotal.incrementAndGet();
                    queue.poll();
                    playersThisTick++;
                }
            }

            if (queue.isEmpty()) {
                if (selfHandle[0] != null) selfHandle[0].cancel();
                if (activeBatchTask == selfHandle[0]) {
                    activeBatchTask = null;
                }
                if (callback != null && myGen == activeGeneration.get()) {
                    callback.accept(new LiveUpdateReport(
                            invTotal.get(), ecTotal.get(), failTotal.get(), totalPlayers));
                }
            }
        };

        if (scheduler == null || (Bukkit.getServer() == null && scheduler.getClass() == PlatformScheduler.class)) {
            // Synchronous fallback when running headless unit tests without a custom test scheduler
            while (!queue.isEmpty()) {
                step.run();
            }
            return;
        }

        PlatformScheduler.TaskHandle handle = scheduler.runGlobalTimerHandle(h -> {
            selfHandle[0] = h;
            step.run();
        }, 0L, tickInterval);
        selfHandle[0] = handle;
        this.activeBatchTask = handle;
    }

    private void dispatchFoliaBatchedUpdates(List<UUID> playerUuids,
                                             java.util.function.Function<UUID, Player> playerLookup,
                                             long myGen,
                                             int maxPlayersPerTick, int maxItemsPerTick, int tickInterval,
                                             AtomicInteger invTotal, AtomicInteger ecTotal, AtomicInteger failTotal,
                                             int totalPlayers, Consumer<LiveUpdateReport> callback) {
        final AtomicInteger pending = new AtomicInteger(totalPlayers);
        final int waveSize = Math.max(1, maxPlayersPerTick == Integer.MAX_VALUE ? totalPlayers : maxPlayersPerTick);

        for (int idx = 0; idx < totalPlayers; idx++) {
            final UUID uuid = playerUuids.get(idx);
            final long delayTicks = ((long) (idx / waveSize)) * tickInterval;
            Player initialPlayer = playerLookup.apply(uuid);
            if (initialPlayer == null || !initialPlayer.isOnline()) {
                completeFoliaPlayer(pending, myGen, invTotal, ecTotal, failTotal, totalPlayers, callback);
                continue;
            }
            scheduleFoliaCursorStep(initialPlayer, new PlayerUpdateCursor(uuid), playerLookup, myGen,
                    maxItemsPerTick, tickInterval, delayTicks, invTotal, ecTotal, failTotal, pending, totalPlayers, callback);
        }
    }

    private void scheduleFoliaCursorStep(Player targetHint, PlayerUpdateCursor cursor,
                                         java.util.function.Function<UUID, Player> playerLookup,
                                         long myGen, int maxItemsPerTick, int tickInterval, long delayTicks,
                                         AtomicInteger invTotal, AtomicInteger ecTotal, AtomicInteger failTotal,
                                         AtomicInteger pending, int totalPlayers, Consumer<LiveUpdateReport> callback) {
        final PlatformScheduler.TaskHandle[] handleHolder = new PlatformScheduler.TaskHandle[1];
        PlatformScheduler.TaskHandle handle = scheduler.runEntityDelayedHandle(targetHint, () -> {
            if (handleHolder[0] != null) {
                activeFoliaTasks.remove(handleHolder[0]);
            }
            if (myGen != activeGeneration.get() || !isPluginEnabledSafe()) {
                return;
            }

            Player p = playerLookup.apply(cursor.uuid);
            if (p == null || !p.isOnline() || !p.isValid()) {
                completeFoliaPlayer(pending, myGen, invTotal, ecTotal, failTotal, totalPlayers, callback);
                return;
            }

            AtomicInteger rebuiltThisTick = new AtomicInteger(0);
            try {
                boolean finished = advanceCursorStep(p, cursor, maxItemsPerTick,
                        invTotal, ecTotal, failTotal, rebuiltThisTick);
                if (!finished) {
                    scheduleFoliaCursorStep(p, cursor, playerLookup, myGen,
                            maxItemsPerTick, tickInterval, tickInterval, invTotal, ecTotal, failTotal,
                            pending, totalPlayers, callback);
                    return;
                }
            } catch (Throwable t) {
                failTotal.incrementAndGet();
            }

            completeFoliaPlayer(pending, myGen, invTotal, ecTotal, failTotal, totalPlayers, callback);
        }, delayTicks);

        handleHolder[0] = handle;
        activeFoliaTasks.add(handle);
    }

    private boolean advanceCursorStep(Player player, PlayerUpdateCursor cursor, int maxRebuildsThisTick,
                                      AtomicInteger invTotal, AtomicInteger ecTotal,
                                      AtomicInteger failTotal, AtomicInteger rebuiltThisTick) {
        PlayerInventory inv = player.getInventory();

        while (cursor.phase < 4) {
            if (rebuiltThisTick.get() >= maxRebuildsThisTick) {
                return false;
            }

            switch (cursor.phase) {
                case 0 -> {
                    if (!config.isContainerInventory() || inv == null) {
                        cursor.phase = 1;
                        cursor.slot = 0;
                        continue;
                    }
                    ItemStack[] storage = inv.getStorageContents();
                    if (storage == null || cursor.slot >= storage.length) {
                        cursor.phase = 1;
                        cursor.slot = 0;
                        continue;
                    }
                    while (cursor.slot < storage.length) {
                        if (rebuiltThisTick.get() >= maxRebuildsThisTick) {
                            return false;
                        }
                        int i = cursor.slot++;
                        ItemStack current = storage[i];
                        if (current == null || current.getType().isAir() || !current.hasItemMeta()) continue;
                        ItemStack updated = updateItemInternal(current, player, failTotal, rebuiltThisTick);
                        if (updated != current) {
                            inv.setItem(i, updated);
                            invTotal.incrementAndGet();
                        }
                    }
                    cursor.phase = 1;
                    cursor.slot = 0;
                }
                case 1 -> {
                    if (!config.isContainerArmor() || inv == null) {
                        cursor.phase = 2;
                        cursor.slot = 0;
                        continue;
                    }
                    while (cursor.slot < 4) {
                        if (rebuiltThisTick.get() >= maxRebuildsThisTick) {
                            return false;
                        }
                        int slotIdx = cursor.slot++;
                        ItemStack piece = switch (slotIdx) {
                            case 0 -> inv.getHelmet();
                            case 1 -> inv.getChestplate();
                            case 2 -> inv.getLeggings();
                            default -> inv.getBoots();
                        };
                        if (piece == null || piece.getType().isAir() || !piece.hasItemMeta()) continue;
                        ItemStack updated = updateItemInternal(piece, player, failTotal, rebuiltThisTick);
                        if (updated != piece) {
                            switch (slotIdx) {
                                case 0 -> inv.setHelmet(updated);
                                case 1 -> inv.setChestplate(updated);
                                case 2 -> inv.setLeggings(updated);
                                default -> inv.setBoots(updated);
                            }
                            invTotal.incrementAndGet();
                        }
                    }
                    cursor.phase = 2;
                    cursor.slot = 0;
                }
                case 2 -> {
                    if (!config.isContainerOffhand() || inv == null) {
                        cursor.phase = 3;
                        cursor.slot = 0;
                        continue;
                    }
                    if (cursor.slot == 0) {
                        if (rebuiltThisTick.get() >= maxRebuildsThisTick) {
                            return false;
                        }
                        cursor.slot = 1;
                        ItemStack offhand = inv.getItemInOffHand();
                        if (offhand != null && !offhand.getType().isAir() && offhand.hasItemMeta()) {
                            ItemStack updated = updateItemInternal(offhand, player, failTotal, rebuiltThisTick);
                            if (updated != offhand) {
                                inv.setItemInOffHand(updated);
                                invTotal.incrementAndGet();
                            }
                        }
                    }
                    cursor.phase = 3;
                    cursor.slot = 0;
                }
                case 3 -> {
                    if (!config.isContainerEnderChest()) {
                        cursor.phase = 4;
                        cursor.slot = 0;
                        continue;
                    }
                    Inventory ec = player.getEnderChest();
                    ItemStack[] ecContents = ec != null ? ec.getContents() : null;
                    if (ecContents == null || cursor.slot >= ecContents.length) {
                        cursor.phase = 4;
                        cursor.slot = 0;
                        continue;
                    }
                    while (cursor.slot < ecContents.length) {
                        if (rebuiltThisTick.get() >= maxRebuildsThisTick) {
                            return false;
                        }
                        int i = cursor.slot++;
                        ItemStack current = ecContents[i];
                        if (current == null || current.getType().isAir() || !current.hasItemMeta()) continue;
                        ItemStack updated = updateItemInternal(current, player, failTotal, rebuiltThisTick);
                        if (updated != current) {
                            ec.setItem(i, updated);
                            ecTotal.incrementAndGet();
                        }
                    }
                    cursor.phase = 4;
                    cursor.slot = 0;
                }
                default -> cursor.phase = 4;
            }
        }

        if (statManager != null) {
            statManager.recalculate(player);
        }
        return true;
    }

    private void completeFoliaPlayer(AtomicInteger pending, long myGen,
                                     AtomicInteger invTotal, AtomicInteger ecTotal, AtomicInteger failTotal,
                                     int totalPlayers, Consumer<LiveUpdateReport> callback) {
        if (pending.decrementAndGet() == 0 && callback != null && myGen == activeGeneration.get()) {
            callback.accept(new LiveUpdateReport(
                    invTotal.get(), ecTotal.get(), failTotal.get(), totalPlayers));
        }
    }

    private boolean isPluginEnabledSafe() {
        DynamicLoreEngine loreEngine = itemBuilder != null ? itemBuilder.getLoreEngine() : null;
        Plugin plugin = loreEngine != null ? loreEngine.getPlugin() : null;
        if (plugin == null) return true;
        try {
            return plugin.isEnabled();
        } catch (Throwable ignored) {
            return true;
        }
    }

    /**
     * Backward-compatible call without report callback.
     */
    public void updateAllOnlinePlayers() {
        updateAllOnlinePlayers(null);
    }

    /**
     * Updates an individual player's main inventory, equipment, off-hand, and ender chest safely.
     */
    public void updatePlayer(Player player) {
        if (!config.isEnabled() || player == null || !player.isValid()) return;

        updatePlayerEquipmentAndInventory(player, null, null);
        if (config.isContainerEnderChest()) {
            updatePlayerEnderChest(player, null, null);
        }

        if (statManager != null) {
            statManager.recalculate(player);
        }
    }

    private int updatePlayerEquipmentAndInventory(Player player, AtomicInteger failCounter, AtomicInteger rebuiltCounter) {
        int invCount = 0;
        PlayerInventory inv = player.getInventory();
        if (inv == null) return 0;

        // 1. Storage contents (0-35) updated slot-by-slot only where changes occurred
        if (config.isContainerInventory()) {
            ItemStack[] storage = inv.getStorageContents();
            if (storage != null) {
                for (int i = 0; i < storage.length; i++) {
                    ItemStack current = storage[i];
                    if (current == null || current.getType().isAir() || !current.hasItemMeta()) continue;
                    ItemStack updated = updateItemInternal(current, player, failCounter, rebuiltCounter);
                    if (updated != current) {
                        inv.setItem(i, updated);
                        invCount++;
                    }
                }
            }
        }

        // 2. Armor slots updated individually only on modified pieces (prevents equip flicker/sounds)
        if (config.isContainerArmor()) {
            ItemStack helmet = inv.getHelmet();
            if (helmet != null && !helmet.getType().isAir() && helmet.hasItemMeta()) {
                ItemStack updated = updateItemInternal(helmet, player, failCounter, rebuiltCounter);
                if (updated != helmet) {
                    inv.setHelmet(updated);
                    invCount++;
                }
            }
            ItemStack chest = inv.getChestplate();
            if (chest != null && !chest.getType().isAir() && chest.hasItemMeta()) {
                ItemStack updated = updateItemInternal(chest, player, failCounter, rebuiltCounter);
                if (updated != chest) {
                    inv.setChestplate(updated);
                    invCount++;
                }
            }
            ItemStack legs = inv.getLeggings();
            if (legs != null && !legs.getType().isAir() && legs.hasItemMeta()) {
                ItemStack updated = updateItemInternal(legs, player, failCounter, rebuiltCounter);
                if (updated != legs) {
                    inv.setLeggings(updated);
                    invCount++;
                }
            }
            ItemStack boots = inv.getBoots();
            if (boots != null && !boots.getType().isAir() && boots.hasItemMeta()) {
                ItemStack updated = updateItemInternal(boots, player, failCounter, rebuiltCounter);
                if (updated != boots) {
                    inv.setBoots(updated);
                    invCount++;
                }
            }
        }

        // 3. Off-hand slot
        if (config.isContainerOffhand()) {
            ItemStack offhand = inv.getItemInOffHand();
            if (offhand != null && !offhand.getType().isAir() && offhand.hasItemMeta()) {
                ItemStack updated = updateItemInternal(offhand, player, failCounter, rebuiltCounter);
                if (updated != offhand) {
                    inv.setItemInOffHand(updated);
                    invCount++;
                }
            }
        }

        return invCount;
    }

    private int updatePlayerEnderChest(Player player, AtomicInteger failCounter, AtomicInteger rebuiltCounter) {
        int ecCount = 0;
        Inventory ec = player.getEnderChest();
        if (ec == null) return 0;
        ItemStack[] ecContents = ec.getContents();
        if (ecContents == null) return 0;

        for (int i = 0; i < ecContents.length; i++) {
            ItemStack current = ecContents[i];
            if (current == null || current.getType().isAir() || !current.hasItemMeta()) continue;
            ItemStack updated = updateItemInternal(current, player, failCounter, rebuiltCounter);
            if (updated != current) {
                ec.setItem(i, updated);
                ecCount++;
            }
        }
        return ecCount;
    }

    /**
     * Updates an individual ItemStack according to definition and configured preservation rules.
     */
    public ItemStack updateItem(ItemStack current, AtomicInteger failCounter) {
        return updateItemInternal(current, null, failCounter, null);
    }

    private ItemStack updateItemInternal(ItemStack current, Player player, AtomicInteger failCounter, AtomicInteger rebuiltCounter) {
        if (current == null || current.getType().isAir() || !current.hasItemMeta()) return current;

        ItemMeta curMeta = current.getItemMeta();
        if (curMeta == null || !pdcManager.isRoyItem(curMeta)) return current;

        ItemType type = pdcManager.getType(curMeta);
        String id = pdcManager.getId(curMeta);
        ItemDefinition def = registry.get(type, id);

        if (def == null) return current; // Definition removed or not loaded

        try {
            // 1. Read all PDC metadata from the single curMeta snapshot
            String socketsRaw = pdcManager.getSocketsData(curMeta);
            List<String> sockets = null;
            if (config.isPreserveGems() && socketsRaw != null && !socketsRaw.isEmpty()) {
                sockets = ItemBuilder.deserializeSockets(socketsRaw);
            }

            long expire = pdcManager.getExpireTime(curMeta);
            String oldTier = pdcManager.getTier(curMeta);
            String oldSetState = pdcManager.getSetState(curMeta);
            boolean isBonusActive = "active".equalsIgnoreCase(oldSetState);
            int storedSyncHash = pdcManager.getSyncHash(curMeta);
            String oldManagedLore = pdcManager.getManagedLoreData(curMeta);

            // 2. Active Enchantments resolution (Vanilla + EcoEnchants + Stored Enchants)
            Map<Enchantment, Integer> activeEnchants = new LinkedHashMap<>();
            if (config.isPreserveEnchantments()) {
                activeEnchants.putAll(curMeta.getEnchants());
                if (curMeta instanceof EnchantmentStorageMeta storageMeta) {
                    activeEnchants.putAll(storageMeta.getStoredEnchants());
                }
            }
            if (def.getEnchantments() != null) {
                for (Map.Entry<Enchantment, Integer> e : def.getEnchantments().entrySet()) {
                    activeEnchants.putIfAbsent(e.getKey(), e.getValue());
                }
            }

            // 3. Lightweight O(1) Config-Fingerprint Skip (no full ItemBuilder.build/lore parse on unchanged items)
            int defHash = getOrComputeDefinitionSyncHash(def);
            int expectedSyncHash = computeItemSyncHash(defHash, socketsRaw, oldTier, oldSetState, activeEnchants);

            if (expire == 0L && storedSyncHash != 0 && oldManagedLore != null && storedSyncHash == expectedSyncHash) {
                BaseSignature sig = getOrComputeBaseSignature(def);
                boolean matchesBase = true;
                if (sig != null) {
                    if (sig.material() != null && current.getType() != sig.material()) {
                        matchesBase = false;
                    } else if (!config.isPreserveDisplayName()) {
                        Component expectedName = sig.displayName();
                        Component currentName = curMeta.hasDisplayName() ? curMeta.displayName() : null;
                        if (!Objects.equals(currentName, expectedName)) {
                            matchesBase = false;
                        }
                    }
                }
                if (matchesBase) {
                    return current; // Zero rebuild, zero packet, zero flicker!
                }
            }

            if (rebuiltCounter != null) {
                rebuiltCounter.incrementAndGet();
            }

            // 4. Build updated base item (reusing cached base template when runtime state matches defaults and item has no dynamic set lore)
            ItemStack updated;
            boolean hasSet = def.getItemSet() != null && !def.getItemSet().isEmpty();
            boolean canReuseTemplate = expire == 0L
                    && !hasSet
                    && (sockets == null || sockets.isEmpty())
                    && !isBonusActive
                    && Objects.equals(activeEnchants, defaultEnchantsMap(def));

            if (canReuseTemplate) {
                CachedTemplate cachedTemplate = getOrBuildBaseTemplate(def);
                if (cachedTemplate != null && cachedTemplate.item() != null) {
                    updated = cachedTemplate.item().clone();
                    updated.setAmount(Math.max(1, current.getAmount()));
                } else {
                    updated = itemBuilder.build(def, current.getAmount(), sockets, expire, activeEnchants, isBonusActive, player);
                }
            } else {
                updated = itemBuilder.build(def, current.getAmount(), sockets, expire, activeEnchants, isBonusActive, player);
            }

            ItemMeta updMeta = updated.getItemMeta();
            if (updMeta == null) return current;

            // 5. Display Name preservation
            if (config.isPreserveDisplayName() && curMeta.hasDisplayName()) {
                updMeta.displayName(curMeta.displayName());
            }

            // 6. Lore preservation and clean RoyItems managed-lore synchronization
            List<Component> generatedBaseLore = updMeta.hasLore() ? updMeta.lore() : Collections.emptyList();
            DynamicLoreEngine loreEngine = itemBuilder.getLoreEngine();
            String nextManagedLore;
            if (config.isPreserveLore() && curMeta.hasLore()) {
                if (loreEngine != null) {
                    updMeta.lore(loreEngine.updateEnchantmentLinesInLore(curMeta.lore(), def, activeEnchants));
                } else {
                    updMeta.lore(curMeta.lore());
                }
                nextManagedLore = oldManagedLore;
            } else {
                if (curMeta.hasLore() && loreEngine != null) {
                    updMeta.lore(loreEngine.mergeCustomLore(
                            curMeta.lore(), generatedBaseLore, def, activeEnchants, oldManagedLore, true));
                }
                nextManagedLore = DynamicLoreEngine.serializeManagedLoreLines(generatedBaseLore);
            }

            // 7. Custom Model Data preservation
            if (config.isPreserveCustomModelData() && curMeta.hasCustomModelData()) {
                updMeta.setCustomModelData(curMeta.getCustomModelData());
            }

            // 8. Custom Attribute preservation
            if (config.isPreserveAttributes() && curMeta.hasAttributeModifiers()) {
                updMeta.setAttributeModifiers(curMeta.getAttributeModifiers());
            }

            // 9. Third-party PDC / custom NBT preservation
            if (config.isPreservePDC()) {
                curMeta.getPersistentDataContainer().copyTo(updMeta.getPersistentDataContainer(), false);
            }

            // 10. Re-tag core RoyItems PDC data cleanly on the single updMeta instance
            pdcManager.tagItem(updMeta, def.getType(), def.getId(), def.getVersion(), expire,
                    config.isPreserveGems() ? socketsRaw : null);

            int enchantHash = (loreEngine != null) ? loreEngine.computeEnchantmentHash(def, activeEnchants) : activeEnchants.hashCode();
            pdcManager.setEnchantHash(updMeta, enchantHash);
            pdcManager.setSyncHash(updMeta, expectedSyncHash);
            if (nextManagedLore != null) {
                pdcManager.setManagedLoreData(updMeta, nextManagedLore);
            }

            if (config.isPreserveTier() && oldTier != null && !oldTier.isEmpty()) {
                pdcManager.tagTier(updMeta, oldTier);
            } else if (def.getItemTier() != null && !def.getItemTier().isEmpty()) {
                pdcManager.tagTier(updMeta, def.getItemTier());
            }

            if (oldSetState != null && !oldSetState.isEmpty()) {
                pdcManager.setSetState(updMeta, oldSetState);
            }

            // 11. Enchantments preservation directly on updMeta (avoids extra setItemMeta/getItemMeta roundtrips)
            if (config.isPreserveEnchantments()) {
                Map<Enchantment, Integer> curEnchants = curMeta.getEnchants();
                if (!curEnchants.isEmpty()) {
                    for (Map.Entry<Enchantment, Integer> entry : curEnchants.entrySet()) {
                        updMeta.addEnchant(entry.getKey(), entry.getValue(), true);
                    }
                }
                if (curMeta instanceof EnchantmentStorageMeta curStorage
                        && updMeta instanceof EnchantmentStorageMeta updStorage) {
                    for (Map.Entry<Enchantment, Integer> entry : curStorage.getStoredEnchants().entrySet()) {
                        updStorage.addStoredEnchant(entry.getKey(), entry.getValue(), true);
                    }
                }
            }

            // 12. Durability / Damage preservation directly on updMeta
            if (config.isPreserveDurability()) {
                if (curMeta instanceof Damageable curDmg && updMeta instanceof Damageable updDmg) {
                    if (curDmg.hasDamage() && !def.isUnbreakable()) {
                        updDmg.setDamage(curDmg.getDamage());
                    }
                }
            }

            updated.setItemMeta(updMeta);
            return updated;
        } catch (Throwable t) {
            if (failCounter != null) {
                failCounter.incrementAndGet();
            }
            return current;
        }
    }

    private Map<Enchantment, Integer> defaultEnchantsMap(ItemDefinition def) {
        return def.getEnchantments() != null ? def.getEnchantments() : Collections.emptyMap();
    }

    private BaseSignature getOrComputeBaseSignature(ItemDefinition def) {
        if (def == null) return null;
        String key = def.getType().name() + ":" + def.getId().toLowerCase(Locale.ROOT);
        BaseSignature cached = baseSignatureCache.get(key);
        if (cached != null) return cached;

        Material mat = def.getMaterial() != null ? Material.matchMaterial(def.getMaterial()) : null;
        Component expectedName = (def.getName() != null && !def.getName().isEmpty())
                ? TextUtility.formatItemName(def.getName())
                : null;
        BaseSignature sig = new BaseSignature(mat, expectedName);
        baseSignatureCache.putIfAbsent(key, sig);
        return sig;
    }

    private CachedTemplate getOrBuildBaseTemplate(ItemDefinition def) {
        if (def == null || itemBuilder == null) return null;
        String key = def.getType().name() + ":" + def.getId().toLowerCase(Locale.ROOT);
        CachedTemplate cached = baseTemplateCache.get(key);
        if (cached != null) return cached;
        try {
            ItemStack built = itemBuilder.build(def, 1, null, 0L, def.getEnchantments(), false, null);
            if (built != null && !built.getType().isAir()) {
                ItemMeta tm = built.getItemMeta();
                Component expectedName = (tm != null && tm.hasDisplayName()) ? tm.displayName() : null;
                CachedTemplate entry = new CachedTemplate(built, expectedName);
                baseTemplateCache.putIfAbsent(key, entry);
                return entry;
            }
        } catch (Throwable ignored) {}
        return null;
    }

    private int getOrComputeDefinitionSyncHash(ItemDefinition def) {
        String key = def.getType().name() + ":" + def.getId().toLowerCase(Locale.ROOT);
        Integer cached = definitionHashCache.get(key);
        if (cached != null) return cached;

        int h = computeDefinitionSyncHash(def);
        definitionHashCache.putIfAbsent(key, h);
        return h;
    }

    private int computeDefinitionSyncHash(ItemDefinition def) {
        int h = 17;
        h = 31 * h + Objects.hash(
                def.getType(), def.getId(), def.getMaterial(), def.getName(),
                def.getLore(), def.getLoreFormat(), def.isUnbreakable(), def.isGlow(),
                def.getItemModel(), def.getStats(), def.getVanillaAttributes(), def.getCustomStats(),
                def.getEnchantments(), def.isHideEnchantments(), def.isHideAttributes(), def.isHideUnbreakable(),
                def.getItemTier(), def.getItemSet(), def.getGemSockets(),
                def.getTrimMaterial(), def.getTrimPattern(), def.isHideArmorTrim(),
                def.getDyeColor(), def.isHideDye(), def.getEquipmentModel(), def.getEquipmentSlot(),
                def.getVersion(), def.getExpired()
        );

        // Preservation flags affect how existing items are synchronized
        h = 31 * h + Objects.hash(
                config.isPreserveEnchantments(), config.isPreserveGems(), config.isPreserveDurability(),
                config.isPreserveDisplayName(), config.isPreserveLore(), config.isPreserveTier(),
                config.isPreserveCustomModelData(), config.isPreservePDC(), config.isPreserveAttributes()
        );

        DynamicLoreEngine loreEngine = itemBuilder != null ? itemBuilder.getLoreEngine() : null;
        if (loreEngine != null) {
            try {
                List<String> baseLore = loreEngine.buildLore(def, null, 0L, def.getEnchantments(), false, null);
                h = 31 * h + Objects.hashCode(baseLore);
                if (def.getItemSet() != null && !def.getItemSet().isEmpty()) {
                    List<String> activeSetLore = loreEngine.buildLore(def, null, 0L, def.getEnchantments(), true, null);
                    h = 31 * h + Objects.hashCode(activeSetLore);
                }

                LoreFormat fmt = loreEngine.getFormat(def);
                if (fmt != null) {
                    h = 31 * h + Objects.hash(
                            fmt.getId(), fmt.getFormat(), fmt.getBar(),
                            fmt.getEnchantmentFormat(), fmt.getEnchantmentCurseFormat(),
                            fmt.getEnchantmentSymbol(), fmt.getEnchantmentLevelStyle(), fmt.getEnchantmentOrder(),
                            fmt.getEnchantmentCustomNames(),
                            fmt.getSocketEmpty(), fmt.getSocketFilled()
                    );
                }

                LoreFormat globalFmt = loreEngine.getGlobalEnchantmentFormat();
                if (globalFmt != null) {
                    h = 31 * h + Objects.hash(
                            globalFmt.getEnchantmentFormat(), globalFmt.getEnchantmentCurseFormat(),
                            globalFmt.getEnchantmentSymbol(), globalFmt.getEnchantmentLevelStyle(),
                            globalFmt.getEnchantmentOrder(), globalFmt.getEnchantmentCustomNames()
                    );
                }

                Plugin plugin = loreEngine.getPlugin();
                if (plugin != null && plugin.getConfig() != null) {
                    h = 31 * h + Boolean.hashCode(plugin.getConfig().getBoolean("enchantments.hide-vanilla-tooltip", true));
                    h = 31 * h + Boolean.hashCode(plugin.getConfig().getBoolean("enchantments.auto-sync-lore", true));
                }
            } catch (Throwable ignored) {}
        }

        return h == 0 ? 1 : h;
    }

    private int computeItemSyncHash(int defHash, String socketsRaw, String oldTier, String oldSetState,
                                    Map<Enchantment, Integer> activeEnchants) {
        int h = defHash;
        h = 31 * h + Objects.hashCode(config.isPreserveGems() ? socketsRaw : null);
        h = 31 * h + Objects.hashCode(config.isPreserveTier() ? oldTier : null);
        h = 31 * h + Objects.hashCode(oldSetState);
        if (activeEnchants != null && !activeEnchants.isEmpty()) {
            int enchHash = 0;
            for (Map.Entry<Enchantment, Integer> entry : activeEnchants.entrySet()) {
                String k = entry.getKey() != null ? entry.getKey().getKey().toString() : "";
                int v = entry.getValue() != null ? entry.getValue() : 0;
                enchHash += k.hashCode() * 31 + v;
            }
            h = 31 * h + enchHash;
        }
        return h == 0 ? 1 : h;
    }

    /**
     * Updates an array of ItemStacks in place, returning true if any item was updated.
     */
    public boolean updateItemArray(ItemStack[] items, AtomicInteger failCounter) {
        return updateItemArrayCount(items, failCounter) > 0;
    }

    /**
     * Updates an array of ItemStacks in place, returning the number of items successfully updated.
     */
    public int updateItemArrayCount(ItemStack[] items, AtomicInteger failCounter) {
        if (items == null) return 0;
        int count = 0;
        for (int i = 0; i < items.length; i++) {
            ItemStack current = items[i];
            if (current == null || current.getType().isAir() || !current.hasItemMeta()) continue;
            ItemStack updated = updateItemInternal(current, null, failCounter, null);
            if (updated != current) {
                items[i] = updated;
                count++;
            }
        }
        return count;
    }

    /**
     * Scans and updates RoyItems in an inventory slot-by-slot only where changes occur,
     * returning the number of items successfully updated.
     */
    public int updateInventory(Inventory inventory, AtomicInteger failCounter) {
        if (inventory == null || !config.isEnabled()) return 0;

        ItemStack[] contents = inventory.getContents();
        if (contents == null) return 0;

        int updatedCount = 0;
        for (int i = 0; i < contents.length; i++) {
            ItemStack current = contents[i];
            if (current == null || current.getType().isAir() || !current.hasItemMeta()) continue;
            ItemStack updated = updateItemInternal(current, null, failCounter, null);
            if (updated != current) {
                inventory.setItem(i, updated);
                updatedCount++;
            }
        }
        return updatedCount;
    }

    public int updateInventory(Inventory inventory) {
        return updateInventory(inventory, null);
    }
}
