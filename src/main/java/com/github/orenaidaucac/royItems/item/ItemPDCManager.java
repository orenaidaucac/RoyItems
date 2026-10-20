package com.github.orenaidaucac.royItems.item;

import org.bukkit.NamespacedKey;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.Plugin;

import java.util.UUID;

/**
 * Handles persistent metadata for RoyItems using Bukkit's PersistentDataContainer.
 */
public class ItemPDCManager {

    private final NamespacedKey KEY_TYPE;
    private final NamespacedKey KEY_ID;
    private final NamespacedKey KEY_UUID;
    private final NamespacedKey KEY_VERSION;
    private final NamespacedKey KEY_EXPIRE;
    private final NamespacedKey KEY_SOCKETS;

    public ItemPDCManager(Plugin plugin) {
        this.KEY_TYPE = new NamespacedKey(plugin, "item_type");
        this.KEY_ID = new NamespacedKey(plugin, "item_id");
        this.KEY_UUID = new NamespacedKey(plugin, "item_uuid");
        this.KEY_VERSION = new NamespacedKey(plugin, "item_version");
        this.KEY_EXPIRE = new NamespacedKey(plugin, "item_expire");
        this.KEY_SOCKETS = new NamespacedKey(plugin, "item_sockets");
    }

    public boolean isRoyItem(ItemStack item) {
        if (item == null || !item.hasItemMeta()) return false;
        ItemMeta meta = item.getItemMeta();
        return meta.getPersistentDataContainer().has(KEY_TYPE, PersistentDataType.STRING)
                && meta.getPersistentDataContainer().has(KEY_ID, PersistentDataType.STRING);
    }

    public ItemType getType(ItemStack item) {
        if (!isRoyItem(item)) return null;
        String raw = item.getItemMeta().getPersistentDataContainer().get(KEY_TYPE, PersistentDataType.STRING);
        return ItemType.fromString(raw);
    }

    public String getId(ItemStack item) {
        if (!isRoyItem(item)) return null;
        return item.getItemMeta().getPersistentDataContainer().get(KEY_ID, PersistentDataType.STRING);
    }

    public UUID getUUID(ItemStack item) {
        if (!isRoyItem(item)) return null;
        String raw = item.getItemMeta().getPersistentDataContainer().get(KEY_UUID, PersistentDataType.STRING);
        if (raw == null) return null;
        try {
            return UUID.fromString(raw);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    public int getVersion(ItemStack item) {
        if (!isRoyItem(item)) return 0;
        Integer ver = item.getItemMeta().getPersistentDataContainer().get(KEY_VERSION, PersistentDataType.INTEGER);
        return ver != null ? ver : 0;
    }

    public long getExpireTime(ItemStack item) {
        if (!isRoyItem(item)) return 0L;
        Long exp = item.getItemMeta().getPersistentDataContainer().get(KEY_EXPIRE, PersistentDataType.LONG);
        return exp != null ? exp : 0L;
    }

    public String getSocketsData(ItemStack item) {
        if (!isRoyItem(item)) return null;
        return item.getItemMeta().getPersistentDataContainer().get(KEY_SOCKETS, PersistentDataType.STRING);
    }

    public void tagItem(ItemMeta meta, ItemType type, String id, int version, long expireTimestamp, String socketsData) {
        if (meta == null) return;
        PersistentDataContainer pdc = meta.getPersistentDataContainer();
        pdc.set(KEY_TYPE, PersistentDataType.STRING, type.name());
        pdc.set(KEY_ID, PersistentDataType.STRING, id.toLowerCase());
        pdc.set(KEY_UUID, PersistentDataType.STRING, UUID.randomUUID().toString());
        pdc.set(KEY_VERSION, PersistentDataType.INTEGER, version);
        if (expireTimestamp > 0) {
            pdc.set(KEY_EXPIRE, PersistentDataType.LONG, expireTimestamp);
        }
        if (socketsData != null && !socketsData.isEmpty()) {
            pdc.set(KEY_SOCKETS, PersistentDataType.STRING, socketsData);
        }
    }

    public void updateSockets(ItemStack item, String socketsData) {
        if (!isRoyItem(item)) return;
        ItemMeta meta = item.getItemMeta();
        if (socketsData == null || socketsData.isEmpty()) {
            meta.getPersistentDataContainer().remove(KEY_SOCKETS);
        } else {
            meta.getPersistentDataContainer().set(KEY_SOCKETS, PersistentDataType.STRING, socketsData);
        }
        item.setItemMeta(meta);
    }

    public void updateVersion(ItemStack item, int newVersion) {
        if (!isRoyItem(item)) return;
        ItemMeta meta = item.getItemMeta();
        meta.getPersistentDataContainer().set(KEY_VERSION, PersistentDataType.INTEGER, newVersion);
        item.setItemMeta(meta);
    }
}
