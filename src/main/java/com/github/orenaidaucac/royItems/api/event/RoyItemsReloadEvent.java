package com.github.orenaidaucac.royItems.api.event;

import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;
import org.jetbrains.annotations.NotNull;

/**
 * Fired synchronously when RoyItems finishes reloading all configurations,
 * item definitions, lore templates, tiers, and sets (e.g. from /ri reload or GUI editor save).
 * Plugins integrating with RoyItems (such as RoyMC-VirtualStorage) can listen to this
 * event to synchronize cached item prototypes without requiring heavy database scans.
 */
public class RoyItemsReloadEvent extends Event {

    private static final HandlerList HANDLERS = new HandlerList();

    public RoyItemsReloadEvent() {
        super(false); // Synchronous event on main server thread
    }

    @NotNull
    @Override
    public HandlerList getHandlers() {
        return HANDLERS;
    }

    public static HandlerList getHandlerList() {
        return HANDLERS;
    }
}
