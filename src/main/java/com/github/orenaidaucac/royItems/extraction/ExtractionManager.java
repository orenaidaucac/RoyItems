package com.github.orenaidaucac.royItems.extraction;

import com.github.orenaidaucac.royItems.RoyItems;
import com.github.orenaidaucac.royItems.core.message.MessageManager;
import com.github.orenaidaucac.royItems.integration.EcoEnchantsHook;
import com.github.orenaidaucac.royItems.integration.IntegrationManager;
import com.github.orenaidaucac.royItems.item.ItemDefinition;
import com.github.orenaidaucac.royItems.item.ItemPDCManager;
import com.github.orenaidaucac.royItems.item.ItemRegistry;
import com.github.orenaidaucac.royItems.lore.DynamicLoreEngine;
import dev.triumphteam.gui.guis.Gui;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.EnchantmentStorageMeta;

import java.io.File;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Core management service for the Advanced Enchantment Extraction System.
 * Coordinates GUI sessions, eligibility filtering, atomic item manipulation,
 * enchanted book generation, and safe transaction rollbacks.
 */
public class ExtractionManager {

    private final RoyItems plugin;
    private final ItemRegistry registry;
    private final ItemPDCManager pdcManager;
    private final DynamicLoreEngine loreEngine;
    private final IntegrationManager integrationManager;
    private final MessageManager messageManager;

    private final ExtractionGuiConfig guiConfig = new ExtractionGuiConfig();
    private final Map<UUID, ExtractionSession> activeSessions = new ConcurrentHashMap<>();
    private final Map<UUID, Gui> openGuis = new ConcurrentHashMap<>();
    private final Set<UUID> processingPlayers = ConcurrentHashMap.newKeySet();

    public ExtractionManager(RoyItems plugin, ItemRegistry registry, ItemPDCManager pdcManager,
                             DynamicLoreEngine loreEngine, IntegrationManager integrationManager,
                             MessageManager messageManager) {
        this.plugin = plugin;
        this.registry = registry;
        this.pdcManager = pdcManager;
        this.loreEngine = loreEngine;
        this.integrationManager = integrationManager;
        this.messageManager = messageManager;
        loadConfig();
    }

    public void loadConfig() {
        File file = new File(plugin.getDataFolder(), "gui/remove-enchants-gui.yml");
        if (!file.exists()) {
            try {
                file.getParentFile().mkdirs();
                plugin.saveResource("gui/remove-enchants-gui.yml", false);
            } catch (Exception e) {
                plugin.getLogger().warning("[ExtractionManager] Could not save default gui/remove-enchants-gui.yml: " + e.getMessage());
            }
        }

        if (file.exists()) {
            try {
                FileConfiguration yaml = YamlConfiguration.loadConfiguration(file);
                guiConfig.load(yaml);
            } catch (Exception e) {
                plugin.getLogger().warning("[ExtractionManager] Error loading gui/remove-enchants-gui.yml: " + e.getMessage());
            }
        }
    }

    public ExtractionGuiConfig getGuiConfig() {
        return guiConfig;
    }

    public RoyItems getPlugin() {
        return plugin;
    }

    public ItemPDCManager getPdcManager() {
        return pdcManager;
    }

    public boolean hasPlaceholderAPI() {
        return integrationManager != null && integrationManager.hasPlaceholderAPI();
    }

