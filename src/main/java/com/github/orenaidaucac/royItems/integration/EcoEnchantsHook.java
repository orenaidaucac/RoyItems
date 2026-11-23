package com.github.orenaidaucac.royItems.integration;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.EnchantmentStorageMeta;
import org.bukkit.inventory.meta.ItemMeta;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Robust, reflection-based soft-dependency integration for EcoEnchants (Auxilor).
 * Compatible with Auxilor EcoEnchants modern (v11+ / 2026.x) and legacy versions.
 * Supports:
 * - Real-time custom enchantment identification
 * - Official display-name and rarity resolution with Hex / MiniMessage / Unicode preserved
 * - Candidate querying filtered by Rarity ID or Type ID
 * - Level limits calculation
 * - Dynamic generation of valid Enchanted Books with preserved metadata
 */
public class EcoEnchantsHook {

    private boolean available = false;
    private Class<?> ecoEnchantsClass;
    private Object ecoEnchantsInstance;
    private Method valuesMethod;
    private Method getByIDMethod;
    private Method getMethod;
    private Method getByNameMethod;
    private Method getByKeyMethod;

    private Class<?> formattingKtClass;
    private Method getFormattedNameKtMethod;

    private Class<?> enchantedBookBuilderClass;
    private Constructor<?> enchantedBookBuilderConstructor;
    private Method addStoredEnchantMethod;
    private Method buildBookMethod;

    // Fast O(1) reflection cache
    private final Map<Enchantment, Object> ecoObjCache = new ConcurrentHashMap<>();

    private static final int[] ROMAN_VALUES = {1000, 900, 500, 400, 100, 90, 50, 40, 10, 9, 5, 4, 1};
    private static final String[] ROMAN_SYMBOLS = {"M", "CM", "D", "CD", "C", "XC", "L", "XL", "X", "IX", "V", "IV", "I"};

    /**
     * Data wrapper for an eligible EcoEnchants enchantment candidate.
     */
    public static class EcoEnchantCandidate {
        private final Enchantment enchantment;
        private final String id;
        private final String rawDisplayName;
        private final String rarityId;
        private final String rarityDisplayName;
        private final String typeId;
        private final String typeFormat;
        private final int maxLevel;
        private final Object ecoEnchantObj;

        public EcoEnchantCandidate(Enchantment enchantment, String id, String rawDisplayName,
                                   String rarityId, String rarityDisplayName,
                                   String typeId, String typeFormat, int maxLevel, Object ecoEnchantObj) {
            this.enchantment = enchantment;
            this.id = id != null ? id : "";
            this.rawDisplayName = rawDisplayName != null ? rawDisplayName : this.id;
            this.rarityId = rarityId != null ? rarityId : "";
            this.rarityDisplayName = rarityDisplayName != null ? rarityDisplayName : this.rarityId;
            this.typeId = typeId != null ? typeId : "";
            this.typeFormat = typeFormat != null ? typeFormat : "";
            this.maxLevel = Math.max(1, maxLevel);
            this.ecoEnchantObj = ecoEnchantObj;
        }

        public Enchantment getEnchantment() {
            return enchantment;
        }

        public String getId() {
            return id;
        }

        public String getRawDisplayName() {
            return rawDisplayName;
        }

        public String getRarityId() {
            return rarityId;
        }

        public String getRarityDisplayName() {
            return rarityDisplayName;
        }

        public String getTypeId() {
            return typeId;
        }

        public String getTypeFormat() {
            return typeFormat;
        }

        public int getMaxLevel() {
            return maxLevel;
        }

        public Object getEcoEnchantObj() {
            return ecoEnchantObj;
        }

        /**
         * Returns formatted display name preserving Roman numerals and configured styling.
         */
        public String getFormattedName(int level) {
            String name = (rawDisplayName != null && !rawDisplayName.isBlank()) ? rawDisplayName : id;
            String prefix = (typeFormat != null && !typeFormat.isBlank()) ? typeFormat : "";
            String levelStr = (maxLevel == 1 && level == 1) ? "" : " " + toRoman(level);

            if (name.startsWith("&") || name.startsWith("§") || name.startsWith("<") || name.startsWith("{")) {
                return name + levelStr;
            }
            return prefix + name + levelStr;
        }
    }

