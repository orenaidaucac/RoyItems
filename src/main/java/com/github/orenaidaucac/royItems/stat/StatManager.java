package com.github.orenaidaucac.royItems.stat;

import com.github.orenaidaucac.royItems.item.ItemBuilder;
import com.github.orenaidaucac.royItems.item.ItemDefinition;
import com.github.orenaidaucac.royItems.item.ItemPDCManager;
import com.github.orenaidaucac.royItems.item.ItemRegistry;
import com.github.orenaidaucac.royItems.item.ItemType;
import com.github.orenaidaucac.royItems.mechanics.RequirementChecker;
import com.github.orenaidaucac.royItems.tier_set.ItemSetManager;
import org.bukkit.NamespacedKey;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.attribute.AttributeModifier;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Event-driven Stat Manager that computes and caches player stats from equipped items,
 * inventory talismans, slotted gems, and set bonuses, and dynamically synchronizes
 * Minecraft AttributeInstances on the Player entity.
 */
public class StatManager {

    private final ItemRegistry registry;
    private final ItemPDCManager pdcManager;
    private final ItemSetManager setManager;
    private RequirementChecker requirementChecker;
    private final Map<UUID, PlayerStatCache> playerCaches = new ConcurrentHashMap<>();
    private final Map<UUID, Set<Attribute>> playerAppliedVanillaAttrs = new ConcurrentHashMap<>();

    // NamespacedKeys for standard StatType attributes
    private static final NamespacedKey KEY_HEALTH_FLAT = new NamespacedKey("royitems", "stat_max_health_flat");
    private static final NamespacedKey KEY_HEALTH_PERCENT = new NamespacedKey("royitems", "stat_max_health_percent");

    private static final NamespacedKey KEY_SPEED_FLAT = new NamespacedKey("royitems", "stat_movement_speed_flat");
    private static final NamespacedKey KEY_SPEED_PERCENT = new NamespacedKey("royitems", "stat_movement_speed_percent");

    private static final NamespacedKey KEY_ATTACK_SPEED_FLAT = new NamespacedKey("royitems", "stat_attack_speed_flat");
    private static final NamespacedKey KEY_ATTACK_SPEED_PERCENT = new NamespacedKey("royitems", "stat_attack_speed_percent");

    private static final NamespacedKey KEY_ARMOR_FLAT = new NamespacedKey("royitems", "stat_armor_flat");
    private static final NamespacedKey KEY_ARMOR_PERCENT = new NamespacedKey("royitems", "stat_armor_percent");

    private static final NamespacedKey KEY_ARMOR_TOUGHNESS_FLAT = new NamespacedKey("royitems", "stat_armor_toughness_flat");
    private static final NamespacedKey KEY_ARMOR_TOUGHNESS_PERCENT = new NamespacedKey("royitems", "stat_armor_toughness_percent");

    private static final NamespacedKey KEY_KNOCKBACK_FLAT = new NamespacedKey("royitems", "stat_knockback_resistance_flat");
    private static final NamespacedKey KEY_KNOCKBACK_PERCENT = new NamespacedKey("royitems", "stat_knockback_resistance_percent");

    private static final NamespacedKey KEY_ATTACK_DAMAGE_FLAT = new NamespacedKey("royitems", "stat_attack_damage_flat");
    private static final NamespacedKey KEY_ATTACK_DAMAGE_PERCENT = new NamespacedKey("royitems", "stat_attack_damage_percent");

    public StatManager(ItemRegistry registry, ItemPDCManager pdcManager, ItemSetManager setManager) {
        this.registry = registry;
        this.pdcManager = pdcManager;
        this.setManager = setManager;
    }

    public void setRequirementChecker(RequirementChecker requirementChecker) {
        this.requirementChecker = requirementChecker;
    }

    public PlayerStatCache getCache(Player player) {
        if (player == null) return new PlayerStatCache();
        return playerCaches.computeIfAbsent(player.getUniqueId(), k -> new PlayerStatCache());
    }

    public void removePlayer(UUID uuid) {
        playerCaches.remove(uuid);
        playerAppliedVanillaAttrs.remove(uuid);
    }

