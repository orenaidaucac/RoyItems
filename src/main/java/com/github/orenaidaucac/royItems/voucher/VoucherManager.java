package com.github.orenaidaucac.royItems.voucher;

import com.github.orenaidaucac.royItems.core.message.MessageManager;
import com.github.orenaidaucac.royItems.core.platform.PlatformScheduler;
import com.github.orenaidaucac.royItems.core.text.TextUtility;
import com.github.orenaidaucac.royItems.integration.IntegrationManager;
import com.github.orenaidaucac.royItems.item.ItemDefinition;
import com.github.orenaidaucac.royItems.mechanics.CooldownManager;
import com.github.orenaidaucac.royItems.mechanics.RequirementChecker;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import net.kyori.adventure.title.Title;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.Plugin;

import java.time.Duration;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;

/**
 * High-performance, anti-dupe, atomic Voucher Manager.
 * Handles command executions with placeholders, multi-currency reward deposits
 * (Vault/VaultUnlocked, PlayerPoints, ExcellentEconomy), and animated Title Gacha rolling for random rewards.
 */
public class VoucherManager implements Listener {

    private final Plugin plugin;
    private final PlatformScheduler scheduler;
    private final CooldownManager cooldownManager;
    private final RequirementChecker requirementChecker;
    private final IntegrationManager integrationManager;
    private final MessageManager messageManager;

    // Concurrency control: players currently rolling a random gacha voucher
    private final Set<UUID> activeGachaPlayers = ConcurrentHashMap.newKeySet();

    // Cache of configured currency display metadata
    private final Map<String, CurrencyDisplayInfo> currencyDisplayMap = new ConcurrentHashMap<>();

    // Gacha title animation settings
    private boolean gachaEnabled = true;
    private int durationTicks = 30; // 1.5 seconds default
    private int intervalTicks = 2;  // 10 rolls/sec default
    private String soundRollName = "BLOCK_NOTE_BLOCK_PLING";
    private float soundRollPitchMin = 1.0f;
    private float soundRollPitchMax = 1.8f;
    private String soundFinishName = "UI_TOAST_CHALLENGE_COMPLETE";
    private float soundFinishVolume = 1.0f;
    private float soundFinishPitch = 1.2f;
    private String particleName = "FIREWORK";
    private int particleCount = 35;
    private String titleRolling = "<gold><bold>GACHA ROLLING...</bold></gold>";
    private String subtitleRolling = "<yellow>#symbol##amount# <gray>(#currency#)</gray></yellow>";
    private String titleFinish = "<green><bold>REWARD UNLOCKED!</bold></green>";
    private String subtitleFinish = "<yellow>+ #symbol##amount# <gold>#currency#</gold></yellow>";

    public VoucherManager(Plugin plugin, PlatformScheduler scheduler,
                          CooldownManager cooldownManager, RequirementChecker requirementChecker,
                          IntegrationManager integrationManager, MessageManager messageManager) {
        this.plugin = plugin;
        this.scheduler = scheduler;
        this.cooldownManager = cooldownManager;
        this.requirementChecker = requirementChecker;
        this.integrationManager = integrationManager;
        this.messageManager = messageManager;

        loadConfig(plugin.getConfig());
    }

    /**
     * Loads voucher gacha and currency mapping configuration.
     */
    public void loadConfig(FileConfiguration config) {
        if (config == null) return;

        // 1. Voucher Gacha Settings
        ConfigurationSection gachaSec = config.getConfigurationSection("voucher.gacha");
        if (gachaSec != null) {
            this.gachaEnabled = gachaSec.getBoolean("enabled", true);
            this.durationTicks = Math.max(10, gachaSec.getInt("duration-ticks", 30));
            this.intervalTicks = Math.max(1, gachaSec.getInt("interval-ticks", 2));
            this.soundRollName = gachaSec.getString("sound-roll", "BLOCK_NOTE_BLOCK_PLING");
            this.soundRollPitchMin = (float) gachaSec.getDouble("sound-roll-pitch-min", 1.0);
            this.soundRollPitchMax = (float) gachaSec.getDouble("sound-roll-pitch-max", 1.8);
            this.soundFinishName = gachaSec.getString("sound-finish", "UI_TOAST_CHALLENGE_COMPLETE");
            this.soundFinishVolume = (float) gachaSec.getDouble("sound-finish-volume", 1.0);
            this.soundFinishPitch = (float) gachaSec.getDouble("sound-finish-pitch", 1.2);
            this.particleName = gachaSec.getString("particle", "FIREWORK");
            this.particleCount = gachaSec.getInt("particle-count", 35);
            this.titleRolling = gachaSec.getString("title-rolling", "<gold><bold>GACHA ROLLING...</bold></gold>");
            this.subtitleRolling = gachaSec.getString("subtitle-rolling", "<yellow>#symbol##amount# <gray>(#currency#)</gray></yellow>");
            this.titleFinish = gachaSec.getString("title-finish", "<green><bold>REWARD UNLOCKED!</bold></green>");
            this.subtitleFinish = gachaSec.getString("subtitle-finish", "<yellow>+ #symbol##amount# <gold>#currency#</gold></yellow>");
        }

        // 2. Currencies mapping
        currencyDisplayMap.clear();
        ConfigurationSection currSec = config.getConfigurationSection("currencies");
        if (currSec != null) {
            for (String key : currSec.getKeys(false)) {
                String name = currSec.getString(key + ".name", key);
                String symbol = currSec.getString(key + ".symbol", "");
                String format = currSec.getString(key + ".format", "#,##0.##");
                CurrencyDisplayInfo info = new CurrencyDisplayInfo(key, name, symbol, format);
                currencyDisplayMap.put(key.toLowerCase(Locale.ROOT), info);
            }
        }
    }