    /**
     * Attempts to open the Enchantment Extraction GUI for a player dragging a consumable
     * onto an enchanted target equipment piece.
     */
    public boolean openExtractionGui(Player player, ItemStack targetItem, int targetSlot, ItemStack consumableItem) {
        if (player == null || targetItem == null || targetItem.getType().isAir() || consumableItem == null) {
            return false;
        }

        if (!pdcManager.isRoyItem(consumableItem)) {
            return false;
        }

        String consumableId = pdcManager.getId(consumableItem);
        ItemDefinition consumableDef = registry.get(pdcManager.getType(consumableItem), consumableId);
        if (consumableDef == null) {
            return false;
        }

        EnchantExtractionConfig config = consumableDef.getRemoveEnchantsConfig();
        if (config == null || !config.isEnabled()) {
            return false;
        }

        // 1. Target Definition & Whitelist validation
        ItemDefinition targetDef = pdcManager.isRoyItem(targetItem)
                ? registry.get(pdcManager.getType(targetItem), pdcManager.getId(targetItem))
                : null;

        // NEVER allow extraction on another remove-enchants consumable
        if (targetDef != null && targetDef.hasRemoveEnchants()) {
            return false;
        }

        // Validate item-whitelist
        if (!config.isItemWhitelisted(targetItem, targetDef)) {
            messageManager.send(player, "extract-item-not-whitelisted");
            return false;
        }

        // 2. Collect all active enchantments on the target item (excluding cosmetic glow)
        Map<Enchantment, Integer> targetEnchants = new LinkedHashMap<>(targetItem.getEnchantments());
        if (targetItem.getItemMeta() instanceof EnchantmentStorageMeta storageMeta) {
            targetEnchants.putAll(storageMeta.getStoredEnchants());
        }
        filterDummyGlowEnchantments(targetEnchants, targetDef);

        if (targetEnchants.isEmpty()) {
            messageManager.send(player, "extract-no-enchantments");
            return false;
        }

        // 3. Filter eligible enchantments according to the consumable's configuration
        EcoEnchantsHook ecoHook = (integrationManager != null) ? integrationManager.getEcoEnchantsHook() : null;
        List<ExtractionEntry> eligibleEntries = new ArrayList<>();

        for (Map.Entry<Enchantment, Integer> entry : targetEnchants.entrySet()) {
            Enchantment ench = entry.getKey();
            int level = entry.getValue();

            if (config.isEnchantmentEligible(ench, ecoHook)) {
                boolean isEco = (ecoHook != null && ecoHook.isEcoEnchant(ench));
                String name = isEco ? ecoHook.getDisplayName(ench) : DynamicLoreEngine.formatEnchantmentName(ench);
                String roman = DynamicLoreEngine.toRoman(level);
                String rarity = isEco ? ecoHook.getRarityDisplayName(ench) : "Vanilla";
                if (rarity == null || rarity.isBlank()) rarity = "Vanilla";
                String typeName = isEco ? (ecoHook.getTypeId(ench) != null ? ecoHook.getTypeId(ench) : "EcoEnchants") : "Vanilla";

                eligibleEntries.add(new ExtractionEntry(
                        ench, level, isEco, ench.getKey().getKey(), name, roman, rarity, typeName, config.getChance()
                ));
            }
        }

        if (eligibleEntries.isEmpty()) {
            messageManager.send(player, "extract-no-valid-enchantments");
            return false;
        }

        // 3. Register ongoing session and open TriumphGUI
        ExtractionSession session = new ExtractionSession(player.getUniqueId(), targetSlot, targetItem, consumableDef, consumableId);
        activeSessions.put(player.getUniqueId(), session);

        new EnchantmentExtractionGui(this, player, session, eligibleEntries, 1).open();
        return true;
    }

