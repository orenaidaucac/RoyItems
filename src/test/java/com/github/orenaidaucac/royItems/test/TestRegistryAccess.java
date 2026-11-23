package com.github.orenaidaucac.royItems.test;

import io.papermc.paper.registry.RegistryAccess;
import io.papermc.paper.registry.RegistryKey;
import org.bukkit.Keyed;
import org.bukkit.NamespacedKey;
import org.bukkit.Registry;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.EntityCategory;
import org.bukkit.entity.EntityType;
import org.bukkit.inventory.EquipmentSlotGroup;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.ItemType;
import io.papermc.paper.enchantments.EnchantmentRarity;
import io.papermc.paper.registry.set.RegistryKeySet;
import net.kyori.adventure.text.Component;

import java.lang.reflect.Proxy;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Test SPI provider for Paper's RegistryAccess to allow Bukkit Registry initializations in offline unit tests.
 */
public class TestRegistryAccess implements RegistryAccess {

    private final Map<NamespacedKey, Enchantment> dummyEnchants = new java.util.HashMap<>();
    private final Map<NamespacedKey, Object> otherObjects = new java.util.HashMap<>();

    @SuppressWarnings("unchecked")
    @Override
    public <T extends Keyed> Registry<T> getRegistry(Class<T> aClass) {
        return (Registry<T>) createRegistry(aClass);
    }

    @SuppressWarnings("unchecked")
    @Override
    public <T extends Keyed> Registry<T> getRegistry(RegistryKey<T> registryKey) {
        Class<?> type = null;
        if (RegistryKey.ITEM.equals(registryKey)) {
            type = ItemType.class;
        } else if (RegistryKey.BLOCK.equals(registryKey)) {
            type = org.bukkit.block.BlockType.class;
        } else if (RegistryKey.ENCHANTMENT.equals(registryKey)) {
            type = Enchantment.class;
        }
        return (Registry<T>) createRegistry(type);
    }

    private synchronized Registry<?> createRegistry(Class<?> targetType) {
        return (Registry<?>) Proxy.newProxyInstance(
                Registry.class.getClassLoader(),
                new Class<?>[]{Registry.class},
                (proxy, method, args) -> {
                    String methodName = method.getName();
                    if ("getOrThrow".equals(methodName) || "get".equals(methodName)) {
                        NamespacedKey key = null;
                        if (args != null && args.length > 0) {
                            if (args[0] instanceof NamespacedKey) {
                                key = (NamespacedKey) args[0];
                            } else if (args[0] != null) {
                                key = NamespacedKey.fromString(args[0].toString());
                            }
                        }
                        if (key != null) {
                            return getOrCreateKeyed(key, targetType);
                        }
                    }
                    if ("getKey".equals(methodName) && args != null && args.length > 0 && args[0] instanceof Keyed) {
                        return ((Keyed) args[0]).getKey();
                    }
                    return null;
                }
        );
    }

    private synchronized Object getOrCreateKeyed(NamespacedKey key, Class<?> targetType) {
        if (targetType != null && Enchantment.class.isAssignableFrom(targetType)) {
            Enchantment existing = dummyEnchants.get(key);
            if (existing == null) {
                existing = new TestEnchantment(key);
                dummyEnchants.put(key, existing);
            }
            return existing;
        }

        // Check if key is requested during Enchantment initialization
        if (key.getNamespace().equals("minecraft")) {
            Enchantment existing = dummyEnchants.get(key);
            if (existing != null) return existing;

            for (StackTraceElement elem : Thread.currentThread().getStackTrace()) {
                if (elem.getClassName().contains("org.bukkit.enchantments.Enchantment")) {
                    Enchantment ench = new TestEnchantment(key);
                    dummyEnchants.put(key, ench);
                    return ench;
                }
            }
        }

        // For other types (like BlockType, ItemType, etc.), create a dynamic proxy of Keyed / targetType if interface
        Object existing = otherObjects.get(key);
        if (existing != null) return existing;

        if (targetType == null) {
            for (StackTraceElement elem : Thread.currentThread().getStackTrace()) {
                if (elem.getClassName().contains("Material") || elem.getMethodName().contains("asItemType")) {
                    targetType = ItemType.class;
                    break;
                }
            }
        }

        Set<Class<?>> interfaces = new LinkedHashSet<>();
        if (targetType != null && targetType.isInterface()) {
            interfaces.add(targetType);
        }
        interfaces.add(Keyed.class);

        Object mockObj = Proxy.newProxyInstance(
                Keyed.class.getClassLoader(),
                interfaces.toArray(new Class<?>[0]),
                (p, m, a) -> {
                    if ("getKey".equals(m.getName())) return key;
                    if ("key".equals(m.getName())) return key;
                    if ("toString".equals(m.getName())) return key.toString();
                    if ("equals".equals(m.getName()) && a != null && a.length > 0) return p == a[0];
                    if ("hashCode".equals(m.getName())) return key.hashCode();
                    if ("getMaxStackSize".equals(m.getName())) return 64;
                    if ("getMaxDurability".equals(m.getName())) return (short) 0;
                    if ("getItemMetaClass".equals(m.getName())) return org.bukkit.inventory.meta.ItemMeta.class;
                    if ("asMaterial".equals(m.getName())) return org.bukkit.Material.matchMaterial(key.getKey());
                    if ("typed".equals(m.getName())) return p;
                    return null;
                }
        );
        otherObjects.put(key, mockObj);
        return mockObj;
    }

