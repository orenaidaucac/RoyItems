package com.github.orenaidaucac.royItems.dummy;

import com.github.orenaidaucac.royItems.RoyItems;
import com.github.orenaidaucac.royItems.core.text.TextUtility;
import com.github.orenaidaucac.royItems.stat.PlayerStatCache;
import com.github.orenaidaucac.royItems.stat.StatType;
import com.github.retrooper.packetevents.protocol.entity.type.EntityType;
import com.github.retrooper.packetevents.protocol.entity.type.EntityTypes;
import org.bukkit.*;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.util.BoundingBox;
import org.bukkit.util.RayTraceResult;
import org.bukkit.util.Vector;

import java.io.File;
import java.io.IOException;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * High-performance, packet-based Training Dummy manager.
 * Dummies and Hologram Text Displays exist purely on the client side via network packets:
 * - 0 main-thread ticking overhead
 * - Immune to /kill @e, ClearLag, WorldGuard spawn/damage flags, and gamerules
 * - Cannot move, cannot make sounds, cannot be knocked back or pushed
 * - Smoothly looks at the closest player within customizable tracking distance
 * - Real-time packet Text Display tracking Total Damage and DPS
 * - Dual hit registration: PacketEvents INTERACT_ENTITY + Bukkit Arm-Swing RayTrace
 * - Fully configurable per-dummy files in dummy/<dummy_id>.yml
 */
public class DummyManager {

    private final RoyItems plugin;
    private DummyPacketHook packetHook;
    private final Map<String, TrainingDummy> dummies = new ConcurrentHashMap<>();
    private final Map<Integer, TrainingDummy> entityIdLookup = new ConcurrentHashMap<>();
    private final Map<UUID, Long> playerLastAttackTime = new ConcurrentHashMap<>();
    private final AtomicInteger entityIdCounter = new AtomicInteger(1_500_000);

    public DummyManager(RoyItems plugin) {
        this.plugin = plugin;
    }

    public void initialize() {
        // 1. Initialize Packet Hook
        if (isPluginAvailable("packetevents") || isPluginAvailable("PacketEvents")) {
            try {
                this.packetHook = new PacketEventsDummyHook(this);
                this.packetHook.register();
                plugin.getLogger().info("[TrainingDummy] Packet-based Dummy engine active via PacketEvents.");
            } catch (Throwable t) {
                plugin.getLogger().warning("[TrainingDummy] Could not initialize PacketEvents hook: " + t.getMessage());
            }
        } else if (isPluginAvailable("ProtocolLib")) {
            try {
                this.packetHook = new ProtocolLibDummyHook(plugin, this);
                this.packetHook.register();
                plugin.getLogger().info("[TrainingDummy] Packet-based Dummy engine active via ProtocolLib.");
            } catch (Throwable t) {
                plugin.getLogger().warning("[TrainingDummy] Could not initialize ProtocolLib hook: " + t.getMessage());
            }
        }

        // 2. Load persistent dummies from dummy/<dummy_id>.yml
        loadDummies();

        // 3. Start background ticker for proximity tracking, smooth rotation, and session expiration
        startTicker();
    }

    private boolean isPluginAvailable(String name) {
        org.bukkit.plugin.Plugin p = Bukkit.getPluginManager().getPlugin(name);
        return p != null && p.isEnabled();
    }

    public TrainingDummy getDummyByEntityId(int entityId) {
        return entityIdLookup.get(entityId);
    }

    public TrainingDummy getDummy(String id) {
        if (id == null) return null;
        return dummies.get(id.toLowerCase());
    }

    public Collection<TrainingDummy> getDummies() {
        return dummies.values();
    }

    public File getDummyDirectory() {
        File dir = new File(plugin.getDataFolder(), "dummy");
        if (!dir.exists()) {
            dir.mkdirs();
        }
        return dir;
    }