    /**
     * Executes the extraction of a selected enchantment, consuming 1 scroll and returning the book upon success.
     */
    public void processExtraction(Player player, ExtractionSession session, ExtractionEntry entry) {
        if (player == null || session == null || entry == null) return;

        UUID uuid = player.getUniqueId();
        if (!processingPlayers.add(uuid)) {
            messageManager.send(player, "extract-in-progress");
            return;
        }

        try {
            // 1. Verify player is still online and inventory state is consistent
            if (!player.isOnline() || !player.isValid()) {
                return;
            }

            ItemStack currentTarget = player.getInventory().getItem(session.getTargetSlot());
            if (currentTarget == null || currentTarget.getType().isAir()) {
                messageManager.send(player, "extract-item-invalid");
                closeGui(player);
                return;
            }

            // Re-verify target whitelist before extracting
            ItemDefinition targetDef = pdcManager.isRoyItem(currentTarget)
                    ? registry.get(pdcManager.getType(currentTarget), pdcManager.getId(currentTarget))
                    : null;

            EnchantExtractionConfig extractionConfig = session.getConsumableDef().getRemoveEnchantsConfig();
            if (extractionConfig != null && !extractionConfig.isItemWhitelisted(currentTarget, targetDef)) {
                messageManager.send(player, "extract-item-invalid");
                closeGui(player);
                return;
            }

            // Prevent extracting dummy cosmetic glow
            if (isDummyGlow(targetDef, entry.getEnchantment(), entry.getLevel())) {
                messageManager.send(player, "extract-item-invalid");
                closeGui(player);
                return;
            }

            // Verify the enchantment is still on the item
            boolean hasEnch = currentTarget.containsEnchantment(entry.getEnchantment());
            if (!hasEnch && currentTarget.getItemMeta() instanceof EnchantmentStorageMeta storageMeta) {
                hasEnch = storageMeta.hasStoredEnchant(entry.getEnchantment());
            }
            if (!hasEnch) {
                messageManager.send(player, "extract-item-invalid");
                closeGui(player);
                return;
            }

            // 2. Verify consumable availability (cursor first, then inventory)
            ItemStack cursor = player.getItemOnCursor();
            boolean onCursor = isMatchingConsumable(cursor, session.getConsumableId());
            int invConsumableSlot = -1;
            if (!onCursor) {
                invConsumableSlot = findConsumableSlot(player, session.getConsumableId());
                if (invConsumableSlot == -1) {
                    messageManager.send(player, "extract-item-invalid");
                    closeGui(player);
                    return;
                }
            }

            // 3. Remove exactly the selected enchantment from target item
            if (currentTarget.getItemMeta() instanceof EnchantmentStorageMeta storageMeta) {
                storageMeta.removeStoredEnchant(entry.getEnchantment());
                currentTarget.setItemMeta(storageMeta);
            } else {
                currentTarget.removeEnchantment(entry.getEnchantment());
            }

            // Maintain cosmetic glow if target definition has glow enabled
            if (targetDef != null && targetDef.isGlow()) {
                maintainGlowEffect(currentTarget);
            }

            // Re-synchronize lore and PDC hash if target is a RoyItem
            if (pdcManager.isRoyItem(currentTarget)) {
                loreEngine.synchronizeItemEnchantments(currentTarget);
            }

            player.getInventory().setItem(session.getTargetSlot(), currentTarget);

            // 4. Consume 1 consumable scroll
            if (onCursor) {
                if (cursor.getAmount() > 1) {
                    cursor.setAmount(cursor.getAmount() - 1);
                    player.setItemOnCursor(cursor);
                } else {
                    player.setItemOnCursor(null);
                }
            } else {
                deductConsumableFromInventory(player, invConsumableSlot);
            }

            // 5. Roll return chance
            double roll = ThreadLocalRandom.current().nextDouble() * 100.0;
            boolean returnBook = (roll <= entry.getChance());

            Map<String, String> placeholders = new HashMap<>();
            placeholders.put("enchantment", entry.getName());
            placeholders.put("level", entry.getRomanLevel());
            placeholders.put("rarity", entry.getRarityDisplayName());
            placeholders.put("type", entry.getTypeName());
            placeholders.put("chance", String.format(Locale.ROOT, "%.1f", entry.getChance()).replaceAll("\\.0$", ""));

            if (returnBook) {
                // Generate valid enchanted book with preserved metadata
                ItemStack book = createEnchantedBook(entry.getEnchantment(), entry.getLevel(), entry.isEco());
                if (book != null) {
                    Map<Integer, ItemStack> leftover = player.getInventory().addItem(book);
                    if (!leftover.isEmpty()) {
                        for (ItemStack drop : leftover.values()) {
                            player.getWorld().dropItemNaturally(player.getLocation(), drop);
                        }
                        messageManager.send(player, "extract-inventory-full-dropped", placeholders);
                    }
                }
                messageManager.send(player, "extract-success-book", placeholders);
                player.playSound(player.getLocation(), Sound.BLOCK_ENCHANTMENT_TABLE_USE, 1.0f, 1.0f);
            } else {
                messageManager.send(player, "extract-success-no-book", placeholders);
                player.playSound(player.getLocation(), Sound.BLOCK_GRINDSTONE_USE, 1.0f, 0.8f);
            }

            // 6. Close GUI and complete session
            closeGui(player);
        } finally {
            processingPlayers.remove(uuid);
            activeSessions.remove(uuid);
        }
    }

    private ItemStack createEnchantedBook(Enchantment ench, int level, boolean isEco) {
        if (ench == null) return null;

        // 1. EcoEnchants API
        if (isEco && integrationManager != null && integrationManager.getEcoEnchantsHook() != null) {
            ItemStack ecoBook = integrationManager.getEcoEnchantsHook().createEnchantedBook(ench, level);
            if (ecoBook != null) {
                return ecoBook;
            }
        }

        // 2. Standard Bukkit EnchantmentStorageMeta
        ItemStack book = new ItemStack(Material.ENCHANTED_BOOK);
        if (book.getItemMeta() instanceof EnchantmentStorageMeta meta) {
            meta.addStoredEnchant(ench, level, true);
            book.setItemMeta(meta);
        }
        return book;
    }

    private boolean isMatchingConsumable(ItemStack item, String expectedId) {
        if (item == null || item.getType().isAir() || !pdcManager.isRoyItem(item)) {
            return false;
        }
        return pdcManager.getId(item).equalsIgnoreCase(expectedId);
    }

