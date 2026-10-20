package com.github.orenaidaucac.royItems.stat;

import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

/**
 * Custom Defense Engine providing 3 distinct, configurable damage calculation modes:
 * - SIMPLE: Final Damage = Attack Damage - Defense (with safe non-negative floor).
 * - MMORPG: Non-linear diminishing returns curve (low damage attacks still deal partial damage).
 * - CUSTOM_LIMIT: Direct subtraction with independent configurable Min/Max damage limits.
 *
 * Integrates PvP/PvE Defense seamlessly: Effective Defense = Defense + (PvP/PvE Defense).
 */
public class DefenseManager {

    public enum DefenseMode {
        SIMPLE,
        MMORPG,
        CUSTOM_LIMIT
    }

    private final Plugin plugin;
    private final StatManager statManager;

    private DefenseMode mode = DefenseMode.MMORPG;
    private double simpleFloor = 0.0;

    private double mmorpgScale = 100.0;
    private double mmorpgDamageFactor = 1.0;
    private double mmorpgMinDamageFloor = 1.0;

    private boolean customLimitMinEnabled = true;
    private double customLimitMinValue = 2.0;
    private boolean customLimitMaxEnabled = false;
    private double customLimitMaxValue = 100.0;

    private double maxDamageReduction = 0.85;

    public DefenseManager() {
        this(null, null);
    }

    public DefenseManager(Plugin plugin, StatManager statManager) {
        this.plugin = plugin;
        this.statManager = statManager;
        loadConfig();
    }

    /**
     * Loads defense and combat balancing parameters from config.yml.
     */
    public void loadConfig() {
        if (plugin == null) return;
        FileConfiguration config = plugin.getConfig();

        // 1. Select Defense Mode
        String modeStr = config.getString("combat.defense.mode", "MMORPG").trim().toUpperCase();
        try {
            this.mode = DefenseMode.valueOf(modeStr);
        } catch (IllegalArgumentException e) {
            plugin.getLogger().warning("[RoyItems] Unknown defense mode '" + modeStr + "', defaulting to MMORPG.");
            this.mode = DefenseMode.MMORPG;
        }

        // 2. Simple Mode Settings
        this.simpleFloor = config.getDouble("combat.defense.simple.floor-damage", 0.0);

        // 3. MMORPG Mode Settings
        this.mmorpgScale = Math.max(1.0, config.getDouble("combat.defense.mmorpg.scale", 100.0));
        this.mmorpgDamageFactor = config.getDouble("combat.defense.mmorpg.damage-factor", 1.0);
        this.mmorpgMinDamageFloor = config.getDouble("combat.defense.mmorpg.min-damage-floor", 1.0);

        // 4. Custom Limit Mode Settings
        this.customLimitMinEnabled = config.getBoolean("combat.defense.custom-limit.min-damage.enabled", true);
        this.customLimitMinValue = config.getDouble("combat.defense.custom-limit.min-damage.value", 2.0);
        this.customLimitMaxEnabled = config.getBoolean("combat.defense.custom-limit.max-damage.enabled", false);
        this.customLimitMaxValue = config.getDouble("combat.defense.custom-limit.max-damage.value", 100.0);

        // Safety check: ensure min <= max if both are active
        if (customLimitMinEnabled && customLimitMaxEnabled && customLimitMinValue > customLimitMaxValue) {
            plugin.getLogger().warning("[RoyItems] Invalid defense custom-limit: min-damage ("
                    + customLimitMinValue + ") > max-damage (" + customLimitMaxValue + "). Disabling max-damage limit.");
            this.customLimitMaxEnabled = false;
        }

        // 5. Global damage reduction cap
        this.maxDamageReduction = Math.max(0.0, Math.min(1.0, config.getDouble("combat.max-damage-reduction", 0.85)));
    }

    public DefenseMode getMode() {
        return mode;
    }

    public void setMode(DefenseMode mode) {
        this.mode = mode;
    }

    public double getSimpleFloor() {
        return simpleFloor;
    }

    public void setSimpleFloor(double simpleFloor) {
        this.simpleFloor = simpleFloor;
    }

    public double getMmorpgScale() {
        return mmorpgScale;
    }

    public void setMmorpgScale(double mmorpgScale) {
        this.mmorpgScale = mmorpgScale;
    }

    public double getMmorpgDamageFactor() {
        return mmorpgDamageFactor;
    }

    public void setMmorpgDamageFactor(double mmorpgDamageFactor) {
        this.mmorpgDamageFactor = mmorpgDamageFactor;
    }

    public double getMmorpgMinDamageFloor() {
        return mmorpgMinDamageFloor;
    }

