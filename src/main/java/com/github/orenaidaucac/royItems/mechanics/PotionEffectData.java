package com.github.orenaidaucac.royItems.mechanics;

import org.bukkit.potion.PotionEffectType;

/**
 * Potion effect configuration for custom items.
 */
public class PotionEffectData {
    private PotionEffectType type;
    private int amplifier = 0; // 0 = Level 1
    private String slot = "MAIN_HAND"; // MAIN_HAND, OFF_HAND, HEAD, CHEST, LEGS, FEET, INV

    public PotionEffectData() {}

    public PotionEffectData(PotionEffectType type, int amplifier, String slot) {
        this.type = type;
        this.amplifier = amplifier;
        this.slot = slot;
    }

    public PotionEffectType getType() { return type; }
    public void setType(PotionEffectType type) { this.type = type; }

    public int getAmplifier() { return amplifier; }
    public void setAmplifier(int amplifier) { this.amplifier = amplifier; }

    public String getSlot() { return slot; }
    public void setSlot(String slot) { this.slot = slot; }
}
