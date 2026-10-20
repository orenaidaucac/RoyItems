package com.github.orenaidaucac.royItems.item;

import com.github.orenaidaucac.royItems.mechanics.PotionEffectData;
import com.github.orenaidaucac.royItems.stat.StatModifier;
import com.github.orenaidaucac.royItems.stat.StatType;
import org.bukkit.attribute.Attribute;
import org.bukkit.enchantments.Enchantment;

import java.util.*;

/**
 * Definition of a RoyItem loaded from item/<TYPE>.yml.
 */
public class ItemDefinition {

    private final ItemType type;
    private final String id;

    // Display & Material
    private String material = "DIAMOND_SWORD";
    private String name = "";
    private List<String> lore = new ArrayList<>();
    private String loreFormat = "default";
    private boolean unbreakable = false;
    private boolean glow = false;
    private String itemModel = null;

    // Stats & Attributes
    private Map<StatType, StatModifier> stats = new EnumMap<>(StatType.class);
    private Map<Attribute, StatModifier> vanillaAttributes = new HashMap<>();

    // Enchants & Tooltip
    private Map<Enchantment, Integer> enchantments = new HashMap<>();
    private boolean hideEnchantments = false;
    private boolean hideAttributes = false;

    // Requirements & Permissions
    private String permission = null;
    private boolean drop = true;
    private int levelRequired = 0;
    private String mmocoreClass = null;
    private int mmocoreLevel = 0;
    private String auraSkill = null;
    private int auraSkillLevel = 0;
    private List<String> worldRequired = new ArrayList<>();
    private List<String> biomeRequired = new ArrayList<>();

    // MMORPG Categorization
    private String itemTier = null;
    private String itemSet = null;

    // Mechanics
    private List<PotionEffectData> effects = new ArrayList<>();
    private String sound = null;
    private float soundVolume = 1.0f;
    private float soundPitch = 1.0f;
    private List<String> gemSockets = new ArrayList<>(); // e.g. ['1', 'A', 'ABC']
    private double cooldown = 0.0; // Seconds
    private String expired = null; // Duration string e.g. "3 days"

    // Armor specific
    private String trimMaterial = null;
    private String trimPattern = null;
    private boolean hideArmorTrim = false;
    private String dyeColor = null;
    private boolean hideDye = false;

    // Gem specific properties
    private String gemId = null;
    private Map<StatType, StatModifier> gemStats = new EnumMap<>(StatType.class);

    // LiveUpdate version tracking
    private int version = 1;

    public ItemDefinition(ItemType type, String id) {
        this.type = type;
        this.id = id;
    }

    public ItemType getType() { return type; }
    public String getId() { return id; }

