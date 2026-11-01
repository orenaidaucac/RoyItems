package com.github.orenaidaucac.royItems.dummy;

import org.bukkit.entity.Player;

/**
 * Interface defining packet-level operations for spawning, updating, and destroying training dummies.
 */
public interface DummyPacketHook {

    void register();

    void unregister();

    String getName();

    void spawnDummy(Player player, TrainingDummy dummy);

    void destroyDummy(Player player, TrainingDummy dummy);

    void updateHologram(Player player, TrainingDummy dummy);

    void updateRotation(Player player, TrainingDummy dummy, float yaw, float pitch, float headYaw);

    void playHurtAnimation(Player player, TrainingDummy dummy, boolean isCrit);
}
