package com.github.orenaidaucac.royItems.core.update;

import com.github.orenaidaucac.royItems.RoyItems;
import com.github.orenaidaucac.royItems.core.platform.PlatformScheduler;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;

/**
 * Listens for administrator joins and delivers friendly, professional update notifications.
 * Restricts notifications exclusively to server operators and players with 'royitems.admin'.
 */
public class UpdateListener implements Listener {

    private final RoyItems plugin;
    private final UpdateChecker updateChecker;
    private final PlatformScheduler scheduler;

    public UpdateListener(RoyItems plugin, UpdateChecker updateChecker, PlatformScheduler scheduler) {
        this.plugin = plugin;
        this.updateChecker = updateChecker;
        this.scheduler = scheduler;
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();

        // Strictly notify ONLY Administrators and Server Operators
        if (!player.hasPermission("royitems.admin") && !player.isOp()) {
            return;
        }

        UpdateChecker checker = this.updateChecker != null ? this.updateChecker : plugin.getUpdateChecker();
        if (checker == null || !checker.isEnabled() || !checker.isNotifyAdminsOnJoin()) {
            return;
        }

        if (checker.isUpdateAvailable()) {
            // Schedule notification ~2.5 seconds (50 ticks) after join for optimal user experience
            scheduler.runEntityDelayed(player, () -> {
                if (player.isOnline() && checker.isUpdateAvailable()) {
                    plugin.getMessageManager().sendUpdateNotification(
                            player,
                            checker.getCurrentVersion(),
                            checker.getLatestVersion(),
                            checker.getDownloadUrl()
                    );
                }
            }, 50L);
        }
    }
}
