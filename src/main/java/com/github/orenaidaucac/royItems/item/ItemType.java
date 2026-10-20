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

    public static ItemType fromString(String name) {
        if (name == null) return null;
        try {
            return ItemType.valueOf(name.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            return null;
        }
    }
}