    public String getMaterial() { return material; }
    public void setMaterial(String material) { this.material = material; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public List<String> getLore() { return lore; }
    public void setLore(List<String> lore) { this.lore = lore; }

    public String getLoreFormat() { return loreFormat; }
    public void setLoreFormat(String loreFormat) { this.loreFormat = loreFormat; }

    public boolean isUnbreakable() { return unbreakable; }
    public void setUnbreakable(boolean unbreakable) { this.unbreakable = unbreakable; }

    public boolean isGlow() { return glow; }
    public void setGlow(boolean glow) { this.glow = glow; }

    public String getItemModel() { return itemModel; }
    public void setItemModel(String itemModel) { this.itemModel = itemModel; }

    public Map<StatType, StatModifier> getStats() { return stats; }
    public void setStats(Map<StatType, StatModifier> stats) { this.stats = stats; }

    public Map<Attribute, StatModifier> getVanillaAttributes() { return vanillaAttributes; }
    public void setVanillaAttributes(Map<Attribute, StatModifier> vanillaAttributes) { this.vanillaAttributes = vanillaAttributes; }

    public Map<Enchantment, Integer> getEnchantments() { return enchantments; }
    public void setEnchantments(Map<Enchantment, Integer> enchantments) { this.enchantments = enchantments; }

    public boolean isHideEnchantments() { return hideEnchantments; }
    public void setHideEnchantments(boolean hideEnchantments) { this.hideEnchantments = hideEnchantments; }

    public boolean isHideAttributes() { return hideAttributes; }
    public void setHideAttributes(boolean hideAttributes) { this.hideAttributes = hideAttributes; }

    public String getPermission() { return permission; }
    public void setPermission(String permission) { this.permission = permission; }

    public boolean isDrop() { return drop; }
    public void setDrop(boolean drop) { this.drop = drop; }

    public int getLevelRequired() { return levelRequired; }
    public void setLevelRequired(int levelRequired) { this.levelRequired = levelRequired; }

    public String getMmocoreClass() { return mmocoreClass; }
    public void setMmocoreClass(String mmocoreClass) { this.mmocoreClass = mmocoreClass; }

    public int getMmocoreLevel() { return mmocoreLevel; }
    public void setMmocoreLevel(int mmocoreLevel) { this.mmocoreLevel = mmocoreLevel; }

    public String getAuraSkill() { return auraSkill; }
    public void setAuraSkill(String auraSkill) { this.auraSkill = auraSkill; }

    public int getAuraSkillLevel() { return auraSkillLevel; }
    public void setAuraSkillLevel(int auraSkillLevel) { this.auraSkillLevel = auraSkillLevel; }

    public List<String> getWorldRequired() { return worldRequired; }
    public void setWorldRequired(List<String> worldRequired) { this.worldRequired = worldRequired; }

    public List<String> getBiomeRequired() { return biomeRequired; }
    public void setBiomeRequired(List<String> biomeRequired) { this.biomeRequired = biomeRequired; }

    public String getItemTier() { return itemTier; }
    public void setItemTier(String itemTier) { this.itemTier = itemTier; }

    public String getItemSet() { return itemSet; }
    public void setItemSet(String itemSet) { this.itemSet = itemSet; }

    public List<PotionEffectData> getEffects() { return effects; }
    public void setEffects(List<PotionEffectData> effects) { this.effects = effects; }

    public String getSound() { return sound; }
    public void setSound(String sound) { this.sound = sound; }

    public float getSoundVolume() { return soundVolume; }
    public void setSoundVolume(float soundVolume) { this.soundVolume = soundVolume; }

    public float getSoundPitch() { return soundPitch; }
    public void setSoundPitch(float soundPitch) { this.soundPitch = soundPitch; }

    public List<String> getGemSockets() { return gemSockets; }
    public void setGemSockets(List<String> gemSockets) { this.gemSockets = gemSockets; }

    public double getCooldown() { return cooldown; }
    public void setCooldown(double cooldown) { this.cooldown = cooldown; }

    public String getExpired() { return expired; }
    public void setExpired(String expired) { this.expired = expired; }

    public String getTrimMaterial() { return trimMaterial; }
    public void setTrimMaterial(String trimMaterial) { this.trimMaterial = trimMaterial; }

    public String getTrimPattern() { return trimPattern; }
    public void setTrimPattern(String trimPattern) { this.trimPattern = trimPattern; }

    public boolean isHideArmorTrim() { return hideArmorTrim; }
    public void setHideArmorTrim(boolean hideArmorTrim) { this.hideArmorTrim = hideArmorTrim; }

    public String getDyeColor() { return dyeColor; }
    public void setDyeColor(String dyeColor) { this.dyeColor = dyeColor; }

    public boolean isHideDye() { return hideDye; }
    public void setHideDye(boolean hideDye) { this.hideDye = hideDye; }

    public String getGemId() { return gemId; }
    public void setGemId(String gemId) { this.gemId = gemId; }

    public Map<StatType, StatModifier> getGemStats() { return gemStats; }
    public void setGemStats(Map<StatType, StatModifier> gemStats) { this.gemStats = gemStats; }

    public int getVersion() { return version; }
    public void setVersion(int version) { this.version = version; }

    public StatModifier getStat(StatType type) {
        return stats.getOrDefault(type, new StatModifier(0, 0));
    }

    public void setStat(StatType type, StatModifier modifier) {
        if (modifier == null || modifier.isEmpty()) {
            stats.remove(type);
        } else {
            stats.put(type, modifier);
        }
    }
}
