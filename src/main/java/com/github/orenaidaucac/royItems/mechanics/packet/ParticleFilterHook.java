package com.github.orenaidaucac.royItems.mechanics.packet;

public interface ParticleFilterHook {
    void register();
    void unregister();
    String getName();
}