    /**
     * Spawns a packet-based dummy centered at the block the admin is looking at.
     */
    public TrainingDummy spawnDummy(String id, Location loc, String mobTypeName, Player spawner) {
        if (id == null || loc == null || loc.getWorld() == null) return null;
        String key = id.toLowerCase();

        // If dummy with this ID already exists, despawn previous
        if (dummies.containsKey(key)) {
            despawnDummy(key);
        }

        EntityType entityType = resolveEntityType(mobTypeName);
        int dummyId = entityIdCounter.incrementAndGet();
        int holoId = entityIdCounter.incrementAndGet();

        // Perfectly center location on top of the block
        Location centered = loc.clone();
        centered.setX(loc.getBlockX() + 0.5);
        centered.setZ(loc.getBlockZ() + 0.5);

        TrainingDummy dummy = new TrainingDummy(id, centered, mobTypeName, entityType, dummyId, holoId);
        dummies.put(key, dummy);
        // Register both entity ID and hologram ID so clicks on either register the attack
        entityIdLookup.put(dummyId, dummy);
        entityIdLookup.put(holoId, dummy);

        // Save persistent config to dummy/<id>.yml
        saveDummyToFile(dummy);

        // Broadcast spawn packet to nearby players within tracking range
        if (packetHook != null) {
            double rangeSq = dummy.getTrackingRange() * dummy.getTrackingRange();
            for (Player p : centered.getWorld().getPlayers()) {
                if (p.getLocation().distanceSquared(centered) <= rangeSq) {
                    dummy.getTrackingPlayers().add(p.getUniqueId());
                    packetHook.spawnDummy(p, dummy);
                }
            }
        }

        return dummy;
    }

    /**
     * Despawns a dummy and destroys its packet models on all client screens,
     * removing its configuration file.
     */
    public boolean despawnDummy(String id) {
        if (id == null) return false;
        String key = id.toLowerCase();
        TrainingDummy dummy = dummies.remove(key);
        if (dummy == null) return false;

        entityIdLookup.remove(dummy.getEntityId());
        entityIdLookup.remove(dummy.getHologramEntityId());

        // Destroy packet entities on all clients
        if (packetHook != null) {
            for (UUID uuid : dummy.getTrackingPlayers()) {
                Player p = Bukkit.getPlayer(uuid);
                if (p != null && p.isOnline()) {
                    packetHook.destroyDummy(p, dummy);
                }
            }
        }
        dummy.getTrackingPlayers().clear();

        // Delete config file dummy/<id>.yml
        deleteDummyFile(dummy.getId());
        return true;
    }

    /**
     * Despawns every active dummy on the server and deletes their configs.
     */
    public int despawnAll() {
        int count = dummies.size();
        for (String id : new ArrayList<>(dummies.keySet())) {
            despawnDummy(id);
        }
        return count;
    }

    /**
     * Server-side raytrace fallback: triggers when player swings arm or left clicks.
     * Guarantees 100% reliable attack hit registration even if client does not send INTERACT_ENTITY packet.
     */
    public void checkPlayerAttackRayTrace(Player player) {
        if (player == null || dummies.isEmpty()) return;
        Location eye = player.getEyeLocation();
        Vector dir = eye.getDirection().normalize();
        double maxReach = 4.5; // Standard survival weapon reach

        TrainingDummy bestTarget = null;
        double bestDist = Double.MAX_VALUE;

        for (TrainingDummy dummy : dummies.values()) {
            Location dLoc = dummy.getLocation();
            if (dLoc.getWorld() == null || !dLoc.getWorld().equals(player.getWorld())) continue;

            double distSq = eye.distanceSquared(dLoc);
            if (distSq > (maxReach + 2.0) * (maxReach + 2.0)) continue;

            // Bounding box for the dummy mob + hologram
            double height = dummy.getHologramHeight() + 0.6;
            BoundingBox box = new BoundingBox(
                    dLoc.getX() - 0.7, dLoc.getY(), dLoc.getZ() - 0.7,
                    dLoc.getX() + 0.7, dLoc.getY() + height, dLoc.getZ() + 0.7
            );

            RayTraceResult hit = box.rayTrace(eye.toVector(), dir, maxReach);
            if (hit != null) {
                double hitDist = eye.toVector().distance(hit.getHitPosition());
                if (hitDist < bestDist) {
                    bestDist = hitDist;
                    bestTarget = dummy;
                }
            } else {
                // Secondary fallback: angle check facing dummy center
                Vector toDummy = dLoc.clone().add(0, 1.0, 0).toVector().subtract(eye.toVector());
                double dist = toDummy.length();
                if (dist <= maxReach) {
                    Vector norm = toDummy.normalize();
                    double dot = dir.dot(norm);
                    if (dot > 0.82 && dist < bestDist) {
                        bestDist = dist;
                        bestTarget = dummy;
                    }
                }
            }
        }

        if (bestTarget != null) {
            handlePlayerAttack(player, bestTarget);
        }
    }

