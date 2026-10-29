package com.github.orenaidaucac.royItems.mechanics.packet;

import com.comphenix.protocol.PacketType;
import com.comphenix.protocol.ProtocolLibrary;
import com.comphenix.protocol.events.ListenerPriority;
import com.comphenix.protocol.events.PacketAdapter;
import com.comphenix.protocol.events.PacketEvent;
import com.comphenix.protocol.wrappers.WrappedParticle;
import org.bukkit.Particle;
import org.bukkit.plugin.Plugin;

/**
 * Intercepts and cancels outgoing DAMAGE_INDICATOR particle packets via ProtocolLib.
 */
public class ProtocolLibFilter implements ParticleFilterHook {

    private final Plugin plugin;
    private PacketAdapter adapter;

    public ProtocolLibFilter(Plugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public void register() {
        this.adapter = new PacketAdapter(plugin, ListenerPriority.HIGHEST, PacketType.Play.Server.WORLD_PARTICLES) {
            @Override
            public void onPacketSending(PacketEvent event) {
                try {
                    WrappedParticle<?> particle = event.getPacket().getNewParticles().readSafely(0);
                    if (particle != null && particle.getParticle() == Particle.DAMAGE_INDICATOR) {
                        event.setCancelled(true);
                    }
                } catch (Throwable ignored) {}
            }
        };
        ProtocolLibrary.getProtocolManager().addPacketListener(adapter);
    }

    @Override
    public void unregister() {
        if (adapter != null) {
            try {
                ProtocolLibrary.getProtocolManager().removePacketListener(adapter);
            } catch (Throwable ignored) {}
        }
    }

    @Override
    public String getName() {
        return "ProtocolLib";
    }
}
