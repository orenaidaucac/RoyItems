package com.github.orenaidaucac.royItems.voucher;

import com.github.orenaidaucac.royItems.core.message.MessageManager;
import com.github.orenaidaucac.royItems.core.platform.PlatformScheduler;
import com.github.orenaidaucac.royItems.core.text.TextUtility;
import com.github.orenaidaucac.royItems.integration.EcoEnchantsHook;
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
 * (Vault/VaultUnlocked, PlayerPoints, ExcellentEconomy), animated Title Gacha rolling for random currency,
 * and professional 3-phase animated Title Gacha for EcoEnchants random book rewards.
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

    // Disconnect & crash safety for pending EcoEnchants rewards
    private static class PendingEcoReward {
        final UUID playerUuid;
        final ItemStack book;
        final ItemDefinition def;
        final EcoEnchantsHook.EcoEnchantCandidate winner;
        final int level;
        volatile PlatformScheduler.TaskHandle task;

        PendingEcoReward(UUID playerUuid, ItemStack book, ItemDefinition def,
                         EcoEnchantsHook.EcoEnchantCandidate winner, int level) {
            this.playerUuid = playerUuid;
            this.book = book;
            this.def = def;
            this.winner = winner;
            this.level = level;
        }
    }
    private final Map<UUID, PendingEcoReward> pendingEcoRewards = new ConcurrentHashMap<>();

    // Same-tick / multi-packet anti-dupe debounce guard (e.g. HAND + OFF_HAND / InteractEntity + InteractAtEntity)
    private static final long VOUCHER_USE_DEBOUNCE_MS = 120L;
    private final Map<UUID, Long> lastVoucherUseMillis = new ConcurrentHashMap<>();
    private volatile com.github.orenaidaucac.royItems.item.ItemPDCManager pdcManager;

    // Cache of configured currency display metadata
    private final Map<String, CurrencyDisplayInfo> currencyDisplayMap = new ConcurrentHashMap<>();

    // Currency Gacha title animation settings
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

    // EcoEnchants Gacha title animation settings (3-phase: Rolling -> Reveal -> Reward)
    private boolean ecoGachaEnabled = true;
    private int ecoDurationTicks = 30;
    private int ecoIntervalTicks = 2;
    private int ecoFadeInTicks = 5;
    private int ecoStayTicks = 50;
    private int ecoFadeOutTicks = 15;
    private String ecoSoundRollName = "BLOCK_NOTE_BLOCK_PLING";
    private float ecoSoundRollPitchMin = 1.0f;
    private float ecoSoundRollPitchMax = 1.8f;
    private String ecoSoundRevealName = "UI_TOAST_CHALLENGE_COMPLETE";
    private float ecoSoundRevealVolume = 1.0f;
    private float ecoSoundRevealPitch = 1.2f;
    private String ecoParticleName = "FIREWORK";
    private int ecoParticleCount = 35;
    private String ecoTitleRolling = "<gold><bold>✦ ECOENCHANTS GACHA ✦</bold></gold>";
    private String ecoSubtitleRolling = "<gray>Rolling: </gray>#enchantment# <yellow>#level#</yellow> <dark_gray>(#rarity#)</dark_gray>";
    private String ecoTitleReveal = "<green><bold>★ ENCHANTMENT UNLOCKED ★</bold></green>";
    private String ecoSubtitleReveal = "#enchantment# <yellow>#level#</yellow> <dark_gray>•</dark_gray> #rarity#";

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

        // 1. Currency Gacha Settings
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

        // 2. EcoEnchants Gacha Settings
        ConfigurationSection ecoSec = config.getConfigurationSection("voucher.ecoenchants-gacha");
        if (ecoSec != null) {
            this.ecoGachaEnabled = ecoSec.getBoolean("enabled", true);
            this.ecoDurationTicks = Math.max(10, ecoSec.getInt("duration-ticks", 30));
            this.ecoIntervalTicks = Math.max(1, ecoSec.getInt("interval-ticks", 2));
            this.ecoFadeInTicks = Math.max(0, ecoSec.getInt("times.fade-in-ticks", 5));
            this.ecoStayTicks = Math.max(10, ecoSec.getInt("times.stay-ticks", 50));
            this.ecoFadeOutTicks = Math.max(0, ecoSec.getInt("times.fade-out-ticks", 15));
            this.ecoSoundRollName = ecoSec.getString("sound-roll", "BLOCK_NOTE_BLOCK_PLING");
            this.ecoSoundRollPitchMin = (float) ecoSec.getDouble("sound-roll-pitch-min", 1.0);
            this.ecoSoundRollPitchMax = (float) ecoSec.getDouble("sound-roll-pitch-max", 1.8);
            this.ecoSoundRevealName = ecoSec.getString("sound-reveal", "UI_TOAST_CHALLENGE_COMPLETE");
            this.ecoSoundRevealVolume = (float) ecoSec.getDouble("sound-reveal-volume", 1.0);
            this.ecoSoundRevealPitch = (float) ecoSec.getDouble("sound-reveal-pitch", 1.2);
            this.ecoParticleName = ecoSec.getString("particle", "FIREWORK");
            this.ecoParticleCount = ecoSec.getInt("particle-count", 35);
            this.ecoTitleRolling = ecoSec.getString("title-rolling", "<gold><bold>✦ ECOENCHANTS GACHA ✦</bold></gold>");
            this.ecoSubtitleRolling = ecoSec.getString("subtitle-rolling", "<gray>Rolling: </gray>#enchantment# <yellow>#level#</yellow> <dark_gray>(#rarity#)</dark_gray>");
            this.ecoTitleReveal = ecoSec.getString("title-reveal", "<green><bold>★ ENCHANTMENT UNLOCKED ★</bold></green>");
            this.ecoSubtitleReveal = ecoSec.getString("subtitle-reveal", "#enchantment# <yellow>#level#</yellow> <dark_gray>•</dark_gray> #rarity#");
        }

        // 3. Currencies mapping
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

        // 0. Same-tick / multi-packet debounce guard (blocks HAND+OFF_HAND / Entity+AtEntity double fires)
        UUID uuid = player.getUniqueId();
        long now = System.currentTimeMillis();
        Long previousUse = lastVoucherUseMillis.get(uuid);
        if (previousUse != null && (now - previousUse) < VOUCHER_USE_DEBOUNCE_MS) {
            return;
        }
        lastVoucherUseMillis.put(uuid, now);

        // 1. Anti-spam / concurrent rolling check
        if (activeGachaPlayers.contains(uuid)) {
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

        // 3. Currency validation (if configured)
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

        // 4. EcoEnchants validation & upfront deterministic reward selection
        EcoEnchantReward ecoReward = def.getEcoEnchantReward();
        EcoEnchantsHook.EcoEnchantCandidate chosenCandidate = null;
        int chosenLevel = 1;
        ItemStack winningBook = null;
        List<EcoEnchantsHook.EcoEnchantCandidate> candidates = null;

        if (ecoReward != null) {
            if (integrationManager == null || !integrationManager.hasEcoEnchants()) {
                messageManager.send(player, "voucher-ecoenchants-unavailable",
                        "&cEcoEnchants is not currently installed or available on this server.");
                return;
            }

            EcoEnchantsHook hook = integrationManager.getEcoEnchantsHook();
            candidates = hook.getCandidatesByFilter(ecoReward.getRarity(), ecoReward.getType(), ecoReward.getMinLevel());
            if (candidates == null || candidates.isEmpty()) {
                messageManager.send(player, "voucher-ecoenchants-no-enchantments",
                        "&cNo matching EcoEnchants enchantments found for this voucher configuration.");
                return;
            }

            // Upfront deterministic winner selection: outcome cannot be altered or duped during rolling
            chosenCandidate = candidates.get(ThreadLocalRandom.current().nextInt(candidates.size()));
            int maxPossible = Math.min(ecoReward.getMaxLevel(), chosenCandidate.getMaxLevel());
            chosenLevel = (maxPossible <= ecoReward.getMinLevel())
                    ? ecoReward.getMinLevel()
                    : ThreadLocalRandom.current().nextInt(ecoReward.getMinLevel(), maxPossible + 1);

            winningBook = hook.createEnchantedBook(chosenCandidate.getEnchantment(), chosenLevel);
            if (winningBook == null) {
                messageManager.send(player, "voucher-invalid-config", "&cFailed to create enchanted book reward.");
                return;
            }
        }

        // 5. Atomic live-hand verification & safe item consumption
        EquipmentSlot activeHand = (hand == EquipmentSlot.OFF_HAND) ? EquipmentSlot.OFF_HAND : EquipmentSlot.HAND;
        ItemStack liveItem = player.getInventory().getItem(activeHand);
        if (liveItem == null || liveItem.getType().isAir() || liveItem.getAmount() <= 0) {
            return;
        }
        if (pdcManager != null) {
            if (!pdcManager.isRoyItem(liveItem)
                    || pdcManager.getType(liveItem) != com.github.orenaidaucac.royItems.item.ItemType.VOUCHER) {
                return;
            }
            String liveId = pdcManager.getId(liveItem);
            if (liveId == null || !liveId.equalsIgnoreCase(def.getId())) {
                return;
            }
        }

        ItemStack refundBackup = liveItem.clone();
        refundBackup.setAmount(1);

        int remainingAmount = liveItem.getAmount() - 1;
        if (remainingAmount <= 0) {
            player.getInventory().setItem(activeHand, null);
        } else {
            liveItem.setAmount(remainingAmount);
            player.getInventory().setItem(activeHand, liveItem);
        }
        player.updateInventory();

        // 6. Calculate reward amount & deposit currency atomically (if currency reward present)
        double rewardAmount = 0.0;
        CurrencyDisplayInfo cInfo = null;

        if (reward != null) {
            rewardAmount = reward.calculateReward();
            cInfo = getCurrencyInfo(reward.getType());

            boolean depositSuccess = depositCurrency(player, reward.getType(), rewardAmount);
            if (!depositSuccess) {
                // Transaction failed! Refund exact voucher clone immediately to protect player
                refundItem(player, refundBackup, activeHand);
                messageManager.send(player, "voucher-transaction-failed", "&cFailed to deposit reward. The transaction was cancelled safely.");
                return;
            }
        }

        // 7. Apply cooldown only after consumption & currency deposit succeed
        if (def.getCooldown() > 0 && cooldownManager != null) {
            cooldownManager.setCooldown(player, def.getId(), def.getCooldown());
        }

        // 8. Visual / Title feedback & Reward Delivery
        if (winningBook != null) {
            if (ecoGachaEnabled) {
                startEcoEnchantsGachaAnimation(player, def, chosenCandidate, chosenLevel, winningBook, candidates);
            } else {
                deliverEcoEnchantsInstant(player, def, chosenCandidate, chosenLevel, winningBook);
            }
        } else {
            boolean isRandomGacha = (reward != null && reward.isRandom() && gachaEnabled);
            if (isRandomGacha) {
                startGachaAnimation(player, def, reward, rewardAmount, cInfo);
            } else {
                finishVoucherInstant(player, def, rewardAmount, cInfo);
            }
        }

        // 9. Execute configured commands safely with placeholder substitution
        executeCommands(player, def.getCommands(), rewardAmount, cInfo);
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
     * Starts animated Title Gacha rolling for random range currency voucher rewards.
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

                Duration stayDuration = Duration.ofMillis(intervalTicks * 50L + 100L);
                player.showTitle(Title.title(cTitle, cSub, Title.Times.times(Duration.ZERO, stayDuration, Duration.ofMillis(50))));

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
     * Starts animated 3-phase Title Gacha for EcoEnchants random book rewards (Rolling -> Reveal -> Reward).
     */
    private void startEcoEnchantsGachaAnimation(Player player, ItemDefinition def,
                                               EcoEnchantsHook.EcoEnchantCandidate winner,
                                               int finalLevel, ItemStack book,
                                               List<EcoEnchantsHook.EcoEnchantCandidate> candidates) {
        UUID uuid = player.getUniqueId();
        activeGachaPlayers.add(uuid);

        PendingEcoReward pending = new PendingEcoReward(uuid, book, def, winner, finalLevel);
        pendingEcoRewards.put(uuid, pending);

        final int totalRolls = Math.max(1, ecoDurationTicks / ecoIntervalTicks);
        final int[] rollsRemaining = new int[]{totalRolls};
        final Sound rollSound = parseSound(ecoSoundRollName, Sound.BLOCK_NOTE_BLOCK_PLING);
        final Sound revealSound = parseSound(ecoSoundRevealName, Sound.UI_TOAST_CHALLENGE_COMPLETE);
        final Particle particle = parseParticle(ecoParticleName, Particle.FIREWORK);

        PlatformScheduler.TaskHandle task = scheduler.runEntityTimer(player, handle -> {
            if (!player.isOnline() || !activeGachaPlayers.contains(uuid)) {
                if (handle != null) handle.cancel();
                activeGachaPlayers.remove(uuid);
                PendingEcoReward removed = pendingEcoRewards.remove(uuid);
                if (removed != null && removed.book != null && player.isOnline()) {
                    giveItemSafely(player, removed.book);
                }
                return;
            }

            rollsRemaining[0]--;

            if (rollsRemaining[0] > 0) {
                // Phase 1: Rolling step - cycle through random valid candidate enchantments
                EcoEnchantsHook.EcoEnchantCandidate preview = (candidates != null && !candidates.isEmpty())
                        ? candidates.get(ThreadLocalRandom.current().nextInt(candidates.size()))
                        : winner;
                int previewLevel = Math.max(1, ThreadLocalRandom.current().nextInt(1, preview.getMaxLevel() + 1));
                String romanLevel = EcoEnchantsHook.toRoman(previewLevel);

                String rollTitle = ecoTitleRolling
                        .replace("#enchantment#", preview.getRawDisplayName())
                        .replace("%enchantment%", preview.getRawDisplayName())
                        .replace("#level#", romanLevel)
                        .replace("%level%", romanLevel)
                        .replace("#rarity#", preview.getRarityDisplayName())
                        .replace("%rarity%", preview.getRarityDisplayName())
                        .replace("#rarity-id#", preview.getRarityId())
                        .replace("#type#", preview.getTypeId());

                String rollSub = ecoSubtitleRolling
                        .replace("#enchantment#", preview.getRawDisplayName())
                        .replace("%enchantment%", preview.getRawDisplayName())
                        .replace("#level#", romanLevel)
                        .replace("%level%", romanLevel)
                        .replace("#rarity#", preview.getRarityDisplayName())
                        .replace("%rarity%", preview.getRarityDisplayName())
                        .replace("#rarity-id#", preview.getRarityId())
                        .replace("#type#", preview.getTypeId());

                Component cTitle = TextUtility.format(rollTitle, false);
                Component cSub = TextUtility.format(rollSub, false);

                // Anti-flicker: stay slightly exceeds interval
                Duration stayDuration = Duration.ofMillis(ecoIntervalTicks * 50L + 100L);
                player.showTitle(Title.title(cTitle, cSub, Title.Times.times(Duration.ZERO, stayDuration, Duration.ofMillis(50))));

                // Play rolling sound with dynamic pitch
                float pitch = (float) (ecoSoundRollPitchMin + (Math.random() * (ecoSoundRollPitchMax - ecoSoundRollPitchMin)));
                player.playSound(player.getLocation(), rollSound, 0.7f, pitch);

            } else {
                // Phase 2: Reveal & Phase 3: Reward
                if (handle != null) handle.cancel();
                activeGachaPlayers.remove(uuid);
                PendingEcoReward removed = pendingEcoRewards.remove(uuid);

                String romanFinal = EcoEnchantsHook.toRoman(finalLevel);

                String finTitle = ecoTitleReveal
                        .replace("#enchantment#", winner.getRawDisplayName())
                        .replace("%enchantment%", winner.getRawDisplayName())
                        .replace("#level#", romanFinal)
                        .replace("%level%", romanFinal)
                        .replace("#raw-level#", String.valueOf(finalLevel))
                        .replace("#rarity#", winner.getRarityDisplayName())
                        .replace("%rarity%", winner.getRarityDisplayName())
                        .replace("#rarity-id#", winner.getRarityId())
                        .replace("#type#", winner.getTypeId());

                String finSub = ecoSubtitleReveal
                        .replace("#enchantment#", winner.getRawDisplayName())
                        .replace("%enchantment%", winner.getRawDisplayName())
                        .replace("#level#", romanFinal)
                        .replace("%level%", romanFinal)
                        .replace("#raw-level#", String.valueOf(finalLevel))
                        .replace("#rarity#", winner.getRarityDisplayName())
                        .replace("%rarity%", winner.getRarityDisplayName())
                        .replace("#rarity-id#", winner.getRarityId())
                        .replace("#type#", winner.getTypeId());

                Component cTitle = TextUtility.format(finTitle, false);
                Component cSub = TextUtility.format(finSub, false);

                Title.Times times = Title.Times.times(
                        Duration.ofMillis(ecoFadeInTicks * 50L),
                        Duration.ofMillis(ecoStayTicks * 50L),
                        Duration.ofMillis(ecoFadeOutTicks * 50L)
                );
                player.showTitle(Title.title(cTitle, cSub, times));

                // Sound & particle burst
                Location loc = player.getLocation().add(0, 1.2, 0);
                player.playSound(loc, revealSound, ecoSoundRevealVolume, ecoSoundRevealPitch);
                try {
                    player.getWorld().spawnParticle(particle, loc, ecoParticleCount, 0.5, 0.5, 0.5, 0.08);
                } catch (Throwable ignored) {}

                // Phase 3: Safe Delivery
                ItemStack toDeliver = (removed != null && removed.book != null) ? removed.book : book;
                giveItemSafely(player, toDeliver);

                // Send success message to chat
                sendEcoSuccessMessage(player, def, winner, finalLevel);
            }
        }, 0L, ecoIntervalTicks);

        pending.task = task;
    }

    /**
     * Instant reward completion for EcoEnchants vouchers when animation is disabled.
     */
    private void deliverEcoEnchantsInstant(Player player, ItemDefinition def,
                                           EcoEnchantsHook.EcoEnchantCandidate winner,
                                           int finalLevel, ItemStack book) {
        Location loc = player.getLocation().add(0, 1, 0);
        Sound sound = parseSound(ecoSoundRevealName, Sound.UI_TOAST_CHALLENGE_COMPLETE);
        Particle particle = parseParticle(ecoParticleName, Particle.FIREWORK);

        player.playSound(loc, sound, ecoSoundRevealVolume, ecoSoundRevealPitch);
        try {
            player.getWorld().spawnParticle(particle, loc, ecoParticleCount, 0.5, 0.5, 0.5, 0.1);
        } catch (Throwable ignored) {}

        giveItemSafely(player, book);
        sendEcoSuccessMessage(player, def, winner, finalLevel);
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

    private void sendEcoSuccessMessage(Player player, ItemDefinition def,
                                       EcoEnchantsHook.EcoEnchantCandidate winner, int level) {
        String itemName = (def.getName() != null && !def.getName().isBlank()) ? def.getName() : def.getId();
        Map<String, String> placeholders = new HashMap<>();
        placeholders.put("item", itemName);
        placeholders.put("enchantment", winner.getRawDisplayName());
        placeholders.put("level", EcoEnchantsHook.toRoman(level));
        placeholders.put("raw_level", String.valueOf(level));
        placeholders.put("rarity", winner.getRarityDisplayName());
        placeholders.put("rarity_id", winner.getRarityId());
        placeholders.put("type", winner.getTypeId());

        messageManager.send(player, "voucher-ecoenchants-reward",
                "&aYou redeemed &e#item# &aand received &f#enchantment# <yellow>#level#</yellow> &7(#rarity#)&a!",
                placeholders);
    }

    /**
     * Safely delivers an item to a player's inventory, dropping naturally at player's location if full.
     */
    public void giveItemSafely(Player player, ItemStack item) {
        if (player == null || item == null || item.getType().isAir()) return;
        HashMap<Integer, ItemStack> leftover = player.getInventory().addItem(item);
        if (!leftover.isEmpty()) {
            for (ItemStack drop : leftover.values()) {
                player.getWorld().dropItemNaturally(player.getLocation(), drop);
            }
            messageManager.send(player, "voucher-inventory-full-dropped",
                    "&eYour inventory was full, so your reward was dropped at your feet!");
        }
        player.updateInventory();
    }

    /**
     * Safely refunds 1 voucher back into player's hand/inventory if transaction fails.
     */
    private void refundItem(Player player, ItemStack refundBackup, EquipmentSlot hand) {
        if (player == null || refundBackup == null || refundBackup.getType().isAir()) return;
        try {
            ItemStack toReturn = refundBackup.clone();
            toReturn.setAmount(1);
            EquipmentSlot activeHand = (hand == EquipmentSlot.OFF_HAND) ? EquipmentSlot.OFF_HAND : EquipmentSlot.HAND;
            ItemStack current = player.getInventory().getItem(activeHand);

            if (current == null || current.getType().isAir()) {
                player.getInventory().setItem(activeHand, toReturn);
            } else if (current.isSimilar(toReturn) && current.getAmount() < current.getMaxStackSize()) {
                current.setAmount(current.getAmount() + 1);
                player.getInventory().setItem(activeHand, current);
            } else {
                player.getInventory().addItem(toReturn);
            }
            player.updateInventory();
        } catch (Throwable ignored) {}
    }

    public void setPdcManager(com.github.orenaidaucac.royItems.item.ItemPDCManager pdcManager) {
        this.pdcManager = pdcManager;
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
        UUID uuid = event.getPlayer().getUniqueId();
        PendingEcoReward pending = pendingEcoRewards.remove(uuid);
        if (pending != null) {
            if (pending.task != null) pending.task.cancel();
            if (pending.book != null) {
                giveItemSafely(event.getPlayer(), pending.book);
            }
        }
        activeGachaPlayers.remove(uuid);
        lastVoucherUseMillis.remove(uuid);
    }

    public boolean isPlayerRolling(UUID uuid) {
        return activeGachaPlayers.contains(uuid);
    }

    public void clearPlayer(UUID uuid) {
        activeGachaPlayers.remove(uuid);
        lastVoucherUseMillis.remove(uuid);
        PendingEcoReward pending = pendingEcoRewards.remove(uuid);
        if (pending != null && pending.task != null) {
            pending.task.cancel();
        }
    }

    public void cleanup() {
        for (PendingEcoReward pending : pendingEcoRewards.values()) {
            if (pending.task != null) pending.task.cancel();
            Player p = Bukkit.getPlayer(pending.playerUuid);
            if (p != null && p.isOnline() && pending.book != null) {
                giveItemSafely(p, pending.book);
            }
        }
        pendingEcoRewards.clear();
        activeGachaPlayers.clear();
        lastVoucherUseMillis.clear();
        currencyDisplayMap.clear();
    }
}