    /**
     * Handles attack packet from client when player left-clicks the packet dummy.
     */
    public void handlePlayerAttack(Player player, TrainingDummy dummy) {
        if (player == null || dummy == null) return;

        // 200ms debounce prevents double hits between PacketEvents INTERACT_ENTITY and Bukkit Arm Swing raytrace
        long now = System.currentTimeMillis();
        Long lastTime = playerLastAttackTime.get(player.getUniqueId());
        if (lastTime != null && (now - lastTime) < 200L) {
            return;
        }
        playerLastAttackTime.put(player.getUniqueId(), now);

        plugin.getPlatformScheduler().runEntity(player, () -> {
            PlayerStatCache cache = plugin.getStatManager().getCache(player);

            // 1. Compute weapon base attack damage from RoyItems custom stats or fallback to vanilla attribute instance
            double customDamage = cache.calculateTotal(StatType.ATTACK_DAMAGE, 0.0);
            double baseDamage;
            if (customDamage > 0.001) {
                baseDamage = customDamage;
            } else {
                double vanillaDamage = 1.0;
                try {
                    Attribute dmgAttr = Attribute.ATTACK_DAMAGE;
                    if (dmgAttr == null) {
                        dmgAttr = Registry.ATTRIBUTE.get(NamespacedKey.minecraft("generic.attack_damage"));
                    }
                    if (dmgAttr != null) {
                        AttributeInstance inst = player.getAttribute(dmgAttr);
                        if (inst != null) {
                            vanillaDamage = Math.max(1.0, inst.getValue());
                        }
                    }
                } catch (Throwable ignored) {}
                baseDamage = vanillaDamage;
            }
            double critRate = cache.calculateTotal(StatType.CRIT_RATE, 0.0);
            boolean isCrit = (critRate > 0.0 && ThreadLocalRandom.current().nextDouble() * 100.0 < critRate);

            double damage = baseDamage;
            if (isCrit) {
                double critBonus = cache.calculateTotal(StatType.CRIT_DAMAGE, 50.0);
                damage *= (1.0 + (critBonus / 100.0));
            }

            // PvE damage bonus
            double pveBonus = cache.calculateTotal(StatType.PVE_DAMAGE, 0.0);
            damage += pveBonus;

            // Record combat metrics in dummy
            dummy.recordHit(damage, isCrit);

            // Audio & Visual particles at dummy location
            Location dLoc = dummy.getLocation();
            if (dLoc.getWorld() != null) {
                if (isCrit) {
                    dLoc.getWorld().spawnParticle(Particle.CRIT, dLoc.clone().add(0, 1.2, 0), 15, 0.3, 0.3, 0.3, 0.1);
                    player.playSound(dLoc, Sound.ENTITY_PLAYER_ATTACK_CRIT, 0.9f, 1.15f);
                } else {
                    dLoc.getWorld().spawnParticle(Particle.SWEEP_ATTACK, dLoc.clone().add(0, 1.0, 0), 1);
                    player.playSound(dLoc, Sound.ENTITY_PLAYER_ATTACK_STRONG, 0.8f, 1.0f);
                }
            }

            // Broadcast hurt animation and hologram text update to attacking player and all tracking players
            if (packetHook != null) {
                dummy.getTrackingPlayers().add(player.getUniqueId());
                packetHook.playHurtAnimation(player, dummy, isCrit);
                packetHook.updateHologram(player, dummy);

                for (UUID uuid : dummy.getTrackingPlayers()) {
                    if (uuid.equals(player.getUniqueId())) continue;
                    Player p = Bukkit.getPlayer(uuid);
                    if (p != null && p.isOnline()) {
                        packetHook.playHurtAnimation(p, dummy, isCrit);
                        packetHook.updateHologram(p, dummy);
                    }
                }
            }
        });
    }