    public boolean setup() {
        ecoObjCache.clear();
        try {
            if (Bukkit.getServer() == null || Bukkit.getPluginManager() == null
                    || Bukkit.getPluginManager().getPlugin("EcoEnchants") == null) {
                available = false;
                return false;
            }
        } catch (Throwable t) {
            available = false;
            return false;
        }

        try {
            // 1. Resolve EcoEnchants class
            try {
                ecoEnchantsClass = Class.forName("com.willfp.ecoenchants.enchant.EcoEnchants");
            } catch (ClassNotFoundException e) {
                ecoEnchantsClass = Class.forName("com.willfp.ecoenchants.enchantments.EcoEnchants");
            }

            // 2. Resolve singleton INSTANCE if Kotlin object
            try {
                Field f = ecoEnchantsClass.getField("INSTANCE");
                ecoEnchantsInstance = f.get(null);
            } catch (Throwable ignored) {
                ecoEnchantsInstance = null;
            }

            // 3. Resolve lookup methods
            try {
                valuesMethod = ecoEnchantsClass.getMethod("values");
            } catch (NoSuchMethodException ignored) {}

            try {
                getByIDMethod = ecoEnchantsClass.getMethod("getByID", String.class);
            } catch (NoSuchMethodException ignored) {}

            try {
                getMethod = ecoEnchantsClass.getMethod("get", String.class);
            } catch (NoSuchMethodException ignored) {}

            try {
                getByNameMethod = ecoEnchantsClass.getMethod("getByName", String.class);
            } catch (NoSuchMethodException ignored) {}

            try {
                getByKeyMethod = ecoEnchantsClass.getMethod("getByKey", NamespacedKey.class);
            } catch (NoSuchMethodException ignored) {}

            // 4. Resolve EnchantmentFormattingKt
            try {
                formattingKtClass = Class.forName("com.willfp.ecoenchants.display.EnchantmentFormattingKt");
                for (Method m : formattingKtClass.getMethods()) {
                    if (m.getName().equals("getFormattedName") && m.getParameterCount() == 2
                            && m.getParameterTypes()[1] == int.class) {
                        getFormattedNameKtMethod = m;
                        break;
                    }
                }
            } catch (Throwable ignored) {}

            // 5. Resolve EnchantedBookBuilder
            try {
                enchantedBookBuilderClass = Class.forName("com.willfp.eco.core.items.builder.EnchantedBookBuilder");
                enchantedBookBuilderConstructor = enchantedBookBuilderClass.getConstructor();
                addStoredEnchantMethod = enchantedBookBuilderClass.getMethod("addStoredEnchantment", Enchantment.class, int.class);
                buildBookMethod = enchantedBookBuilderClass.getMethod("build");
            } catch (Throwable ignored) {}

            available = (ecoEnchantsClass != null);
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
     * Retrieves the EcoEnchant wrapper instance via reflection with caching.
     */
    public Object getEcoEnchantObj(Enchantment ench) {
        if (!available || ench == null) return null;
        Object cached = ecoObjCache.get(ench);
        if (cached != null) return cached;

        String keyName = ench.getKey().getKey();
        NamespacedKey nsKey = ench.getKey();

        try {
            // 1. Try getByID(key)
            if (getByIDMethod != null) {
                try {
                    Object res = getByIDMethod.invoke(ecoEnchantsInstance, keyName);
                    if (res != null) {
                        ecoObjCache.put(ench, res);
                        return res;
                    }
                } catch (Throwable ignored) {}
            }

            // 2. Try get(key)
            if (getMethod != null) {
                try {
                    Object res = getMethod.invoke(ecoEnchantsInstance, keyName);
                    if (res != null) {
                        ecoObjCache.put(ench, res);
                        return res;
                    }
                } catch (Throwable ignored) {}
            }

            // 3. Try getByName(key)
            if (getByNameMethod != null) {
                try {
                    Object res = getByNameMethod.invoke(ecoEnchantsInstance, keyName);
                    if (res != null) {
                        ecoObjCache.put(ench, res);
                        return res;
                    }
                } catch (Throwable ignored) {}
            }

            // 4. Try getByKey(nsKey)
            if (getByKeyMethod != null) {
                try {
                    Object res = getByKeyMethod.invoke(ecoEnchantsInstance, nsKey);
                    if (res != null) {
                        ecoObjCache.put(ench, res);
                        return res;
                    }
                } catch (Throwable ignored) {}
            }

            // 5. Fallback: match via all candidates
            List<EcoEnchantCandidate> candidates = getAllCandidates();
            for (EcoEnchantCandidate c : candidates) {
                if (c.getEnchantment() != null && c.getEnchantment().equals(ench)) {
                    Object obj = c.getEcoEnchantObj();
                    if (obj != null) {
                        ecoObjCache.put(ench, obj);
                        return obj;
                    }
                }
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

        // 1. Try official Kotlin extension getFormattedName(EcoEnchantLike, int)
        if (getFormattedNameKtMethod != null) {
            try {
                Object res = getFormattedNameKtMethod.invoke(null, ecoEnchant, level);
                if (res instanceof String s && !s.isEmpty()) {
                    return s;
                }
            } catch (Throwable ignored) {}
        }

        // 2. Try direct instance methods on wrapper
        try {
            Method m = ecoEnchant.getClass().getMethod("getFormattedName", int.class);
            Object res = m.invoke(ecoEnchant, level);
            if (res instanceof String s && !s.isEmpty()) {
                return s;
            }
        } catch (Throwable ignored) {}

        try {
            Method m = ecoEnchant.getClass().getMethod("getFormattedName");
            Object res = m.invoke(ecoEnchant);
            if (res instanceof String s && !s.isEmpty()) {
                return s;
            }
        } catch (Throwable ignored) {}

        // 3. Fallback: build from raw display name, type format, and Roman numeral
        EcoEnchantCandidate candidate = buildCandidateFromObj(ecoEnchant);
        if (candidate != null) {
            return candidate.getFormattedName(level);
        }

        return null;
    }

    /**
     * Strips trailing Roman numerals (e.g. " III", " X") or digits (e.g. " 3") from an enchantment name.
     */
    public static String stripTrailingLevel(String text) {
        if (text == null || text.isBlank()) return text;
        return text.replaceAll("(?i)\\s+([IVXLCDM]+|\\d+)$", "").trim();
    }

    /**
     * Returns the maximum level of an EcoEnchant.
     */
    public int getMaxLevel(Enchantment ench) {
        if (!available || ench == null) {
            try {
                return ench != null ? ench.getMaxLevel() : 1;
            } catch (Throwable t) {
                return 1;
            }
        }
        Object ecoEnchant = getEcoEnchantObj(ench);
        if (ecoEnchant == null) {
            try {
                return ench.getMaxLevel();
            } catch (Throwable ignored) {
                return 1;
            }
        }
        try {
            Method m = ecoEnchant.getClass().getMethod("getMaximumLevel");
            Object res = m.invoke(ecoEnchant);
            if (res instanceof Number n) return n.intValue();
        } catch (Throwable ignored) {}
        try {
            Method m = ecoEnchant.getClass().getMethod("getMaxLevel");
            Object res = m.invoke(ecoEnchant);
            if (res instanceof Number n) return n.intValue();
        } catch (Throwable ignored) {}
        EcoEnchantCandidate c = buildCandidateFromObj(ecoEnchant);
        if (c != null && c.getMaxLevel() > 0) return c.getMaxLevel();
        try {
            return ench.getMaxLevel();
        } catch (Throwable ignored) {
            return 1;
        }
    }

    /**
     * Returns the rarity display name of an EcoEnchant if available.
     */
    public String getRarityDisplayName(Enchantment ench) {
        if (!available || ench == null) return null;
        Object ecoEnchant = getEcoEnchantObj(ench);
        if (ecoEnchant == null) return null;
        try {
            Method m = ecoEnchant.getClass().getMethod("getEnchantmentRarity");
            Object rarityObj = m.invoke(ecoEnchant);
            if (rarityObj != null) {
                Method dM = rarityObj.getClass().getMethod("getDisplayName");
                Object res = dM.invoke(rarityObj);
                if (res instanceof String s && !s.isBlank()) return s;
            }
        } catch (Throwable ignored) {}
        EcoEnchantCandidate c = buildCandidateFromObj(ecoEnchant);
        return (c != null) ? c.getRarityDisplayName() : null;
    }

    /**
     * Returns the clean display name of an EcoEnchant without trailing level numerals.
     */
    public String getDisplayName(Enchantment ench) {
        if (!available || ench == null) return null;
        Object ecoEnchant = getEcoEnchantObj(ench);
        if (ecoEnchant == null) return null;

        try {
            Method m = ecoEnchant.getClass().getMethod("getRawDisplayName");
            Object res = m.invoke(ecoEnchant);
            if (res instanceof String s && !s.isEmpty()) {
                return stripTrailingLevel(s);
            }
        } catch (Throwable ignored) {}

        try {
            Method m = ecoEnchant.getClass().getMethod("getDisplayName");
            Object res = m.invoke(ecoEnchant);
            if (res instanceof String s && !s.isEmpty()) {
                return stripTrailingLevel(s);
            }
        } catch (Throwable ignored) {}

        EcoEnchantCandidate c = buildCandidateFromObj(ecoEnchant);
        if (c != null && c.getRawDisplayName() != null && !c.getRawDisplayName().isBlank()) {
            return stripTrailingLevel(c.getRawDisplayName());
        }

        String formatted = getFormattedName(ench, 1);
        return stripTrailingLevel(formatted);
    }

    /**
     * Returns the raw Rarity ID of an EcoEnchant (e.g. "common", "legendary", "special").
     */
    public String getRarityId(Enchantment ench) {
        if (!available || ench == null) return null;
        Object ecoEnchant = getEcoEnchantObj(ench);
        if (ecoEnchant == null) return null;
        EcoEnchantCandidate c = buildCandidateFromObj(ecoEnchant);
        return (c != null) ? c.getRarityId() : null;
    }

    /**
     * Returns the Type ID of an EcoEnchant (e.g. "normal", "special").
     */
    public String getTypeId(Enchantment ench) {
        if (!available || ench == null) return null;
        Object ecoEnchant = getEcoEnchantObj(ench);
        if (ecoEnchant == null) return null;
        EcoEnchantCandidate c = buildCandidateFromObj(ecoEnchant);
        return (c != null) ? c.getTypeId() : null;
    }

    /**
     * Returns all registered EcoEnchants candidates available on the server.
     */
    public List<EcoEnchantCandidate> getAllCandidates() {
        List<EcoEnchantCandidate> list = new ArrayList<>();
        if (!available) return list;

        // 1. Try EcoEnchants.values()
        if (valuesMethod != null) {
            try {
                Object res = valuesMethod.invoke(ecoEnchantsInstance);
                if (res instanceof Collection<?> col) {
                    for (Object obj : col) {
                        EcoEnchantCandidate c = buildCandidateFromObj(obj);
                        if (c != null && c.getEnchantment() != null) {
                            list.add(c);
                        }
                    }
                    if (!list.isEmpty()) {
                        return list;
                    }
                }
            } catch (Throwable ignored) {}
        }

        // 2. Fallback: inspect Bukkit's enchantment registry for ecoenchants namespace
        try {
            for (Enchantment ench : org.bukkit.Registry.ENCHANTMENT) {
                if (ench != null && ench.getKey().getNamespace().equalsIgnoreCase("ecoenchants")) {
                    Object obj = getEcoEnchantObj(ench);
                    if (obj != null) {
                        EcoEnchantCandidate c = buildCandidateFromObj(obj);
                        if (c != null) {
                            list.add(c);
                        }
                    }
                }
            }
        } catch (Throwable ignored) {}

        return list;
    }

    /**
     * Returns a filtered list of EcoEnchants candidates matching a configured Rarity ID or Type ID.
     */
    public List<EcoEnchantCandidate> getCandidatesByFilter(String rarityFilter, String typeFilter, int minLevel) {
        List<EcoEnchantCandidate> all = getAllCandidates();
        List<EcoEnchantCandidate> result = new ArrayList<>();

        boolean hasRarity = (rarityFilter != null && !rarityFilter.isBlank());
        boolean hasType = (typeFilter != null && !typeFilter.isBlank());
        int requiredMinLevel = Math.max(1, minLevel);

        String cleanRarityFilter = hasRarity ? stripColor(rarityFilter).trim() : null;
        String cleanTypeFilter = hasType ? stripColor(typeFilter).trim() : null;

        for (EcoEnchantCandidate candidate : all) {
            if (candidate.getMaxLevel() < requiredMinLevel) {
                continue;
            }

            if (hasRarity) {
                String rId = candidate.getRarityId();
                String rDisplay = candidate.getRarityDisplayName();
                String cleanRDisplay = stripColor(rDisplay);

                boolean matches = rId.equalsIgnoreCase(rarityFilter.trim())
                        || (cleanRDisplay != null && cleanRDisplay.equalsIgnoreCase(cleanRarityFilter))
                        || (rDisplay != null && rDisplay.equalsIgnoreCase(rarityFilter.trim()));
                if (!matches) {
                    continue;
                }
            }

            if (hasType) {
                String tId = candidate.getTypeId();
                String cleanTId = stripColor(tId);
                boolean matches = tId.equalsIgnoreCase(typeFilter.trim())
                        || (cleanTId != null && cleanTId.equalsIgnoreCase(cleanTypeFilter));
                if (!matches) {
                    continue;
                }
            }

            result.add(candidate);
        }

        return result;
    }

    /**
     * Creates a valid Enchanted Book with the specified EcoEnchant and level.
     * Prioritizes Eco's EnchantedBookBuilder if present, with standard Bukkit EnchantmentStorageMeta fallback.
     */
    public ItemStack createEnchantedBook(Enchantment ench, int level) {
        if (ench == null) return null;
        int safeLevel = Math.max(1, level);

        // 1. Try Eco EnchantedBookBuilder
        if (enchantedBookBuilderClass != null && enchantedBookBuilderConstructor != null
                && addStoredEnchantMethod != null && buildBookMethod != null) {
            try {
                Object builder = enchantedBookBuilderConstructor.newInstance();
                addStoredEnchantMethod.invoke(builder, ench, safeLevel);
                Object built = buildBookMethod.invoke(builder);
                if (built instanceof ItemStack is) {
                    return is;
                }
            } catch (Throwable ignored) {}
        }

        // 2. Standard Bukkit EnchantmentStorageMeta
        ItemStack book = new ItemStack(Material.ENCHANTED_BOOK);
        ItemMeta meta = book.getItemMeta();
        if (meta instanceof EnchantmentStorageMeta storageMeta) {
            storageMeta.addStoredEnchant(ench, safeLevel, true);
            book.setItemMeta(storageMeta);
        }
        return book;
    }

    /**
     * Builds an EcoEnchantCandidate wrapper from a live EcoEnchant reflection object.
     */
    private EcoEnchantCandidate buildCandidateFromObj(Object ecoObj) {
        if (ecoObj == null) return null;
        try {
            Enchantment ench = null;
            try {
                Method m = ecoObj.getClass().getMethod("getEnchantment");
                Object res = m.invoke(ecoObj);
                if (res instanceof Enchantment e) ench = e;
            } catch (Throwable ignored) {}

            String id = (ench != null) ? ench.getKey().getKey() : "";

            String rawDisplayName = null;
            try {
                Method m = ecoObj.getClass().getMethod("getRawDisplayName");
                Object res = m.invoke(ecoObj);
                if (res instanceof String s) rawDisplayName = s;
            } catch (Throwable ignored) {}

            int maxLevel = 1;
            try {
                Method m = ecoObj.getClass().getMethod("getMaximumLevel");
                Object res = m.invoke(ecoObj);
                if (res instanceof Number n) maxLevel = n.intValue();
            } catch (Throwable ignored) {}

            String rarityId = "";
            String rarityDisplayName = "";
            try {
                Method m = ecoObj.getClass().getMethod("getEnchantmentRarity");
                Object rarityObj = m.invoke(ecoObj);
                if (rarityObj != null) {
                    try {
                        Method idM = rarityObj.getClass().getMethod("getId");
                        Object res = idM.invoke(rarityObj);
                        if (res instanceof String s) rarityId = s;
                    } catch (Throwable ignored) {
                        try {
                            Method idM = rarityObj.getClass().getMethod("getID");
                            Object res = idM.invoke(rarityObj);
                            if (res instanceof String s) rarityId = s;
                        } catch (Throwable ignored2) {}
                    }

                    try {
                        Method dM = rarityObj.getClass().getMethod("getDisplayName");
                        Object res = dM.invoke(rarityObj);
                        if (res instanceof String s) rarityDisplayName = s;
                    } catch (Throwable ignored) {}
                }
            } catch (Throwable ignored) {}

            String typeId = "";
            String typeFormat = "";
            try {
                Method m = ecoObj.getClass().getMethod("getType");
                Object typeObj = m.invoke(ecoObj);
                if (typeObj != null) {
                    try {
                        Method idM = typeObj.getClass().getMethod("getId");
                        Object res = idM.invoke(typeObj);
                        if (res instanceof String s) typeId = s;
                    } catch (Throwable ignored) {
                        try {
                            Method idM = typeObj.getClass().getMethod("getID");
                            Object res = idM.invoke(typeObj);
                            if (res instanceof String s) typeId = s;
                        } catch (Throwable ignored2) {}
                    }

                    try {
                        Method fM = typeObj.getClass().getMethod("getFormat");
                        Object res = fM.invoke(typeObj);
                        if (res instanceof String s) typeFormat = s;
                    } catch (Throwable ignored) {}
                }
            } catch (Throwable ignored) {}

            return new EcoEnchantCandidate(ench, id, rawDisplayName, rarityId, rarityDisplayName,
                    typeId, typeFormat, maxLevel, ecoObj);
        } catch (Throwable ignored) {
            return null;
        }
    }

    /**
     * Converts an integer level to Roman numerals (I, II, III, IV, V, ...).
     */
    public static String toRoman(int level) {
        if (level <= 0) return String.valueOf(level);
        if (level > 3999) return String.valueOf(level);

        StringBuilder sb = new StringBuilder();
        int num = level;
        for (int i = 0; i < ROMAN_VALUES.length; i++) {
            while (num >= ROMAN_VALUES[i]) {
                sb.append(ROMAN_SYMBOLS[i]);
                num -= ROMAN_VALUES[i];
            }
        }
        return sb.toString();
    }

    /**
     * Strips color codes (MiniMessage, legacy, and hex variants) for clean string comparisons.
     */
    public static String stripColor(String input) {
        if (input == null || input.isEmpty()) return "";
        String s = input;
        s = s.replaceAll("<[^>]*>", "");
        s = s.replaceAll("(?i)[&§]#([0-9a-fA-F]{6})", "");
        s = s.replaceAll("(?i)[&§]x([&§][0-9a-fA-F]){6}", "");
        s = s.replaceAll("(?i)\\{#?([0-9a-fA-F]{6})\\}", "");
        s = s.replaceAll("(?i)[&§][0-9a-fk-or]", "");
        return s.trim();
    }
}
