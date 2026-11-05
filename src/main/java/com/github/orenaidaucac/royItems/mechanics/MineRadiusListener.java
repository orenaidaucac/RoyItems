package com.github.orenaidaucac.royItems.mechanics;

import com.github.orenaidaucac.royItems.core.platform.PlatformScheduler;
import com.github.orenaidaucac.royItems.integration.AdvancedOreGenHook;
import com.github.orenaidaucac.royItems.integration.ExtraStorageHook;
import com.github.orenaidaucac.royItems.item.ItemBuilder;
import com.github.orenaidaucac.royItems.item.ItemDefinition;
import com.github.orenaidaucac.royItems.item.ItemPDCManager;
import com.github.orenaidaucac.royItems.item.ItemRegistry;
import com.github.orenaidaucac.royItems.item.ItemType;
import com.github.orenaidaucac.royItems.stat.StatManager;
import com.github.orenaidaucac.royItems.stat.StatModifier;
import com.github.orenaidaucac.royItems.stat.StatType;
import org.bukkit.*;
import org.bukkit.block.Block;
import org.bukkit.block.TileState;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.Damageable;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;

/**
 * High-performance Mine Radius (Wide-Area Horizontal Mining) Listener.
 * Exclusively mines on a single horizontal plane (X/Z at identical Y level; never 3D cuboid).
 * Deeply integrates with ExtraStorage to deposit drops directly without entity lag spikes.
 * Compliant with Paper, Spigot, and Folia region threading.
 */
public class MineRadiusListener implements Listener {

    private final ItemPDCManager pdcManager;
    private final ItemRegistry registry;
    private final StatManager statManager;
    private final ExtraStorageHook extraStorageHook;
    private final AdvancedOreGenHook advancedOreGenHook;
    private final PlatformScheduler scheduler;

    // Configuration Settings
    private boolean enabled = true;
    private double activationChance = 100.0;
    private long cooldownMs = 0L;
    private final Set<Material> allowedBlocks = EnumSet.noneOf(Material.class);
    private final Set<Material> allowedTools = EnumSet.noneOf(Material.class);
    private int maxRadiusTier = 5;
    private int maxBlocksPerBreak = 150;
    private boolean extraStorageAutoDeposit = true;
    private boolean extraStorageDropLeftover = true;
    private boolean advancedOreGenAutoRegenerate = true;

    // Per-player cooldown tracker
    private final Map<UUID, Long> lastActivationTimes = new ConcurrentHashMap<>();

    // Recursion guard: ensures secondary radius block breaks do not re-trigger Mine Radius
    private static final ThreadLocal<Boolean> IS_PROCESSING = ThreadLocal.withInitial(() -> false);

    public MineRadiusListener(ItemPDCManager pdcManager, ItemRegistry registry, StatManager statManager,
                              ExtraStorageHook extraStorageHook, AdvancedOreGenHook advancedOreGenHook, PlatformScheduler scheduler) {
        this.pdcManager = pdcManager;
        this.registry = registry;
        this.statManager = statManager;
        this.extraStorageHook = extraStorageHook;
        this.advancedOreGenHook = advancedOreGenHook;
        this.scheduler = scheduler;
    }

    public MineRadiusListener(ItemPDCManager pdcManager, ItemRegistry registry, StatManager statManager,
                              ExtraStorageHook extraStorageHook, PlatformScheduler scheduler) {
        this(pdcManager, registry, statManager, extraStorageHook, null, scheduler);
    }

    public void loadConfig(FileConfiguration config) {
        if (config == null) return;

        this.enabled = config.getBoolean("mine-radius.enabled", true);
        this.activationChance = config.getDouble("mine-radius.activation-chance", 100.0);
        this.cooldownMs = config.getLong("mine-radius.cooldown-ms", 0L);
        this.maxRadiusTier = config.getInt("mine-radius.limits.max-radius-tier", 5);
        this.maxBlocksPerBreak = config.getInt("mine-radius.limits.max-blocks-per-break", 150);

        this.extraStorageAutoDeposit = config.getBoolean("mine-radius.extra-storage.auto-deposit", true);
        this.extraStorageDropLeftover = config.getBoolean("mine-radius.extra-storage.drop-leftover-to-world", true);
        this.advancedOreGenAutoRegenerate = config.getBoolean("mine-radius.advanced-ore-gen.auto-regenerate", true);

        // Load Allowed Blocks Whitelist
        allowedBlocks.clear();
        List<String> blockList = config.getStringList("mine-radius.allowed-blocks");
        if (blockList != null && !blockList.isEmpty()) {
            for (String name : blockList) {
                if (name == null || name.trim().isEmpty()) continue;
                Material mat = Material.matchMaterial(name.trim());
                if (mat != null) {
                    boolean isBlk = true;
                    try {
                        isBlk = mat.isBlock();
                    } catch (Throwable ignored) {}
                    if (isBlk) {
                        allowedBlocks.add(mat);
                    }
                }
            }
        }
        if (allowedBlocks.isEmpty()) {
            populateDefaultBlocks();
        }

        // Load Allowed Tools Whitelist
        allowedTools.clear();
        List<String> toolList = config.getStringList("mine-radius.allowed-tools");
        if (toolList != null && !toolList.isEmpty()) {
            for (String name : toolList) {
                if (name == null || name.trim().isEmpty()) continue;
                Material mat = Material.matchMaterial(name.trim());
                if (mat != null) {
                    boolean isItm = true;
                    try {
                        isItm = mat.isItem();
                    } catch (Throwable ignored) {}
                    if (isItm) {
                        allowedTools.add(mat);
                    }
                }
            }
        }
        if (allowedTools.isEmpty()) {
            populateDefaultTools();
        }
    }