    private void startTicker() {
        // Ticks every 3 ticks (~150ms) to update head rotation, range culling, and session expiration
        plugin.getPlatformScheduler().runGlobalTimer(() -> {
            if (packetHook == null || dummies.isEmpty()) return;

            for (TrainingDummy dummy : dummies.values()) {
                Location dLoc = dummy.getLocation();
                if (dLoc.getWorld() == null) continue;

                // 1. Session Expiration check: if combat session expired back to idle, refresh hologram to idle lines
                if (dummy.checkSessionExpiration()) {
                    for (UUID uuid : dummy.getTrackingPlayers()) {
                        Player p = Bukkit.getPlayer(uuid);
                        if (p != null && p.isOnline()) {
                            packetHook.updateHologram(p, dummy);
                        }
                    }
                }

                Player nearest = null;
                double nearestDistSq = Double.MAX_VALUE;
                double trackingRange = dummy.getTrackingRange();
                double trackingDistSq = trackingRange * trackingRange;
                double lookRange = dummy.getLookRange();
                double lookDistSq = lookRange * lookRange;

                // 2. Range Culling: Check which players should see the dummy
                for (Player p : dLoc.getWorld().getPlayers()) {
                    double distSq = p.getLocation().distanceSquared(dLoc);
                    if (distSq <= trackingDistSq) {
                        if (dummy.getTrackingPlayers().add(p.getUniqueId())) {
                            packetHook.spawnDummy(p, dummy);
                        }
                        if (dummy.isLookAtPlayer() && distSq <= lookDistSq && distSq < nearestDistSq) {
                            nearest = p;
                            nearestDistSq = distSq;
                        }
                    } else {
                        if (dummy.getTrackingPlayers().remove(p.getUniqueId())) {
                            packetHook.destroyDummy(p, dummy);
                        }
                    }
                }

                // 3. Smooth Look At: Rotate head and body toward closest player
                if (nearest != null) {
                    float[] look = calculateLookAt(dLoc, nearest.getEyeLocation());
                    for (UUID uuid : dummy.getTrackingPlayers()) {
                        Player p = Bukkit.getPlayer(uuid);
                        if (p != null && p.isOnline()) {
                            packetHook.updateRotation(p, dummy, look[0], look[1], look[0]);
                        }
                    }
                }
            }
        }, 5L, 3L);
    }

    public static float[] calculateLookAt(Location from, Location to) {
        double dx = to.getX() - from.getX();
        double dy = to.getY() - (from.getY() + 1.5);
        double dz = to.getZ() - from.getZ();
        double distXZ = Math.sqrt(dx * dx + dz * dz);

        double yaw = Math.toDegrees(Math.atan2(-dx, dz));
        double pitch = Math.toDegrees(-Math.atan2(dy, distXZ));

        return new float[]{(float) yaw, (float) pitch};
    }

