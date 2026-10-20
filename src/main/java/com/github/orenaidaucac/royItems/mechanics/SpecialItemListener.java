package com.github.orenaidaucac.royItems.mechanics;

import com.github.orenaidaucac.royItems.core.text.TextUtility;
import com.github.orenaidaucac.royItems.integration.IntegrationManager;
import com.github.orenaidaucac.royItems.item.ItemDefinition;
import com.github.orenaidaucac.royItems.item.ItemPDCManager;
import com.github.orenaidaucac.royItems.item.ItemRegistry;
import com.github.orenaidaucac.royItems.item.ItemType;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.potion.PotionEffect;

/**
 * Handles specialized gameplay mechanics for STAFF, VOUCHER, CONSUMABLE, and WING items.
 */
public class SpecialItemListener implements Listener {

    private final ItemPDCManager pdcManager;
    private final ItemRegistry registry;
    private final CooldownManager cooldownManager;
    private final RequirementChecker requirementChecker;
    private final IntegrationManager integrationManager;

    public SpecialItemListener(ItemPDCManager pdcManager, ItemRegistry registry,
                               CooldownManager cooldownManager, RequirementChecker requirementChecker,
                               IntegrationManager integrationManager) {
        this.pdcManager = pdcManager;
        this.registry = registry;
        this.cooldownManager = cooldownManager;
        this.requirementChecker = requirementChecker;
        this.integrationManager = integrationManager;
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onInteract(PlayerInteractEvent event) {
        if (event.getHand() != EquipmentSlot.HAND) return;

        ItemStack item = event.getItem();
        if (item == null || !pdcManager.isRoyItem(item)) return;

        Player player = event.getPlayer();
        ItemType type = pdcManager.getType(item);
        String id = pdcManager.getId(item);
        ItemDefinition def = registry.get(type, id);
        if (def == null) return;

        // Check requirements
        if (!requirementChecker.canUse(player, def)) {
            requirementChecker.sendRequirementFailureMessage(player, def);
            event.setCancelled(true);
            return;
        }

        Action action = event.getAction();
        boolean isRightClick = (action == Action.RIGHT_CLICK_AIR || action == Action.RIGHT_CLICK_BLOCK);

        // 1. VOUCHER Handling
        if (type == ItemType.VOUCHER && isRightClick) {
            handleVoucher(player, item, def);
            event.setCancelled(true);
            return;
        }

        // 2. CONSUMABLE Handling
        if (type == ItemType.CONSUMABLE && isRightClick) {
            handleConsumable(player, item, def);
            event.setCancelled(true);
            return;
        }

        // 3. STAFF Handling
        if (type == ItemType.STAFF && isRightClick) {
            handleStaff(player, def);
            event.setCancelled(true);
            return;
        }
    }

    private void handleVoucher(Player player, ItemStack item, ItemDefinition def) {
        if (cooldownManager.isOnCooldown(player, def.getId())) {
            double remaining = cooldownManager.getRemainingSeconds(player, def.getId());
            player.sendMessage(TextUtility.formatMessage("&cPlease wait &f" + String.format("%.1f", remaining) + "s &cto use this voucher!"));
            return;
        }

        // Consume 1 item
        item.setAmount(item.getAmount() - 1);

        // Sound & Particle
        Location loc = player.getLocation().add(0, 1, 0);
        player.getWorld().spawnParticle(Particle.FIREWORK, loc, 30, 0.5, 0.5, 0.5, 0.1);
        player.playSound(loc, Sound.UI_TOAST_CHALLENGE_COMPLETE, 0.8f, 1.2f);

        // Cooldown
        if (def.getCooldown() > 0) {
            cooldownManager.setCooldown(player, def.getId(), def.getCooldown());
        }

        player.sendMessage(TextUtility.formatMessage("&aYou redeemed &f" +
                (def.getName() != null ? def.getName() : def.getId()) + "&a!"));
    }

    private void handleConsumable(Player player, ItemStack item, ItemDefinition def) {
        if (cooldownManager.isOnCooldown(player, def.getId())) {
            double remaining = cooldownManager.getRemainingSeconds(player, def.getId());
            player.sendMessage(TextUtility.formatMessage("&cPlease wait &f" + String.format("%.1f", remaining) + "s &cto consume this!"));
            return;
        }

        // Apply potion effects
        if (def.getEffects() != null) {
            for (PotionEffectData pData : def.getEffects()) {
                if (pData != null && pData.getType() != null) {
                    player.addPotionEffect(new PotionEffect(pData.getType(), 200, pData.getAmplifier(), false, false, true));
                }
            }
        }

        // Sound & Particle
        Location loc = player.getLocation().add(0, 1, 0);
        player.getWorld().spawnParticle(Particle.HEART, loc, 10, 0.4, 0.4, 0.4, 0.05);
        player.playSound(loc, Sound.ENTITY_GENERIC_EAT, 0.8f, 1.0f);

        // Consume 1 item
        item.setAmount(item.getAmount() - 1);

        if (def.getCooldown() > 0) {
            cooldownManager.setCooldown(player, def.getId(), def.getCooldown());
        }
    }

    private void handleStaff(Player player, ItemDefinition def) {
        if (cooldownManager.isOnCooldown(player, def.getId())) {
            double remaining = cooldownManager.getRemainingSeconds(player, def.getId());
            player.sendMessage(TextUtility.formatMessage("&cStaff is on cooldown! (&f" + String.format("%.1f", remaining) + "s&c)"));
            return;
        }

        // Trigger MythicMobs skill if configured
        boolean castSuccess = false;
        if (integrationManager.hasMythicMobs()) {
            castSuccess = integrationManager.getMythicMobsHook().castSkill(player, def.getId());
        }

        // Default magic particle ray if not MythicMobs skill
        if (!castSuccess) {
            Location eye = player.getEyeLocation();
            for (int i = 1; i <= 15; i++) {
                Location step = eye.clone().add(eye.getDirection().multiply(i));
                player.getWorld().spawnParticle(Particle.WITCH, step, 5, 0.1, 0.1, 0.1, 0.02);
            }
            player.playSound(eye, Sound.ENTITY_ILLUSIONER_CAST_SPELL, 0.8f, 1.4f);
        }

        // Set cooldown
        if (def.getCooldown() > 0) {
            cooldownManager.setCooldown(player, def.getId(), def.getCooldown());
        }
    }
}