    /**
     * Resolves human-friendly display details for a given currency type.
     */
    public CurrencyDisplayInfo getCurrencyInfo(String rawType) {
        if (rawType == null || rawType.isBlank()) {
            return new CurrencyDisplayInfo("VAULT", "Money", "$", "#,##0.##");
        }

        String lower = rawType.toLowerCase(Locale.ROOT);
        String stripped = lower.replaceFirst("^(excellenteconomy_|vault_|economy_)", "");

        // Check configured map first
        if (currencyDisplayMap.containsKey(lower)) {
            return currencyDisplayMap.get(lower);
        }
        if (currencyDisplayMap.containsKey(stripped)) {
            return currencyDisplayMap.get(stripped);
        }

        // Built-in defaults
        if (lower.equals("vault") || lower.equals("money") || lower.equals("eco")) {
            String name = "Money";
            if (integrationManager != null && integrationManager.hasVault()) {
                name = integrationManager.getVaultHook().getCurrencyName();
            }
            return new CurrencyDisplayInfo("VAULT", name, "$", "#,##0.##");
        }

        if (lower.equals("playerpoints") || lower.equals("points")) {
            return new CurrencyDisplayInfo("PLAYERPOINTS", "Points", "✪", "#,##0");
        }

        // ExcellentEconomy fallback inspection
        if (lower.startsWith("excellenteconomy") || lower.startsWith("ee_") || stripped.length() > 0) {
            String eeId = rawType.replaceFirst("(?i)^EXCELLENTECONOMY_", "");
            if (integrationManager != null && integrationManager.hasExcellentEconomy()) {
                String eeName = integrationManager.getExcellentEconomyHook().getCurrencyDisplayName(eeId);
                String eeSymbol = integrationManager.getExcellentEconomyHook().getCurrencySymbol(eeId);
                return new CurrencyDisplayInfo(rawType, eeName, eeSymbol, "#,##0.##");
            }
            return new CurrencyDisplayInfo(rawType, eeId, "", "#,##0.##");
        }

        return new CurrencyDisplayInfo(rawType, rawType, "", "#,##0.##");
    }

    /**
     * Checks if the backing provider for the currency is available and operational.
     */
    public boolean isCurrencyProviderAvailable(String rawType) {
        if (rawType == null) return false;
        String lower = rawType.toLowerCase(Locale.ROOT);

        if (lower.equals("vault") || lower.equals("money") || lower.equals("eco")) {
            return integrationManager != null && integrationManager.hasVault();
        }

        if (lower.equals("playerpoints") || lower.equals("points")) {
            return integrationManager != null && integrationManager.hasPlayerPoints();
        }

        if (lower.startsWith("excellenteconomy") || lower.startsWith("ee_")) {
            if (integrationManager == null || !integrationManager.hasExcellentEconomy()) {
                return false;
            }
            String currId = rawType.replaceFirst("(?i)^EXCELLENTECONOMY_", "");
            return integrationManager.getExcellentEconomyHook().hasCurrency(currId);
        }

        // If not matching prefix, check if ExcellentEconomy registers this currency
        if (integrationManager != null && integrationManager.hasExcellentEconomy()
                && integrationManager.getExcellentEconomyHook().hasCurrency(rawType)) {
            return true;
        }

        return false;
    }