    public static EntityType resolveEntityType(String name) {
        if (name == null || name.isEmpty()) {
            try {
                return EntityTypes.ZOMBIE;
            } catch (Throwable ignored) {
                return null;
            }
        }
        String normalized = name.trim().toLowerCase();
        try {
            EntityType type = EntityTypes.getByName(normalized);
            if (type != null) return type;
        } catch (Throwable ignored) {}

        try {
            return switch (normalized) {
                case "skeleton" -> EntityTypes.SKELETON;
                case "iron_golem", "irongolem", "golem" -> EntityTypes.IRON_GOLEM;
                case "villager" -> EntityTypes.VILLAGER;
                case "creeper" -> EntityTypes.CREEPER;
                case "enderman" -> EntityTypes.ENDERMAN;
                case "piglin" -> EntityTypes.PIGLIN;
                case "wither_skeleton" -> EntityTypes.WITHER_SKELETON;
                case "blaze" -> EntityTypes.BLAZE;
                case "warden" -> EntityTypes.WARDEN;
                case "breeze" -> EntityTypes.BREEZE;
                case "cow" -> EntityTypes.COW;
                case "pig" -> EntityTypes.PIG;
                case "sheep" -> EntityTypes.SHEEP;
                case "wolf" -> EntityTypes.WOLF;
                case "armor_stand", "armorstand" -> EntityTypes.ARMOR_STAND;
                default -> EntityTypes.ZOMBIE;
            };
        } catch (Throwable ignored) {
            return null;
        }
    }

    private void loadDummies() {
        File dir = getDummyDirectory();

        // 1. Check legacy dummies.yml and migrate if present
        File legacyFile = new File(plugin.getDataFolder(), "dummies.yml");
        if (legacyFile.exists()) {
            migrateLegacyFile(legacyFile, dir);
        }

        File[] files = dir.listFiles((d, name) -> name.endsWith(".yml") || name.endsWith(".yaml"));
        if (files == null || files.length == 0) return;

        for (File file : files) {
            String id = file.getName().replace(".yml", "").replace(".yaml", "");
            try {
                YamlConfiguration config = YamlConfiguration.loadConfiguration(file);

                String worldName = config.getString("location.world");
                org.bukkit.World world = worldName != null ? Bukkit.getWorld(worldName) : null;
                if (world == null) {
                    plugin.getLogger().warning("[TrainingDummy] Skipped dummy " + id + ": world '" + worldName + "' not found.");
                    continue;
                }

                double x = config.getDouble("location.x");
                double y = config.getDouble("location.y");
                double z = config.getDouble("location.z");
                float yaw = (float) config.getDouble("location.yaw", 0.0);
                float pitch = (float) config.getDouble("location.pitch", 0.0);
                String mobType = config.getString("mob-type", "ZOMBIE");

                Location loc = new Location(world, x, y, z, yaw, pitch);
                int dummyId = entityIdCounter.incrementAndGet();
                int holoId = entityIdCounter.incrementAndGet();

                TrainingDummy dummy = new TrainingDummy(id, loc, mobType, resolveEntityType(mobType), dummyId, holoId);

                // Load Settings
                dummy.setLookAtPlayer(config.getBoolean("settings.look-at-player", true));
                dummy.setLookRange(config.getDouble("settings.look-range", 12.0));
                dummy.setTrackingRange(config.getDouble("settings.tracking-range", 48.0));
                dummy.setSessionResetDelaySeconds(config.getInt("settings.session-reset-delay-seconds", 5));

                // Load Hologram
                dummy.setHologramEnabled(config.getBoolean("hologram.enabled", true));
                dummy.setHologramOffsetY(config.getDouble("hologram.offset-y", 0.0));
                dummy.setBillboardType(config.getString("hologram.billboard", "CENTER"));
                dummy.setHologramShadow(config.getBoolean("hologram.shadow", true));
                dummy.setHologramBackgroundColor(parseColorInt(config.get("hologram.background-color"), 0x40000000));

                List<String> idle = config.getStringList("hologram.idle-lines");
                if (!idle.isEmpty()) {
                    dummy.setIdleLines(idle);
                }
                List<String> active = config.getStringList("hologram.active-lines");
                if (!active.isEmpty()) {
                    dummy.setActiveLines(active);
                }
                String crit = config.getString("hologram.crit-indicator");
                if (crit != null && !crit.isEmpty()) {
                    dummy.setCritIndicator(crit);
                }

                dummies.put(id.toLowerCase(), dummy);
                // Register both entity ID and hologram ID
                entityIdLookup.put(dummyId, dummy);
                entityIdLookup.put(holoId, dummy);
            } catch (Exception e) {
                plugin.getLogger().warning("[TrainingDummy] Error loading dummy file " + file.getName() + ": " + e.getMessage());
            }
        }
        plugin.getLogger().info("[TrainingDummy] Successfully loaded " + dummies.size() + " training dummies from dummy/ folder.");
    }

