package com.github.orenaidaucac.royItems.dummy;

import com.github.orenaidaucac.royItems.RoyItems;
import com.github.orenaidaucac.royItems.core.text.TextUtility;
import com.github.orenaidaucac.royItems.dummy.event.DummyDamageEvent;
import com.github.orenaidaucac.royItems.stat.PlayerStatCache;
import com.github.orenaidaucac.royItems.stat.StatType;
import com.github.retrooper.packetevents.protocol.entity.type.EntityType;
import com.github.retrooper.packetevents.protocol.entity.type.EntityTypes;
import org.bukkit.*;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.attribute.AttributeModifier;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.AbstractArrow;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.entity.Trident;
import org.bukkit.event.entity.ProjectileHitEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.metadata.FixedMetadataValue;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.util.BoundingBox;
import org.bukkit.util.RayTraceResult;
import org.bukkit.util.Vector;

import java.io.File;
import java.io.IOException;
import java.lang.ref.WeakReference;
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
    private final Map<UUID, Long> playerLastEventTime = new ConcurrentHashMap<>();
    private final Map<UUID, Long> playerLastAttackTime = new ConcurrentHashMap<>();
    private final AtomicInteger entityIdCounter = new AtomicInteger(1_500_000);
    private final Map<UUID, ProjectileTracker> activeProjectiles = new ConcurrentHashMap<>();

    public static class ProjectileTracker {
        final WeakReference<Projectile> projectileRef;
        final UUID shooterId;
        Location lastLocation;
        final long spawnTime;
        final ItemStack weaponUsed;

        public ProjectileTracker(Projectile projectile, Player shooter, ItemStack weaponUsed) {
            this.projectileRef = new WeakReference<>(projectile);
            this.shooterId = shooter.getUniqueId();
            this.lastLocation = projectile.getLocation().clone();
            this.spawnTime = System.currentTimeMillis();
            this.weaponUsed = weaponUsed != null ? weaponUsed.clone() : null;
        }
    }

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

        // 4. Start 1-tick background ticker for in-flight projectile collision tracking
        startProjectileTicker();
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
            BoundingBox box = dummy.getBoundingBox();

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
     * Registers active projectiles fired by players (Arrows, Tridents, etc.)
     * for real-time in-flight collision detection against packet dummies.
     */
    public void registerActiveProjectile(Projectile proj, Player player) {
        if (proj == null || player == null || dummies.isEmpty()) return;
        ItemStack weapon = player.getInventory().getItemInMainHand();
        if (weapon == null || weapon.getType() == Material.AIR) {
            weapon = player.getInventory().getItemInOffHand();
        }
        activeProjectiles.put(proj.getUniqueId(), new ProjectileTracker(proj, player, weapon));
    }

    private void startProjectileTicker() {
        plugin.getPlatformScheduler().runGlobalTimer(this::tickActiveProjectiles, 1L, 1L);
    }

    private void tickActiveProjectiles() {
        if (activeProjectiles.isEmpty() || dummies.isEmpty()) return;
        long now = System.currentTimeMillis();

        for (Iterator<Map.Entry<UUID, ProjectileTracker>> it = activeProjectiles.entrySet().iterator(); it.hasNext(); ) {
            Map.Entry<UUID, ProjectileTracker> entry = it.next();
            ProjectileTracker tracker = entry.getValue();
            Projectile proj = tracker.projectileRef.get();

            if (proj == null || !proj.isValid() || proj.isDead() || proj.isOnGround() || (now - tracker.spawnTime > 15000L)) {
                it.remove();
                continue;
            }

            Location current = proj.getLocation();
            Location previous = tracker.lastLocation;
            tracker.lastLocation = current.clone();

            if (current.getWorld() == null || previous.getWorld() == null || !current.getWorld().equals(previous.getWorld())) {
                it.remove();
                continue;
            }

            Vector seg = current.toVector().subtract(previous.toVector());
            double dist = seg.length();
            if (dist > 0.001) {
                Vector dir = seg.clone().normalize();
                for (TrainingDummy dummy : dummies.values()) {
                    if (dummy.getLocation().getWorld() == null || !dummy.getLocation().getWorld().equals(current.getWorld())) continue;
                    BoundingBox box = dummy.getBoundingBox();
                    RayTraceResult hit = box.rayTrace(previous.toVector(), dir, dist + 0.35);
                    if (hit != null || box.contains(current.toVector())) {
                        Player shooter = Bukkit.getPlayer(tracker.shooterId);
                        if (shooter != null && shooter.isOnline()) {
                            handleProjectileDamage(shooter, dummy, proj, tracker.weaponUsed);
                        }
                        try {
                            proj.remove();
                        } catch (Throwable ignored) {}
                        it.remove();
                        break;
                    }
                }
            }
        }
    }

    /**
     * Fallback collision check triggered when a projectile lands near a dummy.
     */
    public void handleProjectileHitFallback(Projectile proj, Player player, ProjectileHitEvent event) {
        if (proj == null || player == null || dummies.isEmpty()) return;
        activeProjectiles.remove(proj.getUniqueId());

        Location hitLoc = event.getHitBlock() != null
                ? event.getHitBlock().getLocation().clone().add(0.5, 0.5, 0.5)
                : proj.getLocation();

        for (TrainingDummy dummy : dummies.values()) {
            if (dummy.getLocation().getWorld() == null || !dummy.getLocation().getWorld().equals(hitLoc.getWorld())) continue;
            BoundingBox box = dummy.getBoundingBox();
            Vector eye = player.getEyeLocation().toVector();
            Vector toHit = hitLoc.toVector().subtract(eye);
            double maxDist = toHit.length();
            if (maxDist < 0.1) continue;

            RayTraceResult hit = box.rayTrace(eye, toHit.clone().normalize(), maxDist);
            if (hit != null || hitLoc.distanceSquared(dummy.getLocation().clone().add(0, 1.0, 0)) <= 2.8 * 2.8) {
                handleProjectileDamage(player, dummy, proj, player.getInventory().getItemInMainHand());
                try {
                    proj.remove();
                } catch (Throwable ignored) {}
                break;
            }
        }
    }

    /**
     * Calculates authentic, fair outgoing projectile damage (bow, crossbow, thrown trident)
     * including arrow velocity, Power/Impaling enchantments, RoyItems stats, and critical strikes.
     */
    public void handleProjectileDamage(Player player, TrainingDummy dummy, Projectile proj, ItemStack weapon) {
        if (player == null || dummy == null || proj == null) return;
        if (!player.isOnline() || player.isDead() || player.getGameMode() == GameMode.SPECTATOR) return;

        // Prevent double hits from the same physical projectile
        if (proj.hasMetadata("roy_dummy_hit_registered")) {
            return;
        }
        proj.setMetadata("roy_dummy_hit_registered", new FixedMetadataValue(plugin, true));

        plugin.getPlatformScheduler().runEntity(player, () -> {
            PlayerStatCache cache = plugin.getStatManager().getCache(player);

            double baseDamage = 8.0;
            boolean isArrowCrit = false;

            if (proj instanceof AbstractArrow arrow) {
                double speed = arrow.getVelocity().length();
                if (speed < 0.1) speed = 2.5; // Default charged speed fallback
                baseDamage = speed * arrow.getDamage();
                isArrowCrit = arrow.isCritical();
                if (isArrowCrit) {
                    baseDamage += (ThreadLocalRandom.current().nextDouble() * (baseDamage / 2.0 + 1.0));
                }
                // Arrow power enchantment
                if (weapon != null) {
                    int power = weapon.getEnchantmentLevel(Enchantment.POWER);
                    if (power > 0) {
                        baseDamage *= (1.0 + 0.25 * (power + 1));
                    }
                }
            } else if (proj instanceof Trident) {
                baseDamage = 9.0;
                if (weapon != null) {
                    int impaling = weapon.getEnchantmentLevel(Enchantment.IMPALING);
                    if (impaling > 0 && isAquatic(dummy.getMobTypeName())) {
                        baseDamage += (2.5 * impaling);
                    }
                }
            }

            // Custom RoyItems arrow-damage stat
            double customArrowDamage = cache.calculateTotal(StatType.ARROW_DAMAGE, 0.0);
            baseDamage += customArrowDamage;

            // Critical strike calculation
            double critRate = cache.calculateTotal(StatType.CRIT_RATE, 0.0);
            boolean isRoyCrit = (critRate > 0.0 && ThreadLocalRandom.current().nextDouble() * 100.0 < critRate);
            boolean isCrit = isArrowCrit || isRoyCrit;

            double finalDamage = baseDamage;
            if (isCrit) {
                double critBonus = cache.calculateTotal(StatType.CRIT_DAMAGE, 50.0);
                double multiplier = Math.max(1.5, 1.0 + (critBonus / 100.0));
                finalDamage *= multiplier;
            }

            // PvE Damage bonus
            double pveBonus = cache.calculateTotal(StatType.PVE_DAMAGE, 0.0);
            finalDamage += pveBonus;

            // Fire DummyDamageEvent for cancellation and third-party modifications
            DummyDamageEvent event = new DummyDamageEvent(player, dummy, finalDamage, isCrit, true);
            Bukkit.getPluginManager().callEvent(event);
            if (event.isCancelled() || event.getDamage() <= 0.0) {
                return;
            }
            finalDamage = event.getDamage();
            isCrit = event.isCrit();

            applyDamageToDummy(player, dummy, finalDamage, isCrit, proj.getLocation(), true);
        });
    }

    /**
     * Handles authentic melee attack from client when player left-clicks the packet dummy.
     * Accurately calculates the true final output damage based on player attribute attack power,
     * weapon attack-damage, potion effects (Strength/Weakness), attack cooldown, enchantments
     * (Sharpness/Smite/Bane/Impaling), Mace fall smashing, and critical strikes.
     */
    public void handlePlayerAttack(Player player, TrainingDummy dummy) {
        if (player == null || dummy == null) return;
        if (!player.isOnline() || player.isDead() || player.getGameMode() == GameMode.SPECTATOR) return;
        if (player.getWorld() == null || !player.getWorld().equals(dummy.getLocation().getWorld())) return;
        if (player.getLocation().distanceSquared(dummy.getLocation()) > 36.0) return;

        long now = System.currentTimeMillis();
        UUID uuid = player.getUniqueId();

        // 100ms debounce prevents duplicate hits between PacketEvents INTERACT_ENTITY and Bukkit Arm Swing raytrace
        Long lastEvent = playerLastEventTime.get(uuid);
        if (lastEvent != null && (now - lastEvent) < 100L) {
            return;
        }
        playerLastEventTime.put(uuid, now);

        // Previous attack timestamp for calculating authentic 1.9+ weapon swing cooldown
        Long prevAttackTime = playerLastAttackTime.get(uuid);
        playerLastAttackTime.put(uuid, now);

        plugin.getPlatformScheduler().runEntity(player, () -> {
            PlayerStatCache cache = plugin.getStatManager().getCache(player);
            ItemStack weapon = player.getInventory().getItemInMainHand();

            // 1. Resolve Player's True Attack Speed & Cooldown Factor
            // When attacking client packet entities, the client sends an Arm Swing packet which resets
            // Paper's internal attackStrengthTicker to 0 before this scheduler runs.
            // We calculate authentic cooldown using generic.attack_speed and elapsed time since last hit.
            double attackSpeed = 4.0;
            try {
                Attribute speedAttr = Attribute.ATTACK_SPEED;
                if (speedAttr == null) {
                    speedAttr = Registry.ATTRIBUTE.get(NamespacedKey.minecraft("generic.attack_speed"));
                }
                if (speedAttr != null) {
                    AttributeInstance speedInst = player.getAttribute(speedAttr);
                    if (speedInst != null) {
                        attackSpeed = Math.max(0.1, speedInst.getValue());
                    }
                }
            } catch (Throwable ignored) {}

            double periodMillis = (1.0 / attackSpeed) * 1000.0;
            float cooldown = 1.0f;
            if (prevAttackTime != null) {
                long elapsed = now - prevAttackTime;
                if (elapsed < periodMillis) {
                    cooldown = (float) Math.min(1.0, Math.max(0.1, (double) elapsed / periodMillis));
                }
            }
            try {
                cooldown = Math.max(cooldown, player.getAttackCooldown());
            } catch (Throwable ignored) {}

            // Snap near-full cooldown to 1.0f for smooth feel
            if (cooldown >= 0.85f) {
                cooldown = 1.0f;
            }

            // 2. Base Attack Power (Matches player's generic.attack_damage attribute)
            double baseAttackPower = 1.0;
            AttributeInstance inst = null;
            try {
                Attribute dmgAttr = Attribute.ATTACK_DAMAGE;
                if (dmgAttr == null) {
                    dmgAttr = Registry.ATTRIBUTE.get(NamespacedKey.minecraft("generic.attack_damage"));
                }
                if (dmgAttr != null) {
                    inst = player.getAttribute(dmgAttr);
                    if (inst != null) {
                        baseAttackPower = Math.max(1.0, inst.getValue());
                    }
                }
            } catch (Throwable ignored) {}

            // Fallback safety if StatManager attribute sync is slightly delayed
            double customWeaponDmg = cache.calculateTotal(StatType.ATTACK_DAMAGE, 0.0);
            if (customWeaponDmg > 0.001) {
                baseAttackPower = Math.max(baseAttackPower, 1.0 + customWeaponDmg);
            }

            // Potion effects: In Paper 1.20.5+, Strength/Weakness modify generic.attack_damage directly.
            // If the attribute instance does not already contain the effect modifier, apply manually as fallback.
            boolean hasStrengthMod = false;
            boolean hasWeaknessMod = false;
            if (inst != null) {
                for (AttributeModifier m : inst.getModifiers()) {
                    String mName = m.getName() != null ? m.getName().toLowerCase() : "";
                    String mKey = m.getKey() != null ? m.getKey().getKey().toLowerCase() : "";
                    if (mName.contains("strength") || mKey.contains("strength")) {
                        hasStrengthMod = true;
                    }
                    if (mName.contains("weakness") || mKey.contains("weakness")) {
                        hasWeaknessMod = true;
                    }
                }
            }
            if (!hasStrengthMod) {
                PotionEffect strength = player.getPotionEffect(PotionEffectType.STRENGTH);
                if (strength != null) {
                    baseAttackPower += (3.0 * (strength.getAmplifier() + 1));
                }
            }
            if (!hasWeaknessMod) {
                PotionEffect weakness = player.getPotionEffect(PotionEffectType.WEAKNESS);
                if (weakness != null) {
                    baseAttackPower = Math.max(0.0, baseAttackPower - (4.0 * (weakness.getAmplifier() + 1)));
                }
            }

            // 3. Attack Cooldown factor (Vanilla 1.9+ formula: 0.2 + cooldown^2 * 0.8)
            float cooldownFactor = (cooldown >= 0.9f) ? 1.0f : (0.2f + cooldown * cooldown * 0.8f);
            double scaledDamage = baseAttackPower * cooldownFactor;

            // 4. Weapon Enchantments
            double enchantBonus = 0.0;
            if (weapon != null && weapon.getType() != Material.AIR) {
                // Sharpness
                int sharpness = weapon.getEnchantmentLevel(Enchantment.SHARPNESS);
                if (sharpness > 0) {
                    enchantBonus += (0.5 * sharpness + 0.5);
                }

                // Smite (undead mobs)
                int smite = weapon.getEnchantmentLevel(Enchantment.SMITE);
                if (smite > 0 && isUndead(dummy.getMobTypeName())) {
                    enchantBonus += (2.5 * smite);
                }

                // Bane of Arthropods (arthropods)
                int bane = weapon.getEnchantmentLevel(Enchantment.BANE_OF_ARTHROPODS);
                if (bane > 0 && isArthropod(dummy.getMobTypeName())) {
                    enchantBonus += (2.5 * bane);
                }

                // Impaling
                int impaling = weapon.getEnchantmentLevel(Enchantment.IMPALING);
                if (impaling > 0 && isAquatic(dummy.getMobTypeName())) {
                    enchantBonus += (2.5 * impaling);
                }

                // Mace Density (1.21+)
                try {
                    Enchantment density = Registry.ENCHANTMENT.get(NamespacedKey.minecraft("density"));
                    if (density != null) {
                        int densityLvl = weapon.getEnchantmentLevel(density);
                        if (densityLvl > 0 && player.getFallDistance() > 1.5f) {
                            enchantBonus += (0.5 * densityLvl * player.getFallDistance());
                        }
                    }
                } catch (Throwable ignored) {}
            }

            // Enchantment damage scales with cooldown in vanilla
            scaledDamage += (enchantBonus * cooldown);

            // 5. Mace Smash Attack Bonus (Minecraft 1.21)
            if (weapon != null && weapon.getType().name().equals("MACE") && player.getFallDistance() > 1.5f) {
                float fall = player.getFallDistance();
                double maceBonus;
                if (fall <= 3.0f) {
                    maceBonus = fall * 3.0;
                } else if (fall <= 8.0f) {
                    maceBonus = (3.0 * 3.0) + (fall - 3.0) * 1.5;
                } else {
                    maceBonus = (3.0 * 3.0) + (5.0 * 1.5) + (fall - 8.0) * 1.0;
                }
                scaledDamage += maceBonus;
            }

            // 6. Critical Hit: Natural Jump-Crit or RoyItems RPG Crit
            boolean isVanillaCrit = (
                    player.getFallDistance() > 0.0f
                    && !player.isOnGround()
                    && !player.isClimbing()
                    && !player.isInWater()
                    && !player.hasPotionEffect(PotionEffectType.BLINDNESS)
                    && !player.isInsideVehicle()
                    && !player.isSprinting()
                    && cooldown >= 0.85f
            );

            double critRate = cache.calculateTotal(StatType.CRIT_RATE, 0.0);
            boolean isRoyCrit = (critRate > 0.0 && ThreadLocalRandom.current().nextDouble() * 100.0 < critRate);
            boolean isCrit = isVanillaCrit || isRoyCrit;

            double finalDamage = scaledDamage;
            if (isCrit) {
                double critBonus = cache.calculateTotal(StatType.CRIT_DAMAGE, 50.0);
                double multiplier = Math.max(1.5, 1.0 + (critBonus / 100.0));
                finalDamage *= multiplier;
            }

            // 7. PvE Damage Bonus
            double pveBonus = cache.calculateTotal(StatType.PVE_DAMAGE, 0.0);
            finalDamage += pveBonus;

            // Fire DummyDamageEvent for cancellation and third-party modifications
            DummyDamageEvent event = new DummyDamageEvent(player, dummy, finalDamage, isCrit, false);
            Bukkit.getPluginManager().callEvent(event);
            if (event.isCancelled() || event.getDamage() <= 0.0) {
                return;
            }
            finalDamage = event.getDamage();
            isCrit = event.isCrit();

            applyDamageToDummy(player, dummy, finalDamage, isCrit, dummy.getLocation().clone().add(0, 1.0, 0), false);
        });
    }

    /**
     * Applies final damage metrics and visual/audio feedback to the dummy and clients.
     */
    public void applyDamageToDummy(Player player, TrainingDummy dummy, double damage, boolean isCrit, Location hitLoc, boolean isProjectile) {
        // Record combat metrics in dummy
        dummy.recordHit(damage, isCrit);

        // Audio & Visual particles at dummy location
        Location dLoc = dummy.getLocation();
        if (dLoc.getWorld() != null) {
            Location fxLoc = hitLoc != null ? hitLoc : dLoc.clone().add(0, 1.0, 0);
            if (isCrit) {
                dLoc.getWorld().spawnParticle(Particle.CRIT, fxLoc, 15, 0.3, 0.3, 0.3, 0.1);
                player.playSound(dLoc, Sound.ENTITY_PLAYER_ATTACK_CRIT, 0.9f, 1.15f);
            } else if (isProjectile) {
                dLoc.getWorld().spawnParticle(Particle.CRIT, fxLoc, 6, 0.2, 0.2, 0.2, 0.05);
                player.playSound(dLoc, Sound.ENTITY_ARROW_HIT_PLAYER, 0.9f, 1.0f);
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
    }

    public static boolean isUndead(String type) {
        if (type == null) return false;
        String t = type.toUpperCase();
        return t.contains("ZOMBIE") || t.contains("SKELETON") || t.contains("WITHER")
                || t.contains("DROWNED") || t.contains("STRAY") || t.contains("HUSK")
                || t.contains("PHANTOM") || t.contains("ZOGLIN");
    }

    public static boolean isArthropod(String type) {
        if (type == null) return false;
        String t = type.toUpperCase();
        return t.contains("SPIDER") || t.contains("SILVERFISH") || t.contains("ENDERMITE") || t.contains("BEE");
    }

    public static boolean isAquatic(String type) {
        if (type == null) return false;
        String t = type.toUpperCase();
        return t.contains("GUARDIAN") || t.contains("DROWNED") || t.contains("TURTLE")
                || t.contains("DOLPHIN") || t.contains("FISH") || t.contains("SQUID");
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
        playerLastEventTime.clear();
        playerLastAttackTime.clear();
        activeProjectiles.clear();
    }
}