    /**
     * Deposits the rewarded currency safely into the player's account.
     */
    public boolean depositCurrency(Player player, String rawType, double amount) {
        if (player == null || amount <= 0) return true;
        String lower = rawType.toLowerCase(Locale.ROOT);

        if (lower.equals("vault") || lower.equals("money") || lower.equals("eco")) {
            if (integrationManager != null && integrationManager.hasVault()) {
                return integrationManager.getVaultHook().deposit(player, amount);
            }
            return false;
        }

        if (lower.equals("playerpoints") || lower.equals("points")) {
            if (integrationManager != null && integrationManager.hasPlayerPoints()) {
                int points = (int) Math.round(amount);
                return integrationManager.getPlayerPointsHook().givePoints(player, points);
            }
            return false;
        }

        String eeId = rawType.replaceFirst("(?i)^EXCELLENTECONOMY_", "");
        if (integrationManager != null && integrationManager.hasExcellentEconomy()) {
            return integrationManager.getExcellentEconomyHook().deposit(player, eeId, amount);
        }

        return false;
    }

    /**
     * Main entry point when a player right-clicks a VOUCHER item.
     */
    public void handleVoucher(Player player, ItemStack item, ItemDefinition def, EquipmentSlot hand) {
        if (player == null || item == null || def == null) return;

        // 1. Anti-spam / concurrent rolling check
        if (activeGachaPlayers.contains(player.getUniqueId())) {
            messageManager.send(player, "voucher-in-progress", "&cYou are currently rolling a voucher! Please wait for it to finish.");
            return;
        }

        // 2. Cooldown check
        if (cooldownManager != null && cooldownManager.isOnCooldown(player, def.getId())) {
            double rem = cooldownManager.getRemainingSeconds(player, def.getId());
            messageManager.send(player, "voucher-cooldown", "&cPlease wait #time#s to use this voucher!",
                    Map.of("time", String.format(Locale.US, "%.1f", rem)));
            return;
        }

        // 3. Currency validation
        VoucherCurrencyReward reward = def.getCurrencyReward();
        if (reward != null) {
            if (!reward.isValid()) {
                messageManager.send(player, "voucher-invalid-config", "&cThis voucher has an invalid configuration.");
                return;
            }
            if (!isCurrencyProviderAvailable(reward.getType())) {
                CurrencyDisplayInfo info = getCurrencyInfo(reward.getType());
                messageManager.send(player, "voucher-currency-unavailable", "&cThe economy provider for #currency# is currently unavailable.",
                        Map.of("currency", info.getDisplayName()));
                return;
            }
        }

        // 4. Safe item consumption
        if (item.getAmount() <= 0) return;
        item.setAmount(item.getAmount() - 1);

        // 5. Apply cooldown
        if (def.getCooldown() > 0 && cooldownManager != null) {
            cooldownManager.setCooldown(player, def.getId(), def.getCooldown());
        }

        // 6. Calculate reward amount & deposit currency atomically
        double rewardAmount = 0.0;
        CurrencyDisplayInfo cInfo = null;

        if (reward != null) {
            rewardAmount = reward.calculateReward();
            cInfo = getCurrencyInfo(reward.getType());

            boolean depositSuccess = depositCurrency(player, reward.getType(), rewardAmount);
            if (!depositSuccess) {
                // Transaction failed! Refund item immediately to protect player
                refundItem(player, item, hand);
                messageManager.send(player, "voucher-transaction-failed", "&cFailed to deposit reward. The transaction was cancelled safely.");
                return;
            }
        }

        // 7. Execute configured commands safely with placeholder substitution
        executeCommands(player, def.getCommands(), rewardAmount, cInfo);

        // 8. Visual / Title feedback
        boolean isRandomGacha = (reward != null && reward.isRandom() && gachaEnabled);
        if (isRandomGacha) {
            startGachaAnimation(player, def, reward, rewardAmount, cInfo);
        } else {
            finishVoucherInstant(player, def, rewardAmount, cInfo);
        }
    }