    /**
     * Recalculates all stats for a player based on equipment, gems, talismans, and sets,
     * and synchronizes the Bukkit attributes on the Player entity.
     */
    public void recalculate(Player player) {
        if (player == null || !player.isValid()) return;

        PlayerStatCache cache = getCache(player);
        cache.reset();

        PlayerInventory inv = player.getInventory();
        Map<String, Integer> setCounts = new HashMap<>();

        // 1. Process Main Hand
        processItem(inv.getItemInMainHand(), cache, setCounts, player);

        // 2. Process Off Hand
        processItem(inv.getItemInOffHand(), cache, setCounts, player);

        // 3. Process Armor pieces
        for (ItemStack armor : inv.getArmorContents()) {
            processItem(armor, cache, setCounts, player);
        }

        // 4. Process Talismans in Inventory
        for (ItemStack item : inv.getStorageContents()) {
            if (item != null && pdcManager.isRoyItem(item)) {
                ItemType type = pdcManager.getType(item);
                if (type == ItemType.TALISMAN) {
                    processItem(item, cache, setCounts, player);
                }
            }
        }

        // 5. Apply Item Set Bonuses
        if (setManager != null) {
            setManager.applySetBonuses(setCounts, cache);
        }

        // 6. Apply attributes directly to Player entity
        applyAttributes(player, cache);
    }

    private void processItem(ItemStack item, PlayerStatCache cache, Map<String, Integer> setCounts, Player player) {
        if (item == null || !pdcManager.isRoyItem(item)) return;

        ItemType type = pdcManager.getType(item);
        String id = pdcManager.getId(item);
        ItemDefinition def = registry.get(type, id);
        if (def == null) return;

        // Check player usage requirements (level, class, permissions)
        if (player != null && requirementChecker != null && !requirementChecker.canUse(player, def)) {
            return;
        }

        // Base item stats
        if (def.getStats() != null) {
            for (Map.Entry<StatType, StatModifier> entry : def.getStats().entrySet()) {
                cache.addStat(entry.getKey(), entry.getValue());
            }
        }

        // Custom vanilla attributes
        if (def.getVanillaAttributes() != null) {
            for (Map.Entry<Attribute, StatModifier> entry : def.getVanillaAttributes().entrySet()) {
                cache.addVanillaAttribute(entry.getKey(), entry.getValue());
            }
        }

        // Slotted gems stats
        String socketsRaw = pdcManager.getSocketsData(item);
        if (socketsRaw != null && !socketsRaw.isEmpty()) {
            List<String> slotted = ItemBuilder.deserializeSockets(socketsRaw);
            for (String gemId : slotted) {
                if (gemId == null || gemId.isEmpty()) continue;
                ItemDefinition gemDef = registry.get(ItemType.GEM_STONE, gemId);
                if (gemDef != null) {
                    if (gemDef.getGemStats() != null) {
                        for (Map.Entry<StatType, StatModifier> gemEntry : gemDef.getGemStats().entrySet()) {
                            cache.addStat(gemEntry.getKey(), gemEntry.getValue());
                        }
                    }
                    if (gemDef.getVanillaAttributes() != null) {
                        for (Map.Entry<Attribute, StatModifier> vaEntry : gemDef.getVanillaAttributes().entrySet()) {
                            cache.addVanillaAttribute(vaEntry.getKey(), vaEntry.getValue());
                        }
                    }
                }
            }
        }

        // Track Set items
        if (def.getItemSet() != null && !def.getItemSet().isEmpty()) {
            setCounts.put(def.getItemSet(), setCounts.getOrDefault(def.getItemSet(), 0) + 1);
        }
    }

    /**
     * Applies computed stats and custom attributes directly to the player entity.
     */
    public void applyAttributes(Player player, PlayerStatCache cache) {
        if (player == null || !player.isValid()) return;

        // 1. MAX_HEALTH
        applyHealthAttribute(player, cache);

        // 2. MOVEMENT_SPEED
        applyAttributeModifier(player, StatType.MOVEMENT_SPEED.getVanillaAttribute(),
                KEY_SPEED_FLAT, KEY_SPEED_PERCENT,
                cache.getFlat(StatType.MOVEMENT_SPEED), cache.getPercent(StatType.MOVEMENT_SPEED));

        // 3. ATTACK_SPEED
        applyAttributeModifier(player, StatType.ATTACK_SPEED.getVanillaAttribute(),
                KEY_ATTACK_SPEED_FLAT, KEY_ATTACK_SPEED_PERCENT,
                cache.getFlat(StatType.ATTACK_SPEED), cache.getPercent(StatType.ATTACK_SPEED));

        // 4. ARMOR
        applyAttributeModifier(player, StatType.ARMOR.getVanillaAttribute(),
                KEY_ARMOR_FLAT, KEY_ARMOR_PERCENT,
                cache.getFlat(StatType.ARMOR), cache.getPercent(StatType.ARMOR));

        // 5. ARMOR_TOUGHNESS
        applyAttributeModifier(player, StatType.ARMOR_TOUGHNESS.getVanillaAttribute(),
                KEY_ARMOR_TOUGHNESS_FLAT, KEY_ARMOR_TOUGHNESS_PERCENT,
                cache.getFlat(StatType.ARMOR_TOUGHNESS), cache.getPercent(StatType.ARMOR_TOUGHNESS));

        // 6. KNOCKBACK_RESISTANCE
        applyAttributeModifier(player, StatType.KNOCKBACK_RESISTANCE.getVanillaAttribute(),
                KEY_KNOCKBACK_FLAT, KEY_KNOCKBACK_PERCENT,
                cache.getFlat(StatType.KNOCKBACK_RESISTANCE), cache.getPercent(StatType.KNOCKBACK_RESISTANCE));

        // 7. ATTACK_DAMAGE
        applyAttributeModifier(player, StatType.ATTACK_DAMAGE.getVanillaAttribute(),
                KEY_ATTACK_DAMAGE_FLAT, KEY_ATTACK_DAMAGE_PERCENT,
                cache.getFlat(StatType.ATTACK_DAMAGE), cache.getPercent(StatType.ATTACK_DAMAGE));

        // 8. Custom Vanilla Attributes (e.g. generic.scale, step_height, etc.)
        applyCustomVanillaAttributes(player, cache);
    }