    private void populateDefaultBlocks() {
        for (Material m : Material.values()) {
            try {
                if (!m.isBlock()) continue;
            } catch (Throwable ignored) {}
            String n = m.name();
            if (n.endsWith("_ORE") || n.contains("STONE") || n.contains("DEEPSLATE") || n.equals("ANCIENT_DEBRIS")
                    || n.equals("NETHERRACK") || n.equals("BASALT") || n.equals("BLACKSTONE") || n.equals("END_STONE")
                    || n.equals("TUFF")) {
                allowedBlocks.add(m);
            }
        }
    }

    private void populateDefaultTools() {
        for (Material m : Material.values()) {
            try {
                if (!m.isItem()) continue;
            } catch (Throwable ignored) {}
            String n = m.name();
            if (n.endsWith("_PICKAXE") || n.endsWith("_SHOVEL") || n.endsWith("_AXE") || n.endsWith("_HOE")) {
                allowedTools.add(m);
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onBlockBreak(BlockBreakEvent event) {
        if (!enabled) return;
        if (IS_PROCESSING.get()) return;

        Player player = event.getPlayer();
        if (player == null || !player.isOnline()) return;
        if (player.getGameMode() == GameMode.CREATIVE || player.getGameMode() == GameMode.SPECTATOR) return;

        Block centerBlock = event.getBlock();
        if (centerBlock == null || !allowedBlocks.contains(centerBlock.getType())) return;

        ItemStack tool = player.getInventory().getItemInMainHand();
        if (tool == null || tool.getType().isAir() || !allowedTools.contains(tool.getType())) return;
        if (pdcManager == null || !pdcManager.isRoyItem(tool)) return;

        ItemType type = pdcManager.getType(tool);
        String id = pdcManager.getId(tool);
        ItemDefinition def = (registry != null) ? registry.get(type, id) : null;
        if (def == null) return;

        // Resolve mine-radius stat value from item base stats + slotted gems + cache
        double statVal = resolveMineRadius(tool, def, player);
        if (statVal <= 0.0) return;

        // Check Cooldown
        long now = System.currentTimeMillis();
        if (cooldownMs > 0) {
            Long lastTime = lastActivationTimes.get(player.getUniqueId());
            if (lastTime != null && (now - lastTime) < cooldownMs) {
                return;
            }
        }

        // Check Activation Chance
        if (activationChance < 100.0) {
            double roll = ThreadLocalRandom.current().nextDouble() * 100.0;
            if (roll >= activationChance) {
                return;
            }
        }

        int radius = Math.min(StatType.getMineRadiusBlocks(statVal), maxRadiusTier);
        if (radius <= 0) return;

        if (cooldownMs > 0) {
            lastActivationTimes.put(player.getUniqueId(), now);
        }

        // Execute Horizontal Plane Area Mining (X/Z at identical Y)
        executeHorizontalAreaMining(player, tool, def, centerBlock, radius);
    }

    private double resolveMineRadius(ItemStack tool, ItemDefinition def, Player player) {
        double val = 0.0;
        StatModifier mod = def.getStat(StatType.MINE_RADIUS);
        if (mod != null && mod.getFlat() > 0) {
            val += mod.getFlat();
        }

        // Slotted gems on tool
        if (pdcManager != null && registry != null) {
            String socketsRaw = pdcManager.getSocketsData(tool);
            if (socketsRaw != null && !socketsRaw.isEmpty()) {
                List<String> slotted = ItemBuilder.deserializeSockets(socketsRaw);
                for (String gemId : slotted) {
                    if (gemId == null || gemId.isEmpty()) continue;
                    ItemDefinition gemDef = registry.get(ItemType.GEM_STONE, gemId);
                    if (gemDef != null && gemDef.getGemStats() != null) {
                        StatModifier gemMod = gemDef.getGemStats().get(StatType.MINE_RADIUS);
                        if (gemMod != null && gemMod.getFlat() > 0) {
                            val += gemMod.getFlat();
                        }
                    }
                }
            }
        }

        // Fallback to player stat cache if not defined directly on weapon
        if (val <= 0.0 && statManager != null && player != null) {
            val = statManager.getCache(player).getFlat(StatType.MINE_RADIUS);
        }

        return val;
    }

    private void executeHorizontalAreaMining(Player player, ItemStack tool, ItemDefinition def, Block centerBlock, int radius) {
        World world = centerBlock.getWorld();
        Location centerLoc = centerBlock.getLocation();
        int y = centerBlock.getY(); // STRICT HORIZONTAL: only this exact Y level

        int minX = centerBlock.getX() - radius;
        int maxX = centerBlock.getX() + radius;
        int minZ = centerBlock.getZ() - radius;
        int maxZ = centerBlock.getZ() + radius;

        List<ItemStack> collectedDrops = new ArrayList<>();
        int totalExp = 0;
        int processedBlocks = 0;

        // Tool durability tracking
        boolean isUnbreakable = def.isUnbreakable() || (tool.getItemMeta() != null && tool.getItemMeta().isUnbreakable());
        int unbreakingLevel = tool.getEnchantmentLevel(Enchantment.UNBREAKING);
        int maxDurability = tool.getType().getMaxDurability();
        int currentDamage = (tool.getItemMeta() instanceof Damageable d) ? d.getDamage() : 0;
        int durabilityLoss = 0;
        boolean toolBroken = false;

        for (int x = minX; x <= maxX; x++) {
            for (int z = minZ; z <= maxZ; z++) {
                // Skip center block (already handled by the original event)
                if (x == centerBlock.getX() && z == centerBlock.getZ()) {
                    continue;
                }

                if (processedBlocks >= maxBlocksPerBreak) {
                    break;
                }

                Location loc = new Location(world, x, y, z);

                // Folia Region Thread Safety Check: skip blocks outside the current region thread
                if (scheduler != null && scheduler.isFolia()) {
                    try {
                        if (!Bukkit.isOwnedByCurrentRegion(loc)) {
                            continue;
                        }
                    } catch (Throwable ignored) {}
                }

                Block target = world.getBlockAt(x, y, z);
                if (target.getType().isAir() || !allowedBlocks.contains(target.getType())) {
                    continue;
                }

                // Security: Don't break TileEntities (chests, furnaces, spawner blocks)
                if (target.getState() instanceof TileState) {
                    continue;
                }

                // Protection check (WorldGuard, GriefPrevention, Towny, Lands, SkyBlock)
                BlockBreakEvent breakEvent = new BlockBreakEvent(target, player);
                IS_PROCESSING.set(true);
                try {
                    Bukkit.getPluginManager().callEvent(breakEvent);
                } finally {
                    IS_PROCESSING.set(false);
                }

                if (breakEvent.isCancelled()) {
                    continue; // Skip protected block safely
                }

                // Collect drops (Paper automatically applies tool Fortune and Silk Touch)
                Collection<ItemStack> blockDrops = target.getDrops(tool, player);
                if (blockDrops != null && !blockDrops.isEmpty()) {
                    for (ItemStack drop : blockDrops) {
                        if (drop != null && !drop.getType().isAir() && drop.getAmount() > 0) {
                            collectedDrops.add(drop.clone());
                        }
                    }
                }

                // Collect EXP
                int exp = breakEvent.getExpToDrop();
                if (exp > 0) {
                    totalExp += exp;
                }

                // Break or Regenerate block
                // For AdvancedOreGen generators: regenerate in-place to keep fence/water generators fully intact
                boolean regenerated = false;
                if (advancedOreGenAutoRegenerate && advancedOreGenHook != null && advancedOreGenHook.isAvailable()
                        && advancedOreGenHook.isOreGenerator(target)) {
                    regenerated = advancedOreGenHook.regenerate(target, player);
                }

                if (!regenerated) {
                    target.setType(Material.AIR, true);
                }
                processedBlocks++;

                // Simulate Durability with Unbreaking
                if (!isUnbreakable && maxDurability > 0) {
                    boolean applyDamage = (unbreakingLevel <= 0) || (ThreadLocalRandom.current().nextInt(unbreakingLevel + 1) == 0);
                    if (applyDamage) {
                        durabilityLoss++;
                        if (currentDamage + durabilityLoss >= maxDurability) {
                            toolBroken = true;
                            break;
                        }
                    }
                }
            }

            if (toolBroken || processedBlocks >= maxBlocksPerBreak) {
                break;
            }
        }

        // 1. Durability Update (Single write for maximum performance)
        if (!isUnbreakable && maxDurability > 0) {
            if (toolBroken) {
                player.getInventory().setItemInMainHand(null);
                player.playSound(player.getLocation(), Sound.ENTITY_ITEM_BREAK, 1.0f, 1.0f);
            } else if (durabilityLoss > 0 && tool.getItemMeta() instanceof Damageable damageable) {
                damageable.setDamage(currentDamage + durabilityLoss);
                tool.setItemMeta(damageable);
            }
        }

        // 2. Deposit Drops via ExtraStorage or drop to world
        depositOrDrop(player, centerLoc, collectedDrops);

        // 3. Award accumulated EXP
        if (totalExp > 0) {
            player.giveExp(totalExp);
        }
    }

    /**
     * Efficiently stores collected drops into ExtraStorage, or falls back to dropping into the world.
     * Aggregates identical material stacks to minimize storage calls and completely eliminate item entity lag.
     */
    public void depositOrDrop(Player player, Location dropLoc, List<ItemStack> drops) {
        if (drops == null || drops.isEmpty()) return;

        boolean canUseStorage = extraStorageAutoDeposit && extraStorageHook != null && extraStorageHook.isAvailable();

        // High-speed aggregation by Material for vanilla items to minimize ExtraStorage API transactions
        Map<Material, Integer> vanillaAggregated = new EnumMap<>(Material.class);
        List<ItemStack> customItems = new ArrayList<>();

        for (ItemStack item : drops) {
            if (item == null || item.getType().isAir() || item.getAmount() <= 0) continue;
            if (item.hasItemMeta()) {
                customItems.add(item);
            } else {
                vanillaAggregated.merge(item.getType(), item.getAmount(), Integer::sum);
            }
        }

        World world = dropLoc.getWorld();

        // 1. Process aggregated Vanilla items
        for (Map.Entry<Material, Integer> entry : vanillaAggregated.entrySet()) {
            Material mat = entry.getKey();
            int totalAmount = entry.getValue();

            int remaining = totalAmount;
            if (canUseStorage) {
                ItemStack tempStack = new ItemStack(mat, totalAmount);
                int stored = extraStorageHook.store(player, tempStack);
                remaining -= stored;
            }

            if (remaining > 0 && extraStorageDropLeftover && world != null) {
                int maxStack = mat.getMaxStackSize();
                while (remaining > 0) {
                    int spawnAmount = Math.min(remaining, maxStack);
                    world.dropItemNaturally(dropLoc, new ItemStack(mat, spawnAmount));
                    remaining -= spawnAmount;
                }
            }
        }

        // 2. Process Custom / NBT items
        for (ItemStack custom : customItems) {
            int remaining = custom.getAmount();
            if (canUseStorage) {
                int stored = extraStorageHook.store(player, custom);
                remaining -= stored;
            }

            if (remaining > 0 && extraStorageDropLeftover && world != null) {
                ItemStack leftover = custom.clone();
                leftover.setAmount(remaining);
                world.dropItemNaturally(dropLoc, leftover);
            }
        }
    }

    public boolean isEnabled() { return enabled; }
    public double getActivationChance() { return activationChance; }
    public long getCooldownMs() { return cooldownMs; }
    public Set<Material> getAllowedBlocks() { return Collections.unmodifiableSet(allowedBlocks); }
    public Set<Material> getAllowedTools() { return Collections.unmodifiableSet(allowedTools); }
    public boolean isBlockAllowed(Material mat) { return mat != null && allowedBlocks.contains(mat); }
    public boolean isToolAllowed(Material mat) { return mat != null && allowedTools.contains(mat); }
    public int getMaxRadiusTier() { return maxRadiusTier; }
    public int getMaxBlocksPerBreak() { return maxBlocksPerBreak; }
    public boolean isExtraStorageAutoDeposit() { return extraStorageAutoDeposit; }
    public boolean isExtraStorageDropLeftover() { return extraStorageDropLeftover; }
    public boolean isAdvancedOreGenAutoRegenerate() { return advancedOreGenAutoRegenerate; }
    public AdvancedOreGenHook getAdvancedOreGenHook() { return advancedOreGenHook; }
}
