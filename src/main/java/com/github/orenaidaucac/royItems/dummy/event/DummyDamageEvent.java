package com.github.orenaidaucac.royItems.dummy.event;

import com.github.orenaidaucac.royItems.dummy.TrainingDummy;
import org.bukkit.entity.Player;
import org.bukkit.event.Cancellable;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;
import org.jetbrains.annotations.NotNull;

/**
 * Fired when a player inflicts damage onto a client-side Training Dummy.
 * Allows plugins to inspect or modify the final damage, critical status, or cancel the attack.
 */
public class DummyDamageEvent extends Event implements Cancellable {

    private static final HandlerList HANDLERS = new HandlerList();

    private final Player player;
    private final TrainingDummy dummy;
    private double damage;
    private boolean crit;
    private final boolean projectile;
    private boolean cancelled = false;

    public DummyDamageEvent(Player player, TrainingDummy dummy, double damage, boolean crit, boolean projectile) {
        this.player = player;
        this.dummy = dummy;
        this.damage = damage;
        this.crit = crit;
        this.projectile = projectile;
    }

    public Player getPlayer() {
        return player;
    }

    public TrainingDummy getDummy() {
        return dummy;
    }

    public double getDamage() {
        return damage;
    }

    public void setDamage(double damage) {
        this.damage = Math.max(0.0, damage);
    }

    public boolean isCrit() {
        return crit;
    }

    public void setCrit(boolean crit) {
        this.crit = crit;
    }

    public boolean isProjectile() {
        return projectile;
    }

    @Override
    public boolean isCancelled() {
        return cancelled;
    }

    @Override
    public void setCancelled(boolean cancel) {
        this.cancelled = cancel;
    }

    @Override
    public @NotNull HandlerList getHandlers() {
        return HANDLERS;
    }

    public static HandlerList getHandlerList() {
        return HANDLERS;
    }
}
