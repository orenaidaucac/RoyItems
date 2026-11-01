package com.github.orenaidaucac.royItems.dummy;

import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerAnimationEvent;
import org.bukkit.event.player.PlayerAnimationType;
import org.bukkit.event.player.PlayerInteractEvent;

/**
 * Universal attack fallback listener for Training Dummies.
 * When players swing their weapon (left-click) at a packet-based dummy,
 * this listener uses a server-side 3D Bounding-Box RayTrace to guarantee
 * 100% reliable attack registration across all clients, mods, and packet configurations.
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
        if (event.getAction() == Action.LEFT_CLICK_AIR || event.getAction() == Action.LEFT_CLICK_BLOCK) {
            handleSwing(event.getPlayer());
        }
    }

    private void handleSwing(Player player) {
        if (player == null || !player.isValid()) return;
        dummyManager.checkPlayerAttackRayTrace(player);
    }
}