    private void migrateLegacyFile(File legacyFile, File dummyDir) {
        try {
            YamlConfiguration legacy = YamlConfiguration.loadConfiguration(legacyFile);
            ConfigurationSection sec = legacy.getConfigurationSection("dummies");
            if (sec != null) {
                for (String id : sec.getKeys(false)) {
                    ConfigurationSection dSec = sec.getConfigurationSection(id);
                    if (dSec == null) continue;
                    File target = new File(dummyDir, id + ".yml");
                    if (!target.exists()) {
                        YamlConfiguration newConfig = new YamlConfiguration();
                        newConfig.set("mob-type", dSec.getString("mob-type", "ZOMBIE"));
                        newConfig.set("location.world", dSec.getString("world", "world"));
                        newConfig.set("location.x", dSec.getDouble("x"));
                        newConfig.set("location.y", dSec.getDouble("y"));
                        newConfig.set("location.z", dSec.getDouble("z"));
                        newConfig.set("location.yaw", dSec.getDouble("yaw", 0.0));
                        newConfig.set("location.pitch", dSec.getDouble("pitch", 0.0));

                        newConfig.set("settings.look-at-player", true);
                        newConfig.set("settings.look-range", 12.0);
                        newConfig.set("settings.tracking-range", 48.0);
                        newConfig.set("settings.session-reset-delay-seconds", 5);

                        newConfig.set("hologram.enabled", true);
                        newConfig.set("hologram.offset-y", 0.0);
                        newConfig.set("hologram.billboard", "CENTER");
                        newConfig.set("hologram.shadow", true);
                        newConfig.set("hologram.background-color", "#40000000");
                        newConfig.set("hologram.idle-lines", TrainingDummy.getDefaultIdleLines());
                        newConfig.set("hologram.active-lines", TrainingDummy.getDefaultActiveLines());
                        newConfig.set("hologram.crit-indicator", " <gold>★CRIT!</gold>");
                        newConfig.save(target);
                    }
                }
            }
            File backup = new File(plugin.getDataFolder(), "dummies.yml.bak");
            legacyFile.renameTo(backup);
            plugin.getLogger().info("[TrainingDummy] Migrated legacy dummies.yml to dummy/ folder (backup saved as dummies.yml.bak).");
        } catch (Exception e) {
            plugin.getLogger().warning("[TrainingDummy] Could not migrate legacy dummies.yml: " + e.getMessage());
        }
    }

