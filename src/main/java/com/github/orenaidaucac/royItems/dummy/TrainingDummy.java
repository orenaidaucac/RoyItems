package com.github.orenaidaucac.royItems.dummy;

import com.github.orenaidaucac.royItems.core.text.TextUtility;
import com.github.retrooper.packetevents.protocol.entity.type.EntityType;
import net.kyori.adventure.text.Component;
import org.bukkit.Location;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Represents a packet-based Training Dummy entity with tracked DPS, Total Damage,
 * configurable Text Display hologram lines, colors, and client tracking sets.
 */
public class TrainingDummy {

    private final String id;
    private final Location location;
    private final String mobTypeName;
    private final EntityType entityType;
    private final int entityId;
    private final UUID uuid;
    private final int hologramEntityId;
    private final UUID hologramUuid;

    // Settings
    private boolean lookAtPlayer = true;
    private double lookRange = 12.0;
    private double trackingRange = 48.0;
    private int sessionResetDelaySeconds = 5;

    // Hologram settings
    private boolean hologramEnabled = true;
    private double hologramOffsetY = 0.0; // 0.0 means calculate automatically by mob type
    private String billboardType = "CENTER";
    private boolean hologramShadow = true;
    private int hologramBackgroundColor = 0x40000000;
    private List<String> idleLines = new ArrayList<>();
    private List<String> activeLines = new ArrayList<>();
    private String critIndicator = " <gold>★CRIT!</gold>";

    // Combat tracking metrics
    private double totalDamage = 0.0;
    private double lastDamage = 0.0;
    private boolean lastCrit = false;
    private int hitCount = 0;
    private long sessionStartTime = 0L;
    private long lastHitTime = 0L;
    private double dps = 0.0;
    private boolean wasActiveBefore = false;

    // Players currently within view distance who have received spawn packets
    private final Set<UUID> trackingPlayers = ConcurrentHashMap.newKeySet();

    public TrainingDummy(String id, Location location, String mobTypeName, EntityType entityType,
                         int entityId, int hologramEntityId) {
        this.id = id;
        this.location = location;
        this.mobTypeName = mobTypeName != null ? mobTypeName.toUpperCase() : "ZOMBIE";
        this.entityType = entityType;
        this.entityId = entityId;
        this.uuid = UUID.randomUUID();
        this.hologramEntityId = hologramEntityId;
        this.hologramUuid = UUID.randomUUID();
        applyDefaults();
    }

    public void applyDefaults() {
        if (this.idleLines.isEmpty()) {
            this.idleLines = new ArrayList<>(getDefaultIdleLines());
        }
        if (this.activeLines.isEmpty()) {
            this.activeLines = new ArrayList<>(getDefaultActiveLines());
        }
    }

    public static List<String> getDefaultIdleLines() {
        return List.of(
                "<gradient:#FF512F:#DD2476>⚔ Training Dummy ({id}) ⚔</gradient>",
                "<gray>Type:</gray> <white>{mob_type}</white>",
                "<yellow>Hit to test Damage & DPS</yellow>"
        );
    }

    public static List<String> getDefaultActiveLines() {
        return List.of(
                "<gradient:#FF512F:#DD2476>⚔ Training Dummy ({id}) ⚔</gradient>",
                "<gray>DPS:</gray> <yellow><b>{dps}</b></yellow> <dark_gray>|</dark_gray> <gray>Total:</gray> <red><b>{total_damage}</b></red>",
                "<gray>Last:</gray> <green>+{last_damage}{crit}</green> <dark_gray>|</dark_gray> <gray>Hits:</gray> <aqua>{hits}</aqua>"
        );
    }

    public String getId() {
        return id;
    }

    public Location getLocation() {
        return location;
    }

    public String getMobTypeName() {
        return mobTypeName;
    }

    public EntityType getEntityType() {
        return entityType;
    }

    public int getEntityId() {
        return entityId;
    }

    public UUID getUuid() {
        return uuid;
    }

    public int getHologramEntityId() {
        return hologramEntityId;
    }

    public UUID getHologramUuid() {
        return hologramUuid;
    }

    public boolean isLookAtPlayer() {
        return lookAtPlayer;
    }

    public void setLookAtPlayer(boolean lookAtPlayer) {
        this.lookAtPlayer = lookAtPlayer;
    }

    public double getLookRange() {
        return lookRange;
    }

    public void setLookRange(double lookRange) {
        this.lookRange = lookRange;
    }

    public double getTrackingRange() {
        return trackingRange;
    }

    public void setTrackingRange(double trackingRange) {
        this.trackingRange = trackingRange;
    }

    public int getSessionResetDelaySeconds() {
        return sessionResetDelaySeconds;
    }

    public void setSessionResetDelaySeconds(int sessionResetDelaySeconds) {
        this.sessionResetDelaySeconds = Math.max(1, sessionResetDelaySeconds);
    }

    public boolean isHologramEnabled() {
        return hologramEnabled;
    }

    public void setHologramEnabled(boolean hologramEnabled) {
        this.hologramEnabled = hologramEnabled;
    }

    public double getHologramOffsetY() {
        return hologramOffsetY;
    }

    public void setHologramOffsetY(double hologramOffsetY) {
        this.hologramOffsetY = hologramOffsetY;
    }

    public String getBillboardType() {
        return billboardType;
    }

    public void setBillboardType(String billboardType) {
        this.billboardType = billboardType != null ? billboardType : "CENTER";
    }