    private void applyHealthAttribute(Player player, PlayerStatCache cache) {
        Attribute attr = StatType.MAX_HEALTH.getVanillaAttribute();
        if (attr == null) return;
        AttributeInstance instance = player.getAttribute(attr);
        if (instance == null) return;

        double oldMaxHealth = instance.getValue();
        double currentHealth = player.getHealth();

        removeModifierSafely(instance, KEY_HEALTH_FLAT);
        removeModifierSafely(instance, KEY_HEALTH_PERCENT);

        double flat = cache.getFlat(StatType.MAX_HEALTH);
        double percent = cache.getPercent(StatType.MAX_HEALTH);

        if (Math.abs(flat) > 0.0001) {
            instance.addTransientModifier(new AttributeModifier(KEY_HEALTH_FLAT, flat, AttributeModifier.Operation.ADD_NUMBER));
        }
        if (Math.abs(percent) > 0.0001) {
            instance.addTransientModifier(new AttributeModifier(KEY_HEALTH_PERCENT, percent, AttributeModifier.Operation.ADD_SCALAR));
        }

        double newMaxHealth = instance.getValue();
        if (newMaxHealth < 1.0) newMaxHealth = 1.0;

        if (player.isValid() && !player.isDead() && currentHealth > 0.0) {
            if (newMaxHealth > oldMaxHealth) {
                double diff = newMaxHealth - oldMaxHealth;
                player.setHealth(Math.min(newMaxHealth, currentHealth + diff));
            } else if (newMaxHealth < oldMaxHealth) {
                if (currentHealth > newMaxHealth) {
                    player.setHealth(newMaxHealth);
                }
            }
        }
    }

    private void applyAttributeModifier(Player player, Attribute attr, NamespacedKey flatKey, NamespacedKey percentKey, double flat, double percent) {
        if (attr == null) return;
        AttributeInstance instance = player.getAttribute(attr);
        if (instance == null) return;

        removeModifierSafely(instance, flatKey);
        removeModifierSafely(instance, percentKey);

        if (Math.abs(flat) > 0.0001) {
            instance.addTransientModifier(new AttributeModifier(flatKey, flat, AttributeModifier.Operation.ADD_NUMBER));
        }
        if (Math.abs(percent) > 0.0001) {
            instance.addTransientModifier(new AttributeModifier(percentKey, percent, AttributeModifier.Operation.ADD_SCALAR));
        }
    }

    private void removeModifierSafely(AttributeInstance instance, NamespacedKey key) {
        if (instance == null || key == null) return;
        try {
            instance.removeModifier(key);
        } catch (Throwable ignored) {}
        try {
            for (AttributeModifier mod : new ArrayList<>(instance.getModifiers())) {
                if (key.equals(mod.getKey())) {
                    instance.removeModifier(mod);
                }
            }
        } catch (Throwable ignored) {}
    }

