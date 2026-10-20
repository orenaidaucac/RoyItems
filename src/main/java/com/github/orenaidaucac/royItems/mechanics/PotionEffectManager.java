package com.github.orenaidaucac.royItems.mechanics;

import com.github.orenaidaucac.royItems.core.platform.PlatformScheduler;
import com.github.orenaidaucac.royItems.item.ItemDefinition;
import com.github.orenaidaucac.royItems.item.ItemPDCManager;
import com.github.orenaidaucac.royItems.item.ItemRegistry;
import com.github.orenaidaucac.royItems.item.ItemType;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.potion.PotionEffect;

/**
 * Manages periodic application of potion effects from equipped items and inventory talismans.
 */
public class PotionEffectManager {

    private final ItemRegistry registry;
    private final ItemPDCManager pdcManager;
    private final PlatformScheduler scheduler;

    public PotionEffectManager(ItemRegistry registry, ItemPDCManager pdcManager, PlatformScheduler scheduler) {
        this.registry = registry;
        this.pdcManager = pdcManager;
        this.scheduler = scheduler;
    }

    public void startTask() {
        // Run every 3 seconds (60 ticks)
        scheduler.runGlobalTimer(() -> {
            for (Player player : Bukkit.getOnlinePlayers()) {
                scheduler.runEntity(player, () -> applyEffectsToPlayer(player));
            }
        }, 60L, 60L);
    }

    private void applyEffectsToPlayer(Player player) {
        if (player == null || !player.isValid()) return;
        PlayerInventory inv = player.getInventory();

        // 1. Main Hand
        checkAndApply(player, inv.getItemInMainHand(), "MAIN_HAND");

        // 2. Off Hand
        checkAndApply(player, inv.getItemInOffHand(), "OFF_HAND");

        // 3. Armor slots
        checkAndApply(player, inv.getHelmet(), "HEAD");
        checkAndApply(player, inv.getChestplate(), "CHEST");
        checkAndApply(player, inv.getLeggings(), "LEGS");
        checkAndApply(player, inv.getBoots(), "FEET");

        // 4. Inventory storage for INV slot
        for (ItemStack item : inv.getStorageContents()) {
            checkAndApply(player, item, "INV");
        }
    }

    private void checkAndApply(Player player, ItemStack item, String expectedSlot) {
        if (item == null || !pdcManager.isRoyItem(item)) return;

        ItemType type = pdcManager.getType(item);
        String id = pdcManager.getId(item);
        ItemDefinition def = registry.get(type, id);
        if (def == null || def.getEffects() == null || def.getEffects().isEmpty()) return;

        for (PotionEffectData effectData : def.getEffects()) {
            if (effectData == null || effectData.getType() == null) continue;
            if (effectData.getSlot() == null || effectData.getSlot().equalsIgnoreCase(expectedSlot)
                    || (expectedSlot.equalsIgnoreCase("INV") && effectData.getSlot().equalsIgnoreCase("INV"))) {
                // Apply potion effect with 100 ticks (5 seconds) duration
                PotionEffect effect = new PotionEffect(effectData.getType(), 100, effectData.getAmplifier(), false, false, true);
                player.addPotionEffect(effect);
            }
        }
    }
}
