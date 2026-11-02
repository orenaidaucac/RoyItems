package com.github.orenaidaucac.royItems.dummy;

import com.comphenix.protocol.PacketType;
import com.comphenix.protocol.ProtocolLibrary;
import com.comphenix.protocol.events.ListenerPriority;
import com.comphenix.protocol.events.PacketAdapter;
import com.comphenix.protocol.events.PacketContainer;
import com.comphenix.protocol.events.PacketEvent;
import com.comphenix.protocol.wrappers.EnumWrappers;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

/**
 * ProtocolLib fallback implementation for intercepting packet-based dummy attack interactions.
 */
public class ProtocolLibDummyHook implements DummyPacketHook {

    private final Plugin plugin;
    private final DummyManager dummyManager;
    private PacketAdapter adapter;

    public ProtocolLibDummyHook(Plugin plugin, DummyManager dummyManager) {
        this.plugin = plugin;
        this.dummyManager = dummyManager;
    }

    @Override
    public void register() {
        this.adapter = new PacketAdapter(plugin, ListenerPriority.HIGHEST, PacketType.Play.Client.USE_ENTITY) {
            @Override
            public void onPacketReceiving(PacketEvent event) {
                PacketContainer packet = event.getPacket();
                try {
                    int targetId = packet.getIntegers().read(0);
                    TrainingDummy dummy = dummyManager.getDummyByEntityId(targetId);
                    if (dummy != null) {
                        event.setCancelled(true);
                        Player player = event.getPlayer();
                        EnumWrappers.EntityUseAction action = packet.getEntityUseActions().readSafely(0);
                        // Only register ATTACK (left-click). Ignore right-click entirely
                        if (action == EnumWrappers.EntityUseAction.ATTACK) {
                            dummyManager.handlePlayerAttack(player, dummy);
                        }
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
            adapter = null;
        }
    }

    @Override
    public String getName() {
        return "ProtocolLib";
    }

    @Override
    public void spawnDummy(Player player, TrainingDummy dummy) {}

    @Override
    public void destroyDummy(Player player, TrainingDummy dummy) {}

    @Override
    public void updateHologram(Player player, TrainingDummy dummy) {}

    @Override
    public void updateRotation(Player player, TrainingDummy dummy, float yaw, float pitch, float headYaw) {}

    @Override
    public void playHurtAnimation(Player player, TrainingDummy dummy, boolean isCrit) {}
}