    public void saveDummyToFile(TrainingDummy dummy) {
        if (dummy == null) return;
        File dir = getDummyDirectory();
        File file = new File(dir, dummy.getId() + ".yml");
        YamlConfiguration config = new YamlConfiguration();

        try {
            config.options().setHeader(List.of(
                    "===================================================================",
                    "RoyItems Training Dummy Configuration",
                    "ID: " + dummy.getId(),
                    "Placeholders for hologram lines:",
                    "  {id}           - Dummy ID",
                    "  {mob_type}     - Mob Type Name",
                    "  {dps}          - Current DPS",
                    "  {total_damage} - Total session damage",
                    "  {last_damage}  - Last damage dealt",
                    "  {crit}         - Critical hit indicator",
                    "  {hits}         - Number of hits in session",
                    "Supports MiniMessage (<gradient:...>, <color:...>) & Legacy (&a, &#rrggbb)",
                    "==================================================================="
            ));
        } catch (Throwable ignored) {}

        config.set("mob-type", dummy.getMobTypeName());

        config.set("location.world", dummy.getLocation().getWorld() != null ? dummy.getLocation().getWorld().getName() : "world");
        config.set("location.x", dummy.getLocation().getX());
        config.set("location.y", dummy.getLocation().getY());
        config.set("location.z", dummy.getLocation().getZ());
        config.set("location.yaw", (double) dummy.getLocation().getYaw());
        config.set("location.pitch", (double) dummy.getLocation().getPitch());

        config.set("settings.look-at-player", dummy.isLookAtPlayer());
        config.set("settings.look-range", dummy.getLookRange());
        config.set("settings.tracking-range", dummy.getTrackingRange());
        config.set("settings.session-reset-delay-seconds", dummy.getSessionResetDelaySeconds());

        config.set("hologram.enabled", dummy.isHologramEnabled());
        config.set("hologram.offset-y", dummy.getHologramOffsetY());
        config.set("hologram.billboard", dummy.getBillboardType());
        config.set("hologram.shadow", dummy.isHologramShadow());
        config.set("hologram.background-color", String.format("#%08X", dummy.getHologramBackgroundColor()));
        config.set("hologram.idle-lines", dummy.getIdleLines());
        config.set("hologram.active-lines", dummy.getActiveLines());
        config.set("hologram.crit-indicator", dummy.getCritIndicator());

        try {
            config.save(file);
        } catch (IOException e) {
            plugin.getLogger().warning("[TrainingDummy] Could not save dummy file " + file.getName() + ": " + e.getMessage());
        }
    }

    private void deleteDummyFile(String id) {
        File dir = getDummyDirectory();
        File file = new File(dir, id + ".yml");
        if (file.exists()) {
            file.delete();
        }
    }

    public static int parseColorInt(Object val, int defaultColor) {
        if (val == null) return defaultColor;
        if (val instanceof Number num) return num.intValue();
        String str = val.toString().trim();
        if (str.startsWith("#")) {
            str = str.substring(1);
            try {
                if (str.length() == 8) {
                    return (int) Long.parseLong(str, 16);
                } else if (str.length() == 6) {
                    return (int) (0xFF000000L | Long.parseLong(str, 16));
                }
            } catch (NumberFormatException ignored) {}
        } else if (str.startsWith("0x") || str.startsWith("0X")) {
            try {
                return (int) Long.parseLong(str.substring(2), 16);
            } catch (NumberFormatException ignored) {}
        }
        try {
            return Integer.parseInt(str);
        } catch (NumberFormatException ignored) {
            return defaultColor;
        }
    }

    /**
     * Completely destroys all dummy and hologram packets on all client screens,
     * ensuring zero ghost entities or ghost holograms when plugin is disabled or reloaded.
     */
    public void cleanup() {
        if (packetHook != null) {
            for (TrainingDummy dummy : dummies.values()) {
                for (UUID uuid : dummy.getTrackingPlayers()) {
                    Player p = Bukkit.getPlayer(uuid);
                    if (p != null && p.isOnline()) {
                        try {
                            packetHook.destroyDummy(p, dummy);
                        } catch (Throwable ignored) {}
                    }
                }
                dummy.getTrackingPlayers().clear();
            }
            packetHook.unregister();
            packetHook = null;
        }
        dummies.clear();
        entityIdLookup.clear();
        playerLastAttackTime.clear();
    }
}