    /**
     * Executes configured commands with full placeholder substitution and permission contexts.
     */
    private void executeCommands(Player player, List<String> commands, double amount, CurrencyDisplayInfo cInfo) {
        if (commands == null || commands.isEmpty()) return;

        String formattedAmount = (cInfo != null) ? cInfo.format(amount) : String.valueOf(amount);
        String currencyName = (cInfo != null) ? cInfo.getDisplayName() : "";
        String currencySymbol = (cInfo != null) ? cInfo.getSymbol() : "";
        String playerDisplayName = PlainTextComponentSerializer.plainText().serialize(player.displayName());

        for (String rawCmd : commands) {
            if (rawCmd == null || rawCmd.isBlank()) continue;

            String cmd = rawCmd
                    .replace("%player%", player.getName())
                    .replace("%player_uuid%", player.getUniqueId().toString())
                    .replace("%player_displayname%", playerDisplayName)
                    .replace("%amount%", formattedAmount)
                    .replace("%currency%", currencyName)
                    .replace("%currency_symbol%", currencySymbol);

            if (integrationManager != null && integrationManager.hasPlaceholderAPI()) {
                try {
                    cmd = me.clip.placeholderapi.PlaceholderAPI.setPlaceholders(player, cmd);
                } catch (Throwable ignored) {}
            }

            String trimmed = cmd.trim();
            boolean asPlayer = false;

            if (trimmed.regionMatches(true, 0, "[player]", 0, 8)) {
                asPlayer = true;
                trimmed = trimmed.substring(8).trim();
            } else if (trimmed.regionMatches(true, 0, "player:", 0, 7)) {
                asPlayer = true;
                trimmed = trimmed.substring(7).trim();
            } else if (trimmed.regionMatches(true, 0, "[console]", 0, 9)) {
                trimmed = trimmed.substring(9).trim();
            } else if (trimmed.regionMatches(true, 0, "console:", 0, 8)) {
                trimmed = trimmed.substring(8).trim();
            }

            if (trimmed.startsWith("/")) {
                trimmed = trimmed.substring(1);
            }

            final String finalCommand = trimmed;
            final boolean runAsPlayer = asPlayer;

            // Run command on entity scheduler for thread-safety (Folia / Paper)
            scheduler.runEntity(player, () -> {
                try {
                    if (runAsPlayer) {
                        player.performCommand(finalCommand);
                    } else {
                        Bukkit.dispatchCommand(Bukkit.getConsoleSender(), finalCommand);
                    }
                } catch (Throwable ex) {
                    plugin.getLogger().warning("[Voucher] Failed to dispatch command '" + finalCommand + "': " + ex.getMessage());
                }
            });
        }
    }

    /**
     * Starts animated Title Gacha rolling for random range voucher rewards.
     */
    private void startGachaAnimation(Player player, ItemDefinition def, VoucherCurrencyReward reward,
                                     double finalAmount, CurrencyDisplayInfo cInfo) {
        UUID uuid = player.getUniqueId();
        activeGachaPlayers.add(uuid);

        final int totalRolls = Math.max(1, durationTicks / intervalTicks);
        final int[] rollsRemaining = new int[]{totalRolls};
        final Sound rollSound = parseSound(soundRollName, Sound.BLOCK_NOTE_BLOCK_PLING);
        final Sound finishSound = parseSound(soundFinishName, Sound.UI_TOAST_CHALLENGE_COMPLETE);
        final Particle particle = parseParticle(particleName, Particle.FIREWORK);

        scheduler.runEntityTimer(player, handle -> {
            if (!player.isOnline() || !activeGachaPlayers.contains(uuid)) {
                if (handle != null) handle.cancel();
                activeGachaPlayers.remove(uuid);
                return;
            }

            rollsRemaining[0]--;

            if (rollsRemaining[0] > 0) {
                // Rolling step: show random preview number
                double preview = ThreadLocalRandom.current().nextDouble(reward.getMin(), reward.getMax());
                String formattedPreview = cInfo.format(preview);

                String rollTitle = titleRolling
                        .replace("#currency#", cInfo.getDisplayName())
                        .replace("#symbol#", cInfo.getSymbol())
                        .replace("#amount#", formattedPreview);

                String rollSub = subtitleRolling
                        .replace("#currency#", cInfo.getDisplayName())
                        .replace("#symbol#", cInfo.getSymbol())
                        .replace("#amount#", formattedPreview);

                Component cTitle = TextUtility.format(rollTitle, false);
                Component cSub = TextUtility.format(rollSub, false);

                player.showTitle(Title.title(cTitle, cSub, Title.Times.times(Duration.ZERO, Duration.ofMillis(300), Duration.ofMillis(100))));

                // Play rolling sound with dynamic pitch
                float pitch = (float) (soundRollPitchMin + (Math.random() * (soundRollPitchMax - soundRollPitchMin)));
                player.playSound(player.getLocation(), rollSound, 0.7f, pitch);

            } else {
                // Final step: stop rolling and present granted amount!
                if (handle != null) handle.cancel();
                activeGachaPlayers.remove(uuid);

                String formattedFinal = cInfo.format(finalAmount);

                String finTitle = titleFinish
                        .replace("#currency#", cInfo.getDisplayName())
                        .replace("#symbol#", cInfo.getSymbol())
                        .replace("#amount#", formattedFinal);

                String finSub = subtitleFinish
                        .replace("#currency#", cInfo.getDisplayName())
                        .replace("#symbol#", cInfo.getSymbol())
                        .replace("#amount#", formattedFinal);

                Component cTitle = TextUtility.format(finTitle, false);
                Component cSub = TextUtility.format(finSub, false);

                player.showTitle(Title.title(cTitle, cSub, Title.Times.times(Duration.ofMillis(200), Duration.ofMillis(2500), Duration.ofMillis(500))));

                // Play completion sound & particles
                Location loc = player.getLocation().add(0, 1.2, 0);
                player.playSound(loc, finishSound, soundFinishVolume, soundFinishPitch);
                try {
                    player.getWorld().spawnParticle(particle, loc, particleCount, 0.5, 0.5, 0.5, 0.08);
                } catch (Throwable ignored) {}

                // Send success message to chat
                sendSuccessMessage(player, def, formattedFinal, cInfo);
            }
        }, 0L, intervalTicks);
    }

