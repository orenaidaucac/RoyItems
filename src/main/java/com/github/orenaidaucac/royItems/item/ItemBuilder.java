package com.github.orenaidaucac.royItems.item;

import com.destroystokyo.paper.profile.PlayerProfile;
import com.destroystokyo.paper.profile.ProfileProperty;
import com.github.orenaidaucac.royItems.core.text.TextUtility;
import com.github.orenaidaucac.royItems.lore.DynamicLoreEngine;
import com.github.orenaidaucac.royItems.stat.StatModifier;
import com.github.orenaidaucac.royItems.stat.StatType;
import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Registry;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeModifier;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.inventory.EquipmentSlotGroup;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ArmorMeta;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.LeatherArmorMeta;
import org.bukkit.inventory.meta.SkullMeta;
import org.bukkit.inventory.meta.trim.ArmorTrim;
import org.bukkit.inventory.meta.trim.TrimMaterial;
import org.bukkit.inventory.meta.trim.TrimPattern;

import java.util.*;

/**
 * Builds physical ItemStacks from ItemDefinitions with material provider support,
 * Data Components, Armor Trims, Enchantments, and PDC tags.
 */
public class ItemBuilder {

    private final ItemPDCManager pdcManager;
    private final DynamicLoreEngine loreEngine;

    public ItemBuilder(ItemPDCManager pdcManager, DynamicLoreEngine loreEngine) {
        this.pdcManager = pdcManager;
        this.loreEngine = loreEngine;
    }

    /**
     * Builds an ItemStack from a definition with default initial state.
     */
    public ItemStack build(ItemDefinition def, int amount) {
        return build(def, amount, null, 0L);
    }

    /**
     * Builds an ItemStack with specific instance state (slotted gems, expiration).
     */
    public ItemStack build(ItemDefinition def, int amount, List<String> slottedSockets, long expireTimestamp) {
        return build(def, amount, slottedSockets, expireTimestamp, null);
    }

    /**
     * Builds an ItemStack with specific instance state (slotted gems, expiration, active enchantments).
     */
    public ItemStack build(ItemDefinition def, int amount, List<String> slottedSockets, long expireTimestamp, Map<Enchantment, Integer> activeEnchants) {
        if (def == null) return new ItemStack(Material.AIR);

        ItemStack item = createBaseItemStack(def.getMaterial());
        item.setAmount(Math.max(1, amount));

        ItemMeta meta = item.getItemMeta();
        if (meta == null) return item;

        // 1. Display Name
        if (def.getName() != null && !def.getName().isEmpty()) {
            meta.displayName(TextUtility.formatItemName(def.getName()));
        }

        // 2. Active Enchantments preparation
        Map<Enchantment, Integer> enchantsToApply = new LinkedHashMap<>();
        if (def.getEnchantments() != null) {
            enchantsToApply.putAll(def.getEnchantments());
        }
        if (activeEnchants != null) {
            enchantsToApply.putAll(activeEnchants);
        }

        // 3. Dynamic Lore
        List<String> rawLore = loreEngine.buildLore(def, slottedSockets, expireTimestamp, enchantsToApply);
        meta.lore(TextUtility.formatLore(rawLore));

        // 4. Unbreakable
        meta.setUnbreakable(def.isUnbreakable());
        if (def.isHideUnbreakable()) {
            meta.addItemFlags(ItemFlag.HIDE_UNBREAKABLE);
        }

        // 5. Apply Enchantments
        for (Map.Entry<Enchantment, Integer> e : enchantsToApply.entrySet()) {
            meta.addEnchant(e.getKey(), e.getValue(), true);
        }

        // Glow
        if (def.isGlow()) {
            meta.addEnchant(Enchantment.LUCK_OF_THE_SEA, 1, true);
            meta.addItemFlags(ItemFlag.HIDE_ENCHANTS);
        }

        // If definition hides enchantments or lore format displays #enchantments#, hide vanilla tooltip
        if (def.isHideEnchantments() || loreEngine.hasEnchantmentsPlaceholder(def)) {
            meta.addItemFlags(ItemFlag.HIDE_ENCHANTS);
        }

        // 6. Item Model & CustomModelData
        if (def.getItemModel() != null && !def.getItemModel().isEmpty()) {
            applyItemModel(meta, def.getItemModel());
        }

        // 7. Armor Trim
        if (meta instanceof ArmorMeta armorMeta) {
            applyArmorTrim(armorMeta, def.getTrimMaterial(), def.getTrimPattern());
            if (def.isHideArmorTrim()) {
                armorMeta.addItemFlags(ItemFlag.HIDE_ARMOR_TRIM);
            }
        }

        // 8. Leather Dye Color
        if (meta instanceof LeatherArmorMeta leatherMeta) {
            if (def.getDyeColor() != null && !def.getDyeColor().isEmpty()) {
                try {
                    String hex = def.getDyeColor().replace("#", "");
                    int rgb = Integer.parseInt(hex, 16);
                    leatherMeta.setColor(Color.fromRGB(rgb));
                } catch (Exception ignored) {}
            }
            if (def.isHideDye()) {
                leatherMeta.addItemFlags(ItemFlag.HIDE_DYE);
            }
        }

        // 9. Suppress Default Material Attributes & Apply Hide Attributes flag
        suppressDefaultMaterialAttributes(meta);
        meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES);

