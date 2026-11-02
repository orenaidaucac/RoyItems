package com.github.orenaidaucac.royItems.dummy;

import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.entity.ProjectileHitEvent;
import org.bukkit.event.entity.ProjectileLaunchEvent;
import org.bukkit.event.player.PlayerAnimationEvent;
import org.bukkit.event.player.PlayerAnimationType;
import org.bukkit.event.player.PlayerInteractEvent;

/**
 * Universal attack and projectile listener for Training Dummies.
 * Ensures only legitimate left-click melee strikes and projectile hits (arrows, tridents)
 * deal damage and calculate DPS, ignoring right-click interactions entirely.
 */
public class DummyAttackListener implements Listener {

    private final DummyManager dummyManager;

    public DummyAttackListener(DummyManager dummyManager) {
        this.dummyManager = dummyManager;
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onAnimation(PlayerAnimationEvent event) {
        if (event.getAnimationType() == PlayerAnimationType.ARM_SWING) {
            handleSwing(event.getPlayer());
        }
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onInteract(PlayerInteractEvent event) {
        // Strictly only process left-click. Right-clicks are ignored completely
        if (event.getAction() == Action.LEFT_CLICK_AIR || event.getAction() == Action.LEFT_CLICK_BLOCK) {
            handleSwing(event.getPlayer());
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onProjectileLaunch(ProjectileLaunchEvent event) {
        Projectile proj = event.getEntity();
        if (proj.getShooter() instanceof Player player) {
            dummyManager.registerActiveProjectile(proj, player);
        }
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onProjectileHit(ProjectileHitEvent event) {
        Projectile proj = event.getEntity();
        if (proj.getShooter() instanceof Player player) {
            dummyManager.handleProjectileHitFallback(proj, player, event);
        }
    }

    private void handleSwing(Player player) {
        if (player == null || !player.isValid()) return;
        dummyManager.checkPlayerAttackRayTrace(player);
    }
}