    public byte getBillboardByte() {
        if (billboardType == null) return (byte) 3;
        return switch (billboardType.trim().toUpperCase()) {
            case "FIXED" -> (byte) 0;
            case "VERTICAL" -> (byte) 1;
            case "HORIZONTAL" -> (byte) 2;
            case "CENTER" -> (byte) 3;
            default -> (byte) 3;
        };
    }

    public boolean isHologramShadow() {
        return hologramShadow;
    }

    public void setHologramShadow(boolean hologramShadow) {
        this.hologramShadow = hologramShadow;
    }

    public int getHologramBackgroundColor() {
        return hologramBackgroundColor;
    }

    public void setHologramBackgroundColor(int hologramBackgroundColor) {
        this.hologramBackgroundColor = hologramBackgroundColor;
    }

    public List<String> getIdleLines() {
        return idleLines;
    }

    public void setIdleLines(List<String> idleLines) {
        if (idleLines != null && !idleLines.isEmpty()) {
            this.idleLines = new ArrayList<>(idleLines);
        }
    }

    public List<String> getActiveLines() {
        return activeLines;
    }

    public void setActiveLines(List<String> activeLines) {
        if (activeLines != null && !activeLines.isEmpty()) {
            this.activeLines = new ArrayList<>(activeLines);
        }
    }

    public String getCritIndicator() {
        return critIndicator;
    }

    public void setCritIndicator(String critIndicator) {
        this.critIndicator = critIndicator != null ? critIndicator : " <gold>★CRIT!</gold>";
    }

    public double getTotalDamage() {
        return totalDamage;
    }

    public double getLastDamage() {
        return lastDamage;
    }

    public boolean isLastCrit() {
        return lastCrit;
    }

    public int getHitCount() {
        return hitCount;
    }

    public double getDps() {
        return dps;
    }

    public long getLastHitTime() {
        return lastHitTime;
    }

    public Set<UUID> getTrackingPlayers() {
        return trackingPlayers;
    }

    public boolean isSessionActive() {
        if (hitCount == 0) return false;
        long now = System.currentTimeMillis();
        long resetDelayMillis = Math.max(1, sessionResetDelaySeconds) * 1000L;
        return (now - lastHitTime <= resetDelayMillis);
    }

    /**
     * Checks if the session just expired since the last tick check.
     * Used by the ticker to update hologram back to idle lines.
     */
    public synchronized boolean checkSessionExpiration() {
        boolean active = isSessionActive();
        if (!active && wasActiveBefore) {
            wasActiveBefore = false;
            return true; // Transitioned from active to idle
        }
        return false;
    }

    /**
     * Records damage dealt to the dummy, computing DPS across active hits.
     */
    public synchronized void recordHit(double damage, boolean isCrit) {
        long now = System.currentTimeMillis();
        long resetDelayMillis = Math.max(1, sessionResetDelaySeconds) * 1000L;

        if (now - lastHitTime > resetDelayMillis) {
            sessionStartTime = now;
            totalDamage = damage;
            hitCount = 1;
        } else {
            totalDamage += damage;
            hitCount++;
        }

        lastDamage = damage;
        lastCrit = isCrit;
        lastHitTime = now;
        wasActiveBefore = true;

        double elapsedSec = Math.max(0.5, (now - sessionStartTime) / 1000.0);
        dps = totalDamage / elapsedSec;
    }

    /**
     * Generates Adventure Component text for the Text Display hologram.
     * Supports MiniMessage, Hex, and Legacy color codes across all lines.
     */
    public Component getHologramText() {
        boolean active = isSessionActive();
        List<String> lines = active ? activeLines : idleLines;
        if (lines == null || lines.isEmpty()) {
            lines = active ? getDefaultActiveLines() : getDefaultIdleLines();
        }

        String critReplacement = (lastCrit && active) ? critIndicator : "";
        String dpsStr = formatNumber(dps);
        String totalDmgStr = formatNumber(totalDamage);
        String lastDmgStr = formatNumber(lastDamage);
        String hitsStr = String.valueOf(hitCount);

        List<Component> componentLines = new ArrayList<>();
        for (String rawLine : lines) {
            String processed = rawLine
                    .replace("{id}", id)
                    .replace("{mob_type}", mobTypeName)
                    .replace("{dps}", dpsStr)
                    .replace("{total_damage}", totalDmgStr)
                    .replace("{last_damage}", lastDmgStr)
                    .replace("{crit}", critReplacement)
                    .replace("{hits}", hitsStr);

            componentLines.add(TextUtility.format(processed, false));
        }

        Component combined = Component.empty();
        for (int i = 0; i < componentLines.size(); i++) {
            if (i > 0) combined = combined.append(Component.newline());
            combined = combined.append(componentLines.get(i));
        }
        return combined;
    }

    /**
     * Formats numbers cleanly: integers without trailing zeros (e.g. "6"),
     * fractions with 1 decimal digit (e.g. "6.5").
     */
    public static String formatNumber(double value) {
        if (Math.abs(value - Math.round(value)) < 0.001) {
            return String.format(Locale.US, "%,d", Math.round(value));
        } else {
            return String.format(Locale.US, "%,.1f", value);
        }
    }

    /**
     * Calculates height offset for the hologram above the mob's feet.
     */
    public double getHologramHeight() {
        if (hologramOffsetY > 0.001) {
            return hologramOffsetY;
        }
        String type = mobTypeName;
        if (type.contains("GOLEM") || type.contains("WARDEN") || type.contains("RAVAGER")) {
            return 3.0;
        }
        if (type.contains("ENDERMAN")) {
            return 3.1;
        }
        if (type.contains("SLIME") || type.contains("PIG") || type.contains("SHEEP") || type.contains("CHICKEN")) {
            return 1.4;
        }
        return 2.2;
    }
}