    public static class TestEnchantment extends Enchantment {
        private final NamespacedKey key;

        public TestEnchantment(NamespacedKey key) {
            this.key = key;
        }

        @Override
        public NamespacedKey getKey() {
            return key;
        }

        @Override
        public String getName() {
            return key != null ? key.getKey().toUpperCase() : "TEST";
        }

        @Override
        public int getMaxLevel() {
            if (key != null) {
                String k = key.getKey().toLowerCase();
                if (k.equals("mending") || k.equals("silk_touch") || k.contains("curse")) {
                    return 1;
                }
            }
            return 5;
        }

        @Override
        public int getStartLevel() {
            return 1;
        }

        @Override
        public org.bukkit.enchantments.EnchantmentTarget getItemTarget() {
            return null;
        }

        @Override
        public boolean isTreasure() {
            return false;
        }

        @Override
        public boolean isCursed() {
            if (key != null) {
                return key.getKey().toLowerCase().contains("curse");
            }
            return false;
        }

        @Override
        public boolean conflictsWith(Enchantment enchantment) {
            return false;
        }

        @Override
        public boolean canEnchantItem(ItemStack itemStack) {
            return true;
        }

        @Override
        public Component displayName(int i) {
            return Component.text(getName());
        }

        @Override
        public boolean isTradeable() {
            return true;
        }

        @Override
        public boolean isDiscoverable() {
            return true;
        }

        @Override
        public int getMinModifiedCost(int i) {
            return 1;
        }

        @Override
        public int getMaxModifiedCost(int i) {
            return 1;
        }

        @Override
        public int getAnvilCost() {
            return 1;
        }

        @Override
        public EnchantmentRarity getRarity() {
            return EnchantmentRarity.COMMON;
        }

        @Override
        public float getDamageIncrease(int i, EntityCategory entityCategory) {
            return 0;
        }

        @Override
        public float getDamageIncrease(int i, EntityType entityType) {
            return 0;
        }

        @Override
        public Set<EquipmentSlotGroup> getActiveSlotGroups() {
            return Collections.emptySet();
        }

        @Override
        public Component description() {
            return Component.text(getName());
        }

        @Override
        public RegistryKeySet<ItemType> getSupportedItems() {
            return null;
        }

        @Override
        public RegistryKeySet<ItemType> getPrimaryItems() {
            return null;
        }

        @Override
        public int getWeight() {
            return 1;
        }

        @Override
        public RegistryKeySet<Enchantment> getExclusiveWith() {
            return null;
        }

        @Override
        public String getTranslationKey() {
            return "enchantment.minecraft." + (key != null ? key.getKey() : "test");
        }

        @Override
        public String translationKey() {
            return getTranslationKey();
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (!(o instanceof Enchantment)) return false;
            Enchantment that = (Enchantment) o;
            return getKey() != null && getKey().equals(that.getKey());
        }

        @Override
        public int hashCode() {
            return getKey() != null ? getKey().hashCode() : 0;
        }
    }
}
