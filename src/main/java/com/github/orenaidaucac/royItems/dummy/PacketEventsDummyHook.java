package com.github.orenaidaucac.royItems.dummy;

import com.github.retrooper.packetevents.PacketEvents;
import com.github.retrooper.packetevents.event.PacketListenerAbstract;
import com.github.retrooper.packetevents.event.PacketListenerPriority;
import com.github.retrooper.packetevents.event.PacketReceiveEvent;
import com.github.retrooper.packetevents.protocol.entity.data.EntityData;
import com.github.retrooper.packetevents.protocol.entity.data.EntityDataTypes;
import com.github.retrooper.packetevents.protocol.entity.type.EntityType;
import com.github.retrooper.packetevents.protocol.entity.type.EntityTypes;
import com.github.retrooper.packetevents.protocol.packettype.PacketType;
import com.github.retrooper.packetevents.util.Vector3d;
import com.github.retrooper.packetevents.wrapper.play.client.WrapperPlayClientInteractEntity;
import com.github.retrooper.packetevents.wrapper.play.server.*;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * High-performance PacketEvents 2.x implementation for packet-based Training Dummies.
 * Spawns and manages client-side mob models and Text Display holograms without server entities.
 */
public class PacketEventsDummyHook implements DummyPacketHook {

    private final DummyManager dummyManager;
    private final PacketListenerAbstract listener;

    public PacketEventsDummyHook(DummyManager dummyManager) {
        this.dummyManager = dummyManager;
        this.listener = new PacketListenerAbstract(PacketListenerPriority.HIGHEST) {
            @Override
            public void onPacketReceive(PacketReceiveEvent event) {
                if (event.getPacketType() == PacketType.Play.Client.INTERACT_ENTITY) {
                    try {
                        WrapperPlayClientInteractEntity packet = new WrapperPlayClientInteractEntity(event);
                        // Only register ATTACK (left-click). Ignore right-click (INTERACT / INTERACT_AT) entirely
                        if (packet.getAction() != WrapperPlayClientInteractEntity.InteractAction.ATTACK) {
                            return;
                        }

                        int targetId = packet.getEntityId();
                        TrainingDummy dummy = dummyManager.getDummyByEntityId(targetId);
                        if (dummy != null) {
                            event.setCancelled(true);
                            Player player = null;
                            if (event.getPlayer() instanceof Player p) {
                                player = p;
                            } else if (event.getUser() != null) {
                                if (event.getUser().getUUID() != null) {
                                    player = Bukkit.getPlayer(event.getUser().getUUID());
                                } else if (event.getUser().getName() != null) {
                                    player = Bukkit.getPlayerExact(event.getUser().getName());
                                }
                            }
                            if (player != null && player.isOnline()) {
                                dummyManager.handlePlayerAttack(player, dummy);
                            }
                        }
                    } catch (Throwable ignored) {}
                }
            }
        };
    }

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

    @Override
    public void spawnDummy(Player player, TrainingDummy dummy) {
        if (player == null || !player.isOnline() || dummy == null) return;

        Location loc = dummy.getLocation();

        // 1. Spawn Mob Entity
        EntityType mobType = dummy.getEntityType() != null ? dummy.getEntityType() : EntityTypes.ZOMBIE;
        WrapperPlayServerSpawnEntity spawnMob = new WrapperPlayServerSpawnEntity(
                dummy.getEntityId(),
                Optional.of(dummy.getUuid()),
                mobType,
                new Vector3d(loc.getX(), loc.getY(), loc.getZ()),
                loc.getPitch(),
                loc.getYaw(),
                loc.getYaw(),
                0,
                Optional.empty()
        );
        PacketEvents.getAPI().getPlayerManager().sendPacket(player, spawnMob);

        // 2. Mob Entity Metadata (Silent = true, NoGravity = true, Health = 20.0f to ensure alive & pickable)
        List<EntityData> mobData = new ArrayList<>();
        mobData.add(new EntityData(4, EntityDataTypes.BOOLEAN, true));
        mobData.add(new EntityData(5, EntityDataTypes.BOOLEAN, true));
        mobData.add(new EntityData(9, EntityDataTypes.FLOAT, 20.0f));
        WrapperPlayServerEntityMetadata metaMob = new WrapperPlayServerEntityMetadata(dummy.getEntityId(), mobData);
        PacketEvents.getAPI().getPlayerManager().sendPacket(player, metaMob);

        // 3. Spawn Text Display Hologram (if enabled)
        if (dummy.isHologramEnabled()) {
            Location holoLoc = loc.clone().add(0, dummy.getHologramHeight(), 0);
            WrapperPlayServerSpawnEntity spawnHolo = new WrapperPlayServerSpawnEntity(
                    dummy.getHologramEntityId(),
                    Optional.of(dummy.getHologramUuid()),
                    EntityTypes.TEXT_DISPLAY,
                    new Vector3d(holoLoc.getX(), holoLoc.getY(), holoLoc.getZ()),
                    0f,
                    0f,
                    0f,
                    0,
                    Optional.empty()
            );
            PacketEvents.getAPI().getPlayerManager().sendPacket(player, spawnHolo);

            // 4. Send Hologram Metadata
            sendHologramMetadata(player, dummy);
        }
    }

