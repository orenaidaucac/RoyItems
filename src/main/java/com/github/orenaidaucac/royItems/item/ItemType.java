package com.github.orenaidaucac.royItems.item;

import org.bukkit.inventory.EquipmentSlot;

/**
 * 14 default MMORPG Item Types for RoyItems.
 */
public enum ItemType {
    SWORD("Melee Weapon", EquipmentSlot.HAND),
    ARMOR("Armor Equipment", null), // can be HEAD, CHEST, LEGS, FEET depending on material
    BOW("Ranged Bow", EquipmentSlot.HAND),
    CROSSBOW("Ranged Crossbow", EquipmentSlot.HAND),
    STAFF("Magic Staff", EquipmentSlot.HAND),
    TOOL("Harvest Tool", EquipmentSlot.HAND),
    GEM_STONE("Gem Stone", null),
    CONSUMABLE("Consumable", EquipmentSlot.HAND),
    MATERIAL("Crafting Material", null),
    OFF_HAND("Shield / Off Hand", EquipmentSlot.OFF_HAND),
    WING("Wing / Glider", EquipmentSlot.CHEST),
    VOUCHER("Voucher Scroll", EquipmentSlot.HAND),
    PET("Companion Pet", null),
    TALISMAN("Passive Talisman", null);

    private final String description;
    private final EquipmentSlot defaultSlot;

    ItemType(String description, EquipmentSlot defaultSlot) {
        this.description = description;
        this.defaultSlot = defaultSlot;
    }

    public String getDescription() {
        return description;
    }

    public EquipmentSlot getDefaultSlot() {
        return defaultSlot;
    }

    public boolean isArmor() {
        return this == ARMOR;
    }

    public boolean isWeapon() {
        return this == SWORD || this == BOW || this == CROSSBOW || this == STAFF;
    }

    public boolean isGem() {
        return this == GEM_STONE;
    }

    public boolean isEquippable() {
        return this == SWORD || this == ARMOR || this == BOW || this == CROSSBOW
                || this == STAFF || this == TOOL || this == OFF_HAND || this == WING;
    }

    public boolean isGear() {
        return isEquippable() || this == TALISMAN;
    }

    /**
     * Checks whether an item of this type is allowed to grant active equipment stats,
     * attributes, set counts, and potion effects in the specified Bukkit equipment slot.
     */
    public boolean isValidEquipmentSlot(EquipmentSlot slot, org.bukkit.Material material) {
        if (slot == null) {
            return false;
        }
        return switch (this) {
            case SWORD, BOW, CROSSBOW, STAFF, TOOL -> slot == EquipmentSlot.HAND;
            case OFF_HAND -> slot == EquipmentSlot.OFF_HAND;
            case WING -> slot == EquipmentSlot.CHEST;
            case ARMOR -> {
                if (slot != EquipmentSlot.HEAD && slot != EquipmentSlot.CHEST
                        && slot != EquipmentSlot.LEGS && slot != EquipmentSlot.FEET) {
                    yield false;
                }
                EquipmentSlot expectedArmorSlot = resolveArmorMaterialSlot(material);
                yield expectedArmorSlot == null || expectedArmorSlot == slot;
            }
            case TALISMAN, GEM_STONE, CONSUMABLE, MATERIAL, VOUCHER, PET -> false;
        };
    }

    /**
     * Checks whether an item of this type grants passive stats and set contributions
     * merely by being present in player storage inventory (without slot duplication).
     */
    public boolean isInventoryPassive() {
        return this == TALISMAN;
    }

    /**
     * Resolves the natural armor slot from a Bukkit Material name without invoking
     * {@code Bukkit.getUnsafe()}, keeping unit tests server-independent.
     *
     * @return {@code HEAD}, {@code CHEST}, {@code LEGS}, {@code FEET}, or {@code null} for custom armor materials.
     */
    public static EquipmentSlot resolveArmorMaterialSlot(org.bukkit.Material material) {
        if (material == null) {
            return null;
        }
        String name = material.name();
        if (name.endsWith("_HELMET") || name.endsWith("_HEAD") || name.endsWith("_SKULL")
                || name.equals("CARVED_PUMPKIN") || name.equals("JACK_O_LANTERN")) {
            return EquipmentSlot.HEAD;
        }
        if (name.endsWith("_CHESTPLATE") || name.equals("ELYTRA")) {
            return EquipmentSlot.CHEST;
        }
        if (name.endsWith("_LEGGINGS")) {
            return EquipmentSlot.LEGS;
        }
        if (name.endsWith("_BOOTS")) {
            return EquipmentSlot.FEET;
        }
        return null;
    }

    public static ItemType fromString(String name) {
        if (name == null) return null;
        try {
            return ItemType.valueOf(name.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            return null;
        }
    }
}
