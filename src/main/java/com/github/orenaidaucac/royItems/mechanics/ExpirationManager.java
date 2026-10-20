package com.github.orenaidaucac.royItems.mechanics;

import com.github.orenaidaucac.royItems.core.platform.PlatformScheduler;
import com.github.orenaidaucac.royItems.core.text.TextUtility;
import com.github.orenaidaucac.royItems.item.ItemPDCManager;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.player.PlayerItemHeldEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;

/**
 * Manages item expiration based on real-time epoch timestamps stored in PDC.
 */
public class ExpirationManager implements Listener {

    private final ItemPDCManager pdcManager;
    private final PlatformScheduler scheduler;

    public ExpirationManager(ItemPDCManager pdcManager, PlatformScheduler scheduler) {
        this.pdcManager = pdcManager;
        this.scheduler = scheduler;
    }

    public void startTask() {
        // Run low-overhead scan every 20 seconds (400 ticks) across online players
        scheduler.runGlobalTimer(() -> {
            long now = System.currentTimeMillis();
            for (Player player : Bukkit.getOnlinePlayers()) {
                scheduler.runEntity(player, () -> checkPlayerInventory(player, now));
            }
        }, 200L, 400L);
    }

    public void checkPlayerInventory(Player player, long now) {
        if (player == null || !player.isValid()) return;
        if (player.isOp() || player.hasPermission("royitems.item.expired.bypass")) return;

        PlayerInventory inv = player.getInventory();
        ItemStack[] contents = inv.getContents();
        boolean modified = false;

        for (int i = 0; i < contents.length; i++) {
            ItemStack item = contents[i];
            if (item != null && isExpired(item, now)) {
                contents[i] = null;
                modified = true;
                notifyExpired(player, item);
            }
        }

        if (modified) {
            inv.setContents(contents);
        }
    }

    public boolean isExpired(ItemStack item, long now) {
        if (item == null || !pdcManager.isRoyItem(item)) return false;
        long expireTime = pdcManager.getExpireTime(item);
        return expireTime > 0 && now >= expireTime;
    }

    private void notifyExpired(Player player, ItemStack item) {
        Location loc = player.getLocation().add(0, 1, 0);
        player.getWorld().spawnParticle(Particle.SMOKE, loc, 20, 0.3, 0.3, 0.3, 0.05);
        player.playSound(loc, Sound.BLOCK_FIRE_EXTINGUISH, 0.7f, 1.0f);
        player.sendMessage(TextUtility.formatMessage("&cAn item in your inventory has expired and dissolved into dust!"));
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onInventoryClick(InventoryClickEvent event) {
        if (event.getWhoClicked() instanceof Player player) {
            if (player.isOp() || player.hasPermission("royitems.item.expired.bypass")) return;

            long now = System.currentTimeMillis();
            ItemStack current = event.getCurrentItem();
            if (current != null && isExpired(current, now)) {
                event.setCurrentItem(null);
                event.setCancelled(true);
                notifyExpired(player, current);
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onHeld(PlayerItemHeldEvent event) {
        Player player = event.getPlayer();
        if (player.isOp() || player.hasPermission("royitems.item.expired.bypass")) return;

        ItemStack item = player.getInventory().getItem(event.getNewSlot());
        long now = System.currentTimeMillis();
        if (item != null && isExpired(item, now)) {
            player.getInventory().setItem(event.getNewSlot(), null);
            notifyExpired(player, item);
        }
    }
}