    private void applyCustomVanillaAttributes(Player player, PlayerStatCache cache) {
        UUID uuid = player.getUniqueId();
        Set<Attribute> previouslyApplied = playerAppliedVanillaAttrs.getOrDefault(uuid, Collections.emptySet());
        Set<Attribute> currentlyApplied = new HashSet<>();

        // Remove old attributes that are no longer present
        for (Attribute oldAttr : previouslyApplied) {
            if (!cache.getVanillaAttributes().containsKey(oldAttr)) {
                AttributeInstance instance = player.getAttribute(oldAttr);
                if (instance != null) {
                    String cleanKey = oldAttr.getKey().getKey().replace('.', '_');
                    removeModifierSafely(instance, new NamespacedKey("royitems", "attr_" + cleanKey + "_flat"));
                    removeModifierSafely(instance, new NamespacedKey("royitems", "attr_" + cleanKey + "_percent"));
                }
            }
        }

        // Apply new/updated custom attributes
        for (Map.Entry<Attribute, StatModifier> entry : cache.getVanillaAttributes().entrySet()) {
            Attribute attr = entry.getKey();
            StatModifier mod = entry.getValue();
            if (attr == null || mod == null || mod.isEmpty()) continue;

            AttributeInstance instance = player.getAttribute(attr);
            if (instance == null) continue;

            String cleanKey = attr.getKey().getKey().replace('.', '_');
            NamespacedKey flatKey = new NamespacedKey("royitems", "attr_" + cleanKey + "_flat");
            NamespacedKey percentKey = new NamespacedKey("royitems", "attr_" + cleanKey + "_percent");

            removeModifierSafely(instance, flatKey);
            removeModifierSafely(instance, percentKey);

            if (Math.abs(mod.getFlat()) > 0.0001) {
                instance.addTransientModifier(new AttributeModifier(flatKey, mod.getFlat(), AttributeModifier.Operation.ADD_NUMBER));
            }
            if (Math.abs(mod.getPercent()) > 0.0001) {
                instance.addTransientModifier(new AttributeModifier(percentKey, mod.getPercent(), AttributeModifier.Operation.ADD_SCALAR));
            }
            currentlyApplied.add(attr);
        }

        if (currentlyApplied.isEmpty()) {
            playerAppliedVanillaAttrs.remove(uuid);
        } else {
            playerAppliedVanillaAttrs.put(uuid, currentlyApplied);
        }
    }

    /**
     * Cleans up all RoyItems modifiers from a player entity (e.g. on quit or plugin disable).
     */
    public void cleanupPlayer(Player player) {
        if (player == null) return;
        UUID uuid = player.getUniqueId();
        playerCaches.remove(uuid);

        try {
            cleanAttribute(player, StatType.MAX_HEALTH.getVanillaAttribute(), KEY_HEALTH_FLAT, KEY_HEALTH_PERCENT);
            cleanAttribute(player, StatType.MOVEMENT_SPEED.getVanillaAttribute(), KEY_SPEED_FLAT, KEY_SPEED_PERCENT);
            cleanAttribute(player, StatType.ATTACK_SPEED.getVanillaAttribute(), KEY_ATTACK_SPEED_FLAT, KEY_ATTACK_SPEED_PERCENT);
            cleanAttribute(player, StatType.ARMOR.getVanillaAttribute(), KEY_ARMOR_FLAT, KEY_ARMOR_PERCENT);
            cleanAttribute(player, StatType.ARMOR_TOUGHNESS.getVanillaAttribute(), KEY_ARMOR_TOUGHNESS_FLAT, KEY_ARMOR_TOUGHNESS_PERCENT);
            cleanAttribute(player, StatType.KNOCKBACK_RESISTANCE.getVanillaAttribute(), KEY_KNOCKBACK_FLAT, KEY_KNOCKBACK_PERCENT);
            cleanAttribute(player, StatType.ATTACK_DAMAGE.getVanillaAttribute(), KEY_ATTACK_DAMAGE_FLAT, KEY_ATTACK_DAMAGE_PERCENT);

            Set<Attribute> applied = playerAppliedVanillaAttrs.remove(uuid);
            if (applied != null) {
                for (Attribute attr : applied) {
                    AttributeInstance instance = player.getAttribute(attr);
                    if (instance != null) {
                        String cleanKey = attr.getKey().getKey().replace('.', '_');
                        removeModifierSafely(instance, new NamespacedKey("royitems", "attr_" + cleanKey + "_flat"));
                        removeModifierSafely(instance, new NamespacedKey("royitems", "attr_" + cleanKey + "_percent"));
                    }
                }
            }
        } catch (Throwable ignored) {}
    }

    private void cleanAttribute(Player player, Attribute attr, NamespacedKey flatKey, NamespacedKey percentKey) {
        if (attr == null) return;
        AttributeInstance instance = player.getAttribute(attr);
        if (instance != null) {
            removeModifierSafely(instance, flatKey);
            removeModifierSafely(instance, percentKey);
        }
    }
}
