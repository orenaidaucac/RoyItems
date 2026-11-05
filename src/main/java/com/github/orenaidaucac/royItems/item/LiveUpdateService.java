package com.github.orenaidaucac.royItems.item;

import com.github.orenaidaucac.royItems.core.platform.PlatformScheduler;
import com.github.orenaidaucac.royItems.core.text.TextUtility;
import com.github.orenaidaucac.royItems.stat.StatManager;
import org.bukkit.Bukkit;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.inventory.meta.Damageable;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;

/**
 * LiveUpdate Service: Automatically synchronizes and updates custom items across
 * player inventories, equipment, off-hand, and ender chests when administrators
 * reload the plugin or save modifications in the Ingame GUI Editor.
 *
 * Supports independent preservation flags (enchantments, gems, lore, PDC, attributes, etc.)
 * configured via config.yml under 'live-update'.
 */
public class LiveUpdateService {

    private final ItemRegistry registry;
    private final ItemPDCManager pdcManager;
    private final ItemBuilder itemBuilder;
    private final PlatformScheduler scheduler;
    private final LiveUpdateConfig config = new LiveUpdateConfig();
    private StatManager statManager;

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
    }

    public boolean isEnabled() {
        return config.isEnabled();
    }

    public boolean isAutoUpdateOnReload() {
        return config.isAutoUpdateOnReload();
    }

    /**
     * Updates all online players' inventories, equipment, and ender chests safely on their
     * designated execution threads, invoking the callback with real-time statistics upon completion.
     */
    public void updateAllOnlinePlayers(Consumer<LiveUpdateReport> callback) {
        if (!config.isEnabled()) {
            if (callback != null) {
                callback.accept(new LiveUpdateReport(0, 0, 0, 0));
            }
            return;
        }

        Collection<? extends Player> players = Bukkit.getOnlinePlayers();
        if (players.isEmpty()) {
            if (callback != null) {
                callback.accept(new LiveUpdateReport(0, 0, 0, 0));
            }
            return;
        }

        AtomicInteger invTotal = new AtomicInteger(0);
        AtomicInteger ecTotal = new AtomicInteger(0);
        AtomicInteger failTotal = new AtomicInteger(0);
        AtomicInteger pending = new AtomicInteger(players.size());

        for (Player player : players) {
            scheduler.runEntity(player, () -> {
                try {
                    if (player != null && player.isValid()) {
                        int invCount = 0;
                        int ecCount = 0;
                        PlayerInventory inv = player.getInventory();

                        // 1. Storage contents (0-35)
                        if (config.isContainerInventory()) {
                            ItemStack[] storage = inv.getStorageContents();
                            int c = updateItemArrayCount(storage, failTotal);
                            if (c > 0) {
                                inv.setStorageContents(storage);
                                invCount += c;
                            }
                        }

                        // 2. Armor slots (helmet, chestplate, leggings, boots)
                        if (config.isContainerArmor()) {
                            ItemStack[] armor = inv.getArmorContents();
                            int c = updateItemArrayCount(armor, failTotal);
                            if (c > 0) {
                                inv.setArmorContents(armor);
                                invCount += c;
                            }
                        }

                        // 3. Off-hand slot
                        if (config.isContainerOffhand()) {
                            ItemStack offhand = inv.getItemInOffHand();
                            if (offhand != null && !offhand.getType().isAir() && pdcManager.isRoyItem(offhand)) {
                                ItemStack updated = updateItem(offhand, failTotal);
                                if (updated != offhand) {
                                    inv.setItemInOffHand(updated);
                                    invCount += 1;
                                }
                            }
                        }

                        // 4. Ender chest
                        if (config.isContainerEnderChest()) {
                            Inventory ec = player.getEnderChest();
                            ItemStack[] ecContents = ec.getContents();
                            int c = updateItemArrayCount(ecContents, failTotal);
                            if (c > 0) {
                                ec.setContents(ecContents);
                                ecCount += c;
                            }
                        }

                        invTotal.addAndGet(invCount);
                        ecTotal.addAndGet(ecCount);

                        // 5. Recalculate stats for online player immediately
                        if (statManager != null) {
                            statManager.recalculate(player);
                        }
                    }
                } catch (Throwable t) {
                    failTotal.incrementAndGet();
                } finally {
                    if (pending.decrementAndGet() == 0) {
                        if (callback != null) {
                            LiveUpdateReport report = new LiveUpdateReport(
                                    invTotal.get(), ecTotal.get(), failTotal.get(), players.size());
                            callback.accept(report);
                        }
                    }
                }
            });
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

        PlayerInventory inv = player.getInventory();
        boolean changedEquipment = false;

        // 1. Storage contents (0-35)
        if (config.isContainerInventory()) {
            ItemStack[] storage = inv.getStorageContents();
            if (updateItemArray(storage, null)) {
                inv.setStorageContents(storage);
            }
        }

        // 2. Armor slots
        if (config.isContainerArmor()) {
            ItemStack[] armor = inv.getArmorContents();
            if (updateItemArray(armor, null)) {
                inv.setArmorContents(armor);
                changedEquipment = true;
            }
        }

        // 3. Off-hand slot
        if (config.isContainerOffhand()) {
            ItemStack offhand = inv.getItemInOffHand();
            if (offhand != null && !offhand.getType().isAir() && pdcManager.isRoyItem(offhand)) {
                ItemStack updated = updateItem(offhand, null);
                if (updated != offhand) {
                    inv.setItemInOffHand(updated);
                    changedEquipment = true;
                }
            }
        }

        // 4. Ender chest
        if (config.isContainerEnderChest()) {
            Inventory ec = player.getEnderChest();
            ItemStack[] ecContents = ec.getContents();
            if (updateItemArray(ecContents, null)) {
                ec.setContents(ecContents);
            }
        }

        // 5. Recalculate stats immediately
        if (statManager != null) {
            statManager.recalculate(player);
        }
    }

    /**
     * Updates an individual ItemStack according to definition and configured preservation rules.
     */
    public ItemStack updateItem(ItemStack current, AtomicInteger failCounter) {
        if (current == null || !pdcManager.isRoyItem(current)) return current;

        ItemType type = pdcManager.getType(current);
        String id = pdcManager.getId(current);
        ItemDefinition def = registry.get(type, id);

        if (def == null) return current; // Definition removed or not loaded

        try {
            ItemMeta curMeta = current.getItemMeta();
            if (curMeta == null) return current;

            // 1. Slotted Sockets
            String socketsRaw = pdcManager.getSocketsData(current);
            List<String> sockets = null;
            if (config.isPreserveGems() && socketsRaw != null && !socketsRaw.isEmpty()) {
                sockets = ItemBuilder.deserializeSockets(socketsRaw);
            }

            // 2. Expiration Time (always preserved for an existing active item)
            long expire = pdcManager.getExpireTime(current);

            // 3. Active Enchantments resolution (Vanilla + EcoEnchants)
            Map<Enchantment, Integer> activeEnchants = new java.util.LinkedHashMap<>();
            if (config.isPreserveEnchantments()) {
                activeEnchants.putAll(curMeta.getEnchants());
            }
            if (def.getEnchantments() != null) {
                for (Map.Entry<Enchantment, Integer> e : def.getEnchantments().entrySet()) {
                    activeEnchants.putIfAbsent(e.getKey(), e.getValue());
                }
            }

            // 4. Item Tier
            String oldTier = pdcManager.getTier(current);

            // 5. Build base updated item with active enchantments
            ItemStack updated = itemBuilder.build(def, current.getAmount(), sockets, expire, activeEnchants);
            ItemMeta updMeta = updated.getItemMeta();
            if (updMeta == null) return current;

            // 5. Display Name preservation
            if (config.isPreserveDisplayName() && curMeta.hasDisplayName()) {
                updMeta.displayName(curMeta.displayName());
            }

            // 6. Lore preservation
            if (config.isPreserveLore() && curMeta.hasLore()) {
                updMeta.lore(curMeta.lore());
            }

            // 7. Custom Model Data preservation
            if (config.isPreserveCustomModelData() && curMeta.hasCustomModelData()) {
                updMeta.setCustomModelData(curMeta.getCustomModelData());
            }

            // 8. Custom Attribute preservation (if true, keep old item attributes; if false, keeps fresh definition attributes)
            if (config.isPreserveAttributes() && curMeta.hasAttributeModifiers()) {
                updMeta.setAttributeModifiers(curMeta.getAttributeModifiers());
            }

            // 9. Third-party PDC / custom NBT preservation
            if (config.isPreservePDC()) {
                curMeta.getPersistentDataContainer().copyTo(updMeta.getPersistentDataContainer(), false);
            }

            // 10. Re-tag core RoyItems PDC data to guarantee definition and preserve rules are 100% authoritative
            pdcManager.tagItem(updMeta, def.getType(), def.getId(), def.getVersion(), expire,
                    config.isPreserveGems() ? socketsRaw : null);
            pdcManager.setEnchantHash(updMeta, activeEnchants.hashCode());
            if (config.isPreserveTier() && oldTier != null && !oldTier.isEmpty()) {
                pdcManager.tagTier(updMeta, oldTier);
            } else if (def.getItemTier() != null && !def.getItemTier().isEmpty()) {
                pdcManager.tagTier(updMeta, def.getItemTier());
            }

            updated.setItemMeta(updMeta);

            // 11. Enchantments preservation (merge custom player enchantments)
            if (config.isPreserveEnchantments()) {
                Map<Enchantment, Integer> curEnchants = curMeta.getEnchants();
                if (!curEnchants.isEmpty()) {
                    for (Map.Entry<Enchantment, Integer> entry : curEnchants.entrySet()) {
                        updated.addUnsafeEnchantment(entry.getKey(), entry.getValue());
                    }
                }
            }

            // 12. Durability / Damage preservation
            if (config.isPreserveDurability()) {
                if (curMeta instanceof Damageable curDmg
                        && updated.getItemMeta() instanceof Damageable updDmg) {
                    if (curDmg.hasDamage() && !def.isUnbreakable()) {
                        updDmg.setDamage(curDmg.getDamage());
                        updated.setItemMeta(updDmg);
                    }
                }
            }

            return updated;
        } catch (Throwable t) {
            if (failCounter != null) {
                failCounter.incrementAndGet();
            }
            return current;
        }
    }

    /**
     * Updates an array of ItemStacks in place, returning true if any item was updated.
     */
    public boolean updateItemArray(ItemStack[] items, AtomicInteger failCounter) {
        if (items == null) return false;
        boolean changed = false;
        for (int i = 0; i < items.length; i++) {
            ItemStack current = items[i];
            if (current == null || !pdcManager.isRoyItem(current)) continue;
            ItemStack updated = updateItem(current, failCounter);
            if (updated != current) {
                items[i] = updated;
                changed = true;
            }
        }
        return changed;
    }

    /**
     * Updates an array of ItemStacks in place, returning the number of items successfully updated.
     */
    public int updateItemArrayCount(ItemStack[] items, AtomicInteger failCounter) {
        if (items == null) return 0;
        int count = 0;
        for (int i = 0; i < items.length; i++) {
            ItemStack current = items[i];
            if (current == null || !pdcManager.isRoyItem(current)) continue;
            ItemStack updated = updateItem(current, failCounter);
            if (updated != current) {
                items[i] = updated;
                count++;
            }
        }
        return count;
    }

    /**
     * Scans and updates RoyItems in an inventory, returning the number of items successfully updated.
     */
    public int updateInventory(Inventory inventory, AtomicInteger failCounter) {
        if (inventory == null || !config.isEnabled()) return 0;

        ItemStack[] contents = inventory.getContents();
        int updatedCount = updateItemArrayCount(contents, failCounter);
        if (updatedCount > 0) {
            inventory.setContents(contents);
        }
        return updatedCount;
    }

    public int updateInventory(Inventory inventory) {
        return updateInventory(inventory, null);
    }
}
