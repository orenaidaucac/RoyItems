package com.github.orenaidaucac.royItems.mechanics.packet;

import com.github.retrooper.packetevents.PacketEvents;
import com.github.retrooper.packetevents.event.PacketListenerAbstract;
import com.github.retrooper.packetevents.event.PacketListenerPriority;
import com.github.retrooper.packetevents.event.PacketSendEvent;
import com.github.retrooper.packetevents.protocol.packettype.PacketType;
import com.github.retrooper.packetevents.protocol.particle.type.ParticleTypes;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerParticle;

/**
 * Intercepts and cancels outgoing DAMAGE_INDICATOR particle packets via PacketEvents.
 */
public class PacketEventsFilter implements ParticleFilterHook {

    private final PacketListenerAbstract listener = new PacketListenerAbstract(PacketListenerPriority.HIGHEST) {
        @Override
        public void onPacketSend(PacketSendEvent event) {
            if (event.getPacketType() == PacketType.Play.Server.PARTICLE) {
                try {
                    WrapperPlayServerParticle packet = new WrapperPlayServerParticle(event);
                    if (packet.getParticle() != null && packet.getParticle().getType() == ParticleTypes.DAMAGE_INDICATOR) {
                        event.setCancelled(true);
                    }
                } catch (Throwable ignored) {}
            }
        }
    };

    @Override
    public void register() {
        PacketEvents.getAPI().getEventManager().registerListener(listener);
    }

    @Override
    public void unregister() {
        try {
            PacketEvents.getAPI().getEventManager().unregisterListener(listener);
        } catch (Throwable ignored) {}
    }

    @Override
    public String getName() {
        return "PacketEvents";
    }
}