    /**
     * Instant reward completion for fixed vouchers or command-only vouchers.
     */
    private void finishVoucherInstant(Player player, ItemDefinition def, double amount, CurrencyDisplayInfo cInfo) {
        Location loc = player.getLocation().add(0, 1, 0);
        Sound sound = parseSound(soundFinishName, Sound.UI_TOAST_CHALLENGE_COMPLETE);
        Particle particle = parseParticle(particleName, Particle.FIREWORK);

        player.playSound(loc, sound, soundFinishVolume, soundFinishPitch);
        try {
            player.getWorld().spawnParticle(particle, loc, particleCount, 0.5, 0.5, 0.5, 0.1);
        } catch (Throwable ignored) {}

        if (cInfo != null) {
            sendSuccessMessage(player, def, cInfo.format(amount), cInfo);
        } else {
            String itemName = (def.getName() != null && !def.getName().isBlank()) ? def.getName() : def.getId();
            messageManager.send(player, "voucher-redeemed", "&aYou have redeemed &e#item#&a!",
                    Map.of("item", itemName));
        }
    }

    private void sendSuccessMessage(Player player, ItemDefinition def, String formattedAmount, CurrencyDisplayInfo cInfo) {
        String itemName = (def.getName() != null && !def.getName().isBlank()) ? def.getName() : def.getId();
        Map<String, String> placeholders = new HashMap<>();
        placeholders.put("item", itemName);
        placeholders.put("amount", formattedAmount);
        placeholders.put("currency", cInfo.getDisplayName());
        placeholders.put("symbol", cInfo.getSymbol());

        messageManager.send(player, "voucher-redeemed-currency",
                "&aYou redeemed &e#item# &aand received &e#symbol##amount# #currency#&a!",
                placeholders);
    }

    /**
     * Safely refunds 1 voucher back into player's hand/inventory if transaction fails.
     */
    private void refundItem(Player player, ItemStack item, EquipmentSlot hand) {
        try {
            if (hand == EquipmentSlot.HAND) {
                ItemStack current = player.getInventory().getItemInMainHand();
                if (current.getType() != Material.AIR) {
                    current.setAmount(current.getAmount() + 1);
                } else {
                    item.setAmount(1);
                    player.getInventory().setItemInMainHand(item);
                }
            } else if (hand == EquipmentSlot.OFF_HAND) {
                ItemStack current = player.getInventory().getItemInOffHand();
                if (current.getType() != Material.AIR) {
                    current.setAmount(current.getAmount() + 1);
                } else {
                    item.setAmount(1);
                    player.getInventory().setItemInOffHand(item);
                }
            }
        } catch (Throwable ignored) {}
    }

    private Sound parseSound(String name, Sound fallback) {
        if (name == null || name.isBlank()) return fallback;
        try {
            return Sound.valueOf(name.trim().toUpperCase(Locale.ROOT));
        } catch (Throwable e) {
            return fallback;
        }
    }

    private Particle parseParticle(String name, Particle fallback) {
        if (name == null || name.isBlank()) return fallback;
        try {
            return Particle.valueOf(name.trim().toUpperCase(Locale.ROOT));
        } catch (Throwable e) {
            return fallback;
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onQuit(PlayerQuitEvent event) {
        activeGachaPlayers.remove(event.getPlayer().getUniqueId());
    }

    public boolean isPlayerRolling(UUID uuid) {
        return activeGachaPlayers.contains(uuid);
    }

    public void clearPlayer(UUID uuid) {
        activeGachaPlayers.remove(uuid);
    }

    public void cleanup() {
        activeGachaPlayers.clear();
        currencyDisplayMap.clear();
    }
}
