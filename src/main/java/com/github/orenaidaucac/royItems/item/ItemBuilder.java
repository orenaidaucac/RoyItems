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
        if (def == null) return new ItemStack(Material.AIR);

        ItemStack item = createBaseItemStack(def.getMaterial());
        item.setAmount(Math.max(1, amount));

        ItemMeta meta = item.getItemMeta();
        if (meta == null) return item;

        // 1. Display Name
        if (def.getName() != null && !def.getName().isEmpty()) {
            meta.displayName(TextUtility.formatItemName(def.getName()));
        }

        // 2. Dynamic Lore
        List<String> rawLore = loreEngine.buildLore(def, slottedSockets, expireTimestamp);
        meta.lore(TextUtility.formatLore(rawLore));

        // 3. Unbreakable
        meta.setUnbreakable(def.isUnbreakable());

        // 4. Glow
        if (def.isGlow()) {
            meta.addEnchant(Enchantment.LUCK_OF_THE_SEA, 1, true);
            meta.addItemFlags(ItemFlag.HIDE_ENCHANTS);
        }

        // 5. Enchantments
        if (def.getEnchantments() != null) {
            for (Map.Entry<Enchantment, Integer> e : def.getEnchantments().entrySet()) {
                meta.addEnchant(e.getKey(), e.getValue(), true);
            }
        }
        if (def.isHideEnchantments()) {
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

        // 9. Attribute Modifiers for Vanilla Attributes
        if (def.isHideAttributes()) {
            meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES);
        } else {
            applyVanillaAttributeModifiers(meta, def);
        }

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
        if (meta != null) {
            try {
                PlayerProfile profile = Bukkit.createProfile(UUID.randomUUID(), null);
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

    private void applyVanillaAttributeModifiers(ItemMeta meta, ItemDefinition def) {
        EquipmentSlotGroup slotGroup = resolveSlotGroup(def.getType());

        for (Map.Entry<StatType, StatModifier> entry : def.getStats().entrySet()) {
            StatType type = entry.getKey();
            StatModifier mod = entry.getValue();
            if (type.hasVanillaAttribute() && mod != null && !mod.isEmpty()) {
                Attribute attr = type.getVanillaAttribute();
                if (Math.abs(mod.getFlat()) > 0.0001) {
                    NamespacedKey key = new NamespacedKey("royitems", type.getKey() + "_flat");
                    meta.addAttributeModifier(attr, new AttributeModifier(key, mod.getFlat(),
                            AttributeModifier.Operation.ADD_NUMBER, slotGroup));
                }
                if (Math.abs(mod.getPercent()) > 0.0001) {
                    NamespacedKey key = new NamespacedKey("royitems", type.getKey() + "_percent");
                    meta.addAttributeModifier(attr, new AttributeModifier(key, mod.getPercent(),
                            AttributeModifier.Operation.ADD_SCALAR, slotGroup));
                }
            }
        }
    }

    private EquipmentSlotGroup resolveSlotGroup(ItemType type) {
        return switch (type) {
            case ARMOR -> EquipmentSlotGroup.ARMOR;
            case OFF_HAND -> EquipmentSlotGroup.OFFHAND;
            case WING -> EquipmentSlotGroup.CHEST;
            default -> EquipmentSlotGroup.MAINHAND;
        };
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