    public void setMmorpgMinDamageFloor(double mmorpgMinDamageFloor) {
        this.mmorpgMinDamageFloor = mmorpgMinDamageFloor;
    }

    public boolean isCustomLimitMinEnabled() {
        return customLimitMinEnabled;
    }

    public void setCustomLimitMinEnabled(boolean customLimitMinEnabled) {
        this.customLimitMinEnabled = customLimitMinEnabled;
    }

    public double getCustomLimitMinValue() {
        return customLimitMinValue;
    }

    public void setCustomLimitMinValue(double customLimitMinValue) {
        this.customLimitMinValue = customLimitMinValue;
    }

    public boolean isCustomLimitMaxEnabled() {
        return customLimitMaxEnabled;
    }

    public void setCustomLimitMaxEnabled(boolean customLimitMaxEnabled) {
        this.customLimitMaxEnabled = customLimitMaxEnabled;
    }

    public double getCustomLimitMaxValue() {
        return customLimitMaxValue;
    }

    public void setCustomLimitMaxValue(double customLimitMaxValue) {
        this.customLimitMaxValue = customLimitMaxValue;
    }

    public double getMaxDamageReduction() {
        return maxDamageReduction;
    }

    /**
     * Calculates Effective Defense = Base Defense + Applicable (PvP / PvE Defense).
     */
    public double calculateEffectiveDefense(Player victim, boolean isPvP, boolean isPvE) {
        PlayerStatCache cache = statManager.getCache(victim);
        double defense = cache.calculateTotal(StatType.DEFENSE, 0.0);

        if (isPvP) {
            double pvpDef = cache.calculateTotal(StatType.PVP_DEFENSE, 0.0);
            defense += pvpDef;
        } else if (isPvE) {
            double pveDef = cache.calculateTotal(StatType.PVE_DEFENSE, 0.0);
            defense += pveDef;
        }
        return defense;
    }

    /**
     * Applies defense mitigation based on the active DefenseMode.
     * Guaranteed to return a safe non-negative finite value.
     */
    public double applyDefense(double attackDamage, double effectiveDefense) {
        if (attackDamage <= 0) return 0.0;

        double damageAfterDefense;

        switch (mode) {
            case SIMPLE -> {
                // Formula: Final Damage = Attack Damage - Defense
                // Safely handles negative intermediate values without healing the victim
                double raw = attackDamage - effectiveDefense;
                damageAfterDefense = Math.max(simpleFloor, raw);
            }
            case MMORPG -> {
                // Non-linear curve: Damage * (scale / (scale + Defense))
                if (effectiveDefense > 0) {
                    double mult = mmorpgScale / (mmorpgScale + effectiveDefense);
                    damageAfterDefense = attackDamage * mult * mmorpgDamageFactor;
                    if (damageAfterDefense < mmorpgMinDamageFloor && attackDamage > 0) {
                        damageAfterDefense = Math.min(attackDamage, mmorpgMinDamageFloor);
                    }
                } else if (effectiveDefense < 0) {
                    // Vulnerability / negative defense
                    double mult = mmorpgScale / Math.max(1.0, mmorpgScale + effectiveDefense);
                    damageAfterDefense = attackDamage * mult * mmorpgDamageFactor;
                } else {
                    damageAfterDefense = attackDamage * mmorpgDamageFactor;
                }
            }
            case CUSTOM_LIMIT -> {
                // Formula: Attack Damage - Defense clamped by independent Min and Max limits
                double raw = attackDamage - effectiveDefense;
                if (customLimitMinEnabled) {
                    raw = Math.max(customLimitMinValue, raw);
                }
                if (customLimitMaxEnabled) {
                    raw = Math.min(customLimitMaxValue, raw);
                }
                damageAfterDefense = Math.max(0.0, raw);
            }
            default -> damageAfterDefense = Math.max(0.0, attackDamage - effectiveDefense);
        }

        if (Double.isNaN(damageAfterDefense) || Double.isInfinite(damageAfterDefense) || damageAfterDefense < 0) {
            return 0.0;
        }
        return damageAfterDefense;
    }

    /**
     * Applies percentage damage reduction capped by maxDamageReduction.
     */
    public double applyDamageReduction(Player victim, double damage) {
        if (damage <= 0) return 0.0;
        PlayerStatCache cache = statManager.getCache(victim);
        double reduction = cache.calculateTotal(StatType.DAMAGE_REDUCTION, 0.0);
        if (reduction > 0) {
            double fraction = Math.min(maxDamageReduction, Math.max(0.0, reduction / 100.0));
            damage *= (1.0 - fraction);
        }
        return Math.max(0.0, damage);
    }
}