        // 10. Tag PDC
        long finalExpire = expireTimestamp;
        if (finalExpire == 0L && def.getExpired() != null && !def.getExpired().isEmpty()) {
            long durationMillis = parseDurationMillis(def.getExpired());
            if (durationMillis > 0) {
                finalExpire = System.currentTimeMillis() + durationMillis;
            }
        }

        String socketsSerialized = serializeSockets(slottedSockets);
        pdcManager.tagItem(meta, def.getType(), def.getId(), def.getVersion(), finalExpire, socketsSerialized);
        pdcManager.setEnchantHash(meta, enchantsToApply.hashCode());
        if (def.getItemTier() != null && !def.getItemTier().isEmpty()) {
            pdcManager.tagTier(meta, def.getItemTier());
        }

        item.setItemMeta(meta);
        return item;
    }

    /**
     * Resolves material string to ItemStack, supporting Vanilla, Heads, and third-party prefixes.
     */
    private ItemStack createBaseItemStack(String materialString) {
        if (materialString == null || materialString.isEmpty()) {
            return new ItemStack(Material.DIAMOND_SWORD);
        }

        String raw = materialString.trim();

        // 1. HeadDatabase: hdb-<id>
        if (raw.toLowerCase().startsWith("hdb-")) {
            // Placeholder head; if HDB is loaded, Hook will replace it
            return createPlayerHead();
        }

        // 2. Base64 Head: basehead-<value>
        if (raw.toLowerCase().startsWith("basehead-")) {
            String base64 = raw.substring(9);
            return createBase64Head(base64);
        }

        // 3. Custom Providers: nexo:id, itemsadder:id, oraxen:id
        if (raw.contains(":")) {
            // Integration hooks can resolve these. Fallback to stone if provider is absent
            return new ItemStack(Material.PAPER);
        }

        // 4. Vanilla Material
        Material mat = Material.matchMaterial(raw.toUpperCase());
        if (mat == null) {
            mat = Material.DIAMOND_SWORD;
        }
        return new ItemStack(mat);
    }

    private ItemStack createPlayerHead() {
        return new ItemStack(Material.PLAYER_HEAD);
    }

    private ItemStack createBase64Head(String base64) {
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

    private void applyItemModel(ItemMeta meta, String model) {
        try {
            // Check if numeric -> CustomModelData
            int cmd = Integer.parseInt(model);
            meta.setCustomModelData(cmd);
        } catch (NumberFormatException e) {
            // NamespacedKey Item Model in Minecraft 1.21.4+
            try {
                NamespacedKey key = model.contains(":") ? NamespacedKey.fromString(model) : NamespacedKey.minecraft(model);
                if (key != null) {
                    meta.setItemModel(key);
                }
            } catch (Throwable ignored) {}
        }
    }

    private void applyArmorTrim(ArmorMeta meta, String trimMatName, String trimPatternName) {
        if (trimMatName == null || trimPatternName == null) return;
        try {
            TrimMaterial trimMat = Registry.TRIM_MATERIAL.get(NamespacedKey.minecraft(trimMatName.toLowerCase()));
            TrimPattern trimPat = Registry.TRIM_PATTERN.get(NamespacedKey.minecraft(trimPatternName.toLowerCase()));
            if (trimMat != null && trimPat != null) {
                meta.setTrim(new ArmorTrim(trimMat, trimPat));
            }
        } catch (Throwable ignored) {}
    }

    private static final NamespacedKey OVERRIDE_KEY = new NamespacedKey("royitems", "base_override");

    private void suppressDefaultMaterialAttributes(ItemMeta meta) {
        if (meta == null) return;
        try {
            Attribute luck = Attribute.LUCK;
            if (luck != null) {
                meta.addAttributeModifier(luck, new AttributeModifier(OVERRIDE_KEY, 0.0,
                        AttributeModifier.Operation.ADD_NUMBER, EquipmentSlotGroup.ANY));
            }
        } catch (Throwable ignored) {}
    }



    public static long parseDurationMillis(String durationStr) {
        if (durationStr == null || durationStr.isEmpty()) return 0L;
        String[] parts = durationStr.trim().toLowerCase().split("\\s+");
        if (parts.length < 2) return 0L;

        try {
            long amount = Long.parseLong(parts[0]);
            String unit = parts[1];

            if (unit.startsWith("sec")) return amount * 1000L;
            if (unit.startsWith("min")) return amount * 60_000L;
            if (unit.startsWith("hour") || unit.startsWith("hr")) return amount * 3_600_000L;
            if (unit.startsWith("day") || unit.startsWith("d")) return amount * 86_400_000L;
            if (unit.startsWith("week") || unit.startsWith("w")) return amount * 7 * 86_400_000L;
            if (unit.startsWith("month") || unit.startsWith("mo")) return amount * 30 * 86_400_000L;
            if (unit.startsWith("year") || unit.startsWith("y")) return amount * 365 * 86_400_000L;
        } catch (NumberFormatException ignored) {}
        return 0L;
    }

    private String serializeSockets(List<String> sockets) {
        if (sockets == null || sockets.isEmpty()) return "";
        return String.join(";;", sockets);
    }

    public static List<String> deserializeSockets(String raw) {
        if (raw == null || raw.isEmpty()) return new ArrayList<>();
        return new ArrayList<>(Arrays.asList(raw.split(";;")));
    }
}