    @Override
    public void destroyDummy(Player player, TrainingDummy dummy) {
        if (player == null || !player.isOnline() || dummy == null) return;

        WrapperPlayServerDestroyEntities destroy;
        if (dummy.isHologramEnabled()) {
            destroy = new WrapperPlayServerDestroyEntities(dummy.getEntityId(), dummy.getHologramEntityId());
        } else {
            destroy = new WrapperPlayServerDestroyEntities(dummy.getEntityId());
        }
        PacketEvents.getAPI().getPlayerManager().sendPacket(player, destroy);
    }

    @Override
    public void updateHologram(Player player, TrainingDummy dummy) {
        if (player == null || !player.isOnline() || dummy == null) return;
        if (dummy.isHologramEnabled()) {
            sendHologramMetadata(player, dummy);
        }
    }

    private void sendHologramMetadata(Player player, TrainingDummy dummy) {
        List<EntityData> holoData = new ArrayList<>();

        // Index 15: Billboard constraint (0 = FIXED, 1 = VERTICAL, 2 = HORIZONTAL, 3 = CENTER)
        holoData.add(new EntityData(15, EntityDataTypes.BYTE, dummy.getBillboardByte()));

        // Index 23: Text Component
        Component textComp = dummy.getHologramText();
        holoData.add(new EntityData(23, EntityDataTypes.ADV_COMPONENT, textComp));

        // Index 25: Background Color ARGB
        holoData.add(new EntityData(25, EntityDataTypes.INT, dummy.getHologramBackgroundColor()));

        // Index 27: Style flags bitmask (Bit 0 = 0x01: Shadow)
        byte styleFlags = 0;
        if (dummy.isHologramShadow()) {
            styleFlags |= 1;
        }
        holoData.add(new EntityData(27, EntityDataTypes.BYTE, styleFlags));

        WrapperPlayServerEntityMetadata metaHolo = new WrapperPlayServerEntityMetadata(dummy.getHologramEntityId(), holoData);
        PacketEvents.getAPI().getPlayerManager().sendPacket(player, metaHolo);
    }

    @Override
    public void updateRotation(Player player, TrainingDummy dummy, float yaw, float pitch, float headYaw) {
        if (player == null || !player.isOnline() || dummy == null) return;
        WrapperPlayServerEntityHeadLook headLook = new WrapperPlayServerEntityHeadLook(dummy.getEntityId(), headYaw);
        WrapperPlayServerEntityRotation rot = new WrapperPlayServerEntityRotation(dummy.getEntityId(), yaw, pitch, true);
        PacketEvents.getAPI().getPlayerManager().sendPacket(player, headLook);
        PacketEvents.getAPI().getPlayerManager().sendPacket(player, rot);
    }

    @Override
    public void playHurtAnimation(Player player, TrainingDummy dummy, boolean isCrit) {
        if (player == null || !player.isOnline() || dummy == null) return;
        WrapperPlayServerEntityAnimation anim = new WrapperPlayServerEntityAnimation(
                dummy.getEntityId(),
                isCrit ? WrapperPlayServerEntityAnimation.EntityAnimationType.CRITICAL_HIT : WrapperPlayServerEntityAnimation.EntityAnimationType.HURT
        );
        PacketEvents.getAPI().getPlayerManager().sendPacket(player, anim);
    }
}
