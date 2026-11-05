package com.github.orenaidaucac.royItems.integration;

import org.bukkit.Bukkit;
import org.bukkit.NamespacedKey;
import org.bukkit.enchantments.Enchantment;

import java.lang.reflect.Method;
import java.util.logging.Level;

/**
 * Robust soft-dependency hook for EcoEnchants (Auxilor).
 * Dynamically resolves EcoEnchants custom enchantments and retrieves their official
 * colored display names, tier styling, and formatting via reflection.
 */
public class EcoEnchantsHook {

    private boolean available = false;
    private Class<?> ecoEnchantsClass;
    private Method getByKeyMethod;
    private Method getByNameMethod;

    public boolean setup() {
        try {
            if (Bukkit.getServer() == null || Bukkit.getPluginManager() == null || Bukkit.getPluginManager().getPlugin("EcoEnchants") == null) {
                available = false;
                return false;
            }
        } catch (Throwable t) {
            available = false;
            return false;
        }

        try {
            // Modern EcoEnchants (v11.0.0+)
            try {
                ecoEnchantsClass = Class.forName("com.willfp.ecoenchants.enchant.EcoEnchants");
            } catch (ClassNotFoundException e) {
                // Legacy EcoEnchants (< v11.0.0)
                ecoEnchantsClass = Class.forName("com.willfp.ecoenchants.enchantments.EcoEnchants");
            }

            try {
                getByKeyMethod = ecoEnchantsClass.getMethod("getByKey", NamespacedKey.class);
            } catch (NoSuchMethodException ignored) {}

            try {
                getByNameMethod = ecoEnchantsClass.getMethod("getByName", String.class);
            } catch (NoSuchMethodException ignored) {}

            available = (getByKeyMethod != null || getByNameMethod != null);
            return available;
        } catch (Throwable t) {
            available = false;
            return false;
        }
    }

    public boolean isAvailable() {
        return available;
    }

    /**
     * Checks if a Bukkit Enchantment is a custom enchantment from EcoEnchants.
     */
    public boolean isEcoEnchant(Enchantment ench) {
        if (ench == null) return false;
        if (ench.getKey().getNamespace().equalsIgnoreCase("ecoenchants")) {
            return true;
        }
        if (!available) return false;
        return getEcoEnchantObj(ench) != null;
    }

    /**
     * Retrieves the EcoEnchant wrapper instance via reflection.
     */
    public Object getEcoEnchantObj(Enchantment ench) {
        if (!available || ench == null) return null;
        try {
            if (getByKeyMethod != null) {
                Object res = getByKeyMethod.invoke(null, ench.getKey());
                if (res != null) return res;
            }
            if (getByNameMethod != null) {
                Object res = getByNameMethod.invoke(null, ench.getKey().getKey());
                if (res != null) return res;
            }
        } catch (Throwable ignored) {}
        return null;
    }

    /**
     * Returns the formatted display name of an EcoEnchant, prioritizing official EcoEnchants formatting.
     */
    public String getFormattedName(Enchantment ench, int level) {
        if (!available || ench == null) return null;
        Object ecoEnchant = getEcoEnchantObj(ench);
        if (ecoEnchant == null) return null;

        try {
            // 1. Try getFormattedName(int)
            try {
                Method m = ecoEnchant.getClass().getMethod("getFormattedName", int.class);
                Object res = m.invoke(ecoEnchant, level);
                if (res instanceof String s && !s.isEmpty()) {
                    return s;
                }
            } catch (NoSuchMethodException ignored) {}

            // 2. Try getFormattedName()
            try {
                Method m = ecoEnchant.getClass().getMethod("getFormattedName");
                Object res = m.invoke(ecoEnchant);
                if (res instanceof String s && !s.isEmpty()) {
                    return s;
                }
            } catch (NoSuchMethodException ignored) {}

            // 3. Try getDisplayName()
            try {
                Method m = ecoEnchant.getClass().getMethod("getDisplayName");
                Object res = m.invoke(ecoEnchant);
                if (res instanceof String s && !s.isEmpty()) {
                    return s;
                }
            } catch (NoSuchMethodException ignored) {}
        } catch (Throwable ignored) {}

        return null;
    }

    /**
     * Returns the display name of an EcoEnchant.
     */
    public String getDisplayName(Enchantment ench) {
        return getFormattedName(ench, 1);
    }
}