    private int findConsumableSlot(Player player, String expectedId) {
        ItemStack[] contents = player.getInventory().getContents();
        for (int i = 0; i < contents.length; i++) {
            ItemStack item = contents[i];
            if (isMatchingConsumable(item, expectedId)) {
                return i;
            }
        }
        return -1;
    }

    private void deductConsumableFromInventory(Player player, int slot) {
        if (slot < 0) return;
        ItemStack item = player.getInventory().getItem(slot);
        if (item != null && !item.getType().isAir()) {
            if (item.getAmount() > 1) {
                item.setAmount(item.getAmount() - 1);
                player.getInventory().setItem(slot, item);
            } else {
                player.getInventory().setItem(slot, null);
            }
        }
    }

    public void trackOpenGui(UUID playerId, Gui gui) {
        if (playerId != null && gui != null) {
            openGuis.put(playerId, gui);
        }
    }

    public void untrackOpenGui(UUID playerId) {
        if (playerId != null) {
            openGuis.remove(playerId);
            activeSessions.remove(playerId);
            processingPlayers.remove(playerId);
        }
    }

    public void closeGui(Player player) {
        if (player == null) return;
        Gui gui = openGuis.remove(player.getUniqueId());
        if (gui != null) {
            gui.close(player);
        } else {
            player.closeInventory();
        }
        activeSessions.remove(player.getUniqueId());
        processingPlayers.remove(player.getUniqueId());
    }

    /**
     * Closes all active extraction GUIs cleanly across all players upon reload or disable.
     */
    public void closeAll() {
        for (Map.Entry<UUID, Gui> entry : openGuis.entrySet()) {
            Player p = plugin.getServer().getPlayer(entry.getKey());
            if (p != null && p.isOnline()) {
                entry.getValue().close(p);
            }
        }
        openGuis.clear();
        activeSessions.clear();
        processingPlayers.clear();
    }

    /**
     * Determines whether a given enchantment on an item is a dummy cosmetic glow enchantment.
     */
    public static boolean isDummyGlow(ItemDefinition def, Enchantment ench, int level) {
        if (def != null && def.isGlow() && ench != null) {
            if (ench.equals(Enchantment.LUCK_OF_THE_SEA) && level == 1) {
                return def.getEnchantments() == null || !def.getEnchantments().containsKey(Enchantment.LUCK_OF_THE_SEA);
            }
        }
        return false;
    }

    /**
     * Filters out dummy cosmetic glow enchantments from an enchantment map if the definition has glow enabled.
     */
    public static void filterDummyGlowEnchantments(Map<Enchantment, Integer> enchants, ItemDefinition def) {
        if (enchants == null || def == null || !def.isGlow()) return;
        boolean hasConfiguredLuck = def.getEnchantments() != null
                && def.getEnchantments().containsKey(Enchantment.LUCK_OF_THE_SEA);
        if (!hasConfiguredLuck) {
            Integer lvl = enchants.get(Enchantment.LUCK_OF_THE_SEA);
            if (lvl != null && lvl == 1) {
                enchants.remove(Enchantment.LUCK_OF_THE_SEA);
            }
        }
    }

    /**
     * Checks whether an item contains real, non-cosmetic enchantments.
     */
    public boolean hasRealEnchantments(ItemStack item, ItemDefinition def) {
        if (item == null || item.getType().isAir()) return false;
        Map<Enchantment, Integer> map = new LinkedHashMap<>(item.getEnchantments());
        if (item.getItemMeta() instanceof EnchantmentStorageMeta storageMeta) {
            map.putAll(storageMeta.getStoredEnchants());
        }
        filterDummyGlowEnchantments(map, def);
        return !map.isEmpty();
    }

    /**
     * Preserves or reapplies cosmetic glow on an item without introducing fake extractable enchantments.
     */
    public static void maintainGlowEffect(ItemStack item) {
        if (item == null || item.getType().isAir()) return;
        try {
            org.bukkit.inventory.meta.ItemMeta meta = item.getItemMeta();
            if (meta != null) {
                meta.setEnchantmentGlintOverride(true);
                item.setItemMeta(meta);
            }
        } catch (Throwable ignored) {
            if (!item.containsEnchantment(Enchantment.LUCK_OF_THE_SEA)) {
                item.addUnsafeEnchantment(Enchantment.LUCK_OF_THE_SEA, 1);
                org.bukkit.inventory.meta.ItemMeta meta = item.getItemMeta();
                if (meta != null) {
                    meta.addItemFlags(org.bukkit.inventory.ItemFlag.HIDE_ENCHANTS);
                    item.setItemMeta(meta);
                }
            }
        }
    }
}
