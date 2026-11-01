package com.github.orenaidaucac.royItems.station;

import com.destroystokyo.paper.profile.PlayerProfile;
import com.destroystokyo.paper.profile.ProfileProperty;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.SkullMeta;

import java.lang.reflect.Method;
import java.util.UUID;

/**
 * Universal item provider resolving items from Vanilla, Base64 Heads, HeadDatabase,
 * Nexo, ItemsAdder, and Oraxen.
 */
public final class CustomItemProvider {

    private CustomItemProvider() {}

    /**
     * Resolves a raw material/provider string into an ItemStack.
     */
    public static ItemStack resolve(String materialString) {
        if (materialString == null || materialString.isBlank()) {
            return new ItemStack(Material.STONE);
        }

        String raw = materialString.trim();

        // 1. Base64 Head: basehead-<value>
        if (raw.toLowerCase().startsWith("basehead-")) {
            String base64 = raw.substring(9);
            return createBase64Head(base64);
        }

        // 2. HeadDatabase: hdb-<id>
        if (raw.toLowerCase().startsWith("hdb-")) {
            String headId = raw.substring(4);
            ItemStack hdbItem = getHeadDatabaseItem(headId);
            if (hdbItem != null) return hdbItem;
            return new ItemStack(Material.PLAYER_HEAD);
        }

        // 3. Custom Providers with namespace
        if (raw.contains(":")) {
            String[] parts = raw.split(":", 2);
            String prefix = parts[0].toLowerCase();
            String id = parts[1];

            ItemStack customItem = switch (prefix) {
                case "nexo" -> getNexoItem(id);
                case "itemsadder", "ia" -> getItemsAdderItem(id);
                case "oraxen" -> getOraxenItem(id);
                default -> null;
            };

            if (customItem != null) {
                return customItem;
            }
        }

        // 4. Vanilla Material
        Material mat = Material.matchMaterial(raw.toUpperCase());
        if (mat != null) {
            return new ItemStack(mat);
        }

        return new ItemStack(Material.STONE);
    }

    public static ItemStack createBase64Head(String base64) {
        ItemStack head = new ItemStack(Material.PLAYER_HEAD);
        SkullMeta meta = (SkullMeta) head.getItemMeta();
        if (meta != null && base64 != null && !base64.isBlank()) {
            try {
                // Use deterministic UUID based on texture content to ensure custom heads can stack together
                UUID profileUuid = UUID.nameUUIDFromBytes(base64.getBytes(java.nio.charset.StandardCharsets.UTF_8));
                PlayerProfile profile = Bukkit.createProfile(profileUuid, null);
                profile.setProperty(new ProfileProperty("textures", base64));
                meta.setPlayerProfile(profile);
                head.setItemMeta(meta);
            } catch (Exception ignored) {}
        }
        return head;
    }

    private static ItemStack getHeadDatabaseItem(String headId) {
        if (Bukkit.getPluginManager().getPlugin("HeadDatabase") == null) {
            return null;
        }
        try {
            Class<?> apiClass = Class.forName("me.arcaniax.hdb.api.HeadDatabaseAPI");
            Object api = apiClass.getDeclaredConstructor().newInstance();
            Method getItemMethod = apiClass.getMethod("getItemHead", String.class);
            Object res = getItemMethod.invoke(api, headId);
            if (res instanceof ItemStack item) {
                return item;
            }
        } catch (Throwable ignored) {}
        return null;
    }

    private static ItemStack getNexoItem(String id) {
        if (Bukkit.getPluginManager().getPlugin("Nexo") == null) {
            return null;
        }
        try {
            Class<?> nexoItemsClass = Class.forName("com.nexomc.nexo.api.NexoItems");
            Method itemMethod = nexoItemsClass.getMethod("item", String.class);
            Object itemObj = itemMethod.invoke(null, id);
            if (itemObj != null) {
                Method buildMethod = itemObj.getClass().getMethod("build");
                Object built = buildMethod.invoke(itemObj);
                if (built instanceof ItemStack is) return is;
            }
        } catch (Throwable ignored) {}
        return null;
    }

    private static ItemStack getItemsAdderItem(String id) {
        if (Bukkit.getPluginManager().getPlugin("ItemsAdder") == null) {
            return null;
        }
        try {
            Class<?> customStackClass = Class.forName("dev.lone.itemsadder.api.CustomStack");
            Method getInstanceMethod = customStackClass.getMethod("getInstance", String.class);
            Object customStack = getInstanceMethod.invoke(null, id);
            if (customStack != null) {
                Method getItemStackMethod = customStack.getClass().getMethod("getItemStack");
                Object is = getItemStackMethod.invoke(customStack);
                if (is instanceof ItemStack item) return item;
            }
        } catch (Throwable ignored) {}
        return null;
    }

    private static ItemStack getOraxenItem(String id) {
        if (Bukkit.getPluginManager().getPlugin("Oraxen") == null) {
            return null;
        }
        try {
            Class<?> oraxenItemsClass = Class.forName("io.thury.oraxen.api.OraxenItems");
            Method getItemByIdMethod = oraxenItemsClass.getMethod("getItemById", String.class);
            Object itemObj = getItemByIdMethod.invoke(null, id);
            if (itemObj != null) {
                Method buildMethod = itemObj.getClass().getMethod("build");
                Object built = buildMethod.invoke(itemObj);
                if (built instanceof ItemStack is) return is;
            }
        } catch (Throwable ignored) {}
        return null;
    }
}
