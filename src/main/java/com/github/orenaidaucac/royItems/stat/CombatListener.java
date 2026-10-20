package com.github.orenaidaucac.royItems.stat;

import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.entity.*;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;

/**
 * Handles real-time combat calculations: attack damage, arrow damage, critical hits,
 * custom defense modes (SIMPLE, MMORPG, CUSTOM_LIMIT), damage reduction, and PvP/PvE scaling.
 */
public class CombatListener implements Listener {

    private final StatManager statManager;
    private final DefenseManager defenseManager;

    public CombatListener(StatManager statManager, DefenseManager defenseManager) {
        this.statManager = statManager;
        this.defenseManager = defenseManager;
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onCombat(EntityDamageByEntityEvent event) {
        Entity damagerEntity = event.getDamager();
        Entity victimEntity = event.getEntity();

        Player attackerPlayer = null;
        boolean isArrow = false;
        Entity trueSource = damagerEntity;

        // Resolve indirect projectile damage vs direct melee damage
        if (damagerEntity instanceof Projectile projectile) {
            if (projectile.getShooter() instanceof Entity shooter) {
                trueSource = shooter;
                if (shooter instanceof Player p) {
                    attackerPlayer = p;
                }
            }
            if (projectile instanceof Arrow) {
                isArrow = true;
            }
        } else if (damagerEntity instanceof Player p) {
            attackerPlayer = p;
        }

        double damage = event.getDamage();

        // 1. Process Attacker Modifiers (Offensive)
        if (attackerPlayer != null) {
            PlayerStatCache attackerCache = statManager.getCache(attackerPlayer);

            // Arrow damage bonus
            if (isArrow) {
                double arrowBonus = attackerCache.calculateTotal(StatType.ARROW_DAMAGE, 0.0);
                damage += arrowBonus;
            }

            // Critical Hit Calculation
            double critRate = attackerCache.calculateTotal(StatType.CRIT_RATE, 0.0); // e.g. 25 for 25%
            if (critRate > 0 && Math.random() * 100.0 < critRate) {
                double critDmgBonus = attackerCache.calculateTotal(StatType.CRIT_DAMAGE, 50.0); // Default 50% extra
                double multiplier = 1.0 + (critDmgBonus / 100.0);
                damage *= multiplier;

                // Visual/Audio feedback for critical hit
                Location loc = victimEntity.getLocation().add(0, 1, 0);
                if (loc.getWorld() != null) {
                    loc.getWorld().spawnParticle(Particle.CRIT, loc, 15, 0.3, 0.3, 0.3, 0.1);
                }
                attackerPlayer.playSound(loc, Sound.ENTITY_PLAYER_ATTACK_CRIT, 0.8f, 1.2f);
            }

            // PvP / PvE Offensive Scaling
            if (victimEntity instanceof Player) {
                double pvpDmg = attackerCache.calculateTotal(StatType.PVP_DAMAGE, 0.0);
                damage += pvpDmg;
            } else if (victimEntity instanceof LivingEntity) {
                double pveDmg = attackerCache.calculateTotal(StatType.PVE_DAMAGE, 0.0);
                damage += pveDmg;
            }
        }

        // 2. Process Victim Modifiers (Custom Defense & Mitigation)
        if (victimEntity instanceof Player victim) {
            boolean isPvP = (trueSource instanceof Player);
            boolean isPvE = (trueSource instanceof LivingEntity && !isPvP);

            // Effective Defense = Base Defense + (PvP/PvE Defense)
            double effectiveDefense = defenseManager.calculateEffectiveDefense(victim, isPvP, isPvE);

            // Apply selected Defense Mode (SIMPLE, MMORPG, CUSTOM_LIMIT)
            damage = defenseManager.applyDefense(damage, effectiveDefense);

            // Apply Percentage Damage Reduction
            damage = defenseManager.applyDamageReduction(victim, damage);
        }

        // 3. Final Safety Clamp (prevent NaN, Infinity, or negative damage)
        if (Double.isNaN(damage) || Double.isInfinite(damage) || damage < 0) {
            damage = 0.0;
        }

        event.setDamage(damage);
    }
}
