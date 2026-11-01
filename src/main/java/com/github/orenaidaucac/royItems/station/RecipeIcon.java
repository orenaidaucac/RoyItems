package com.github.orenaidaucac.royItems.station;

import com.github.orenaidaucac.royItems.core.text.TextUtility;
import com.github.orenaidaucac.royItems.item.ItemBuilder;
import com.github.orenaidaucac.royItems.item.ItemDefinition;
import com.github.orenaidaucac.royItems.item.ItemRegistry;
import com.github.orenaidaucac.royItems.item.ItemType;
import org.bukkit.Material;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Represents the display icon for a recipe inside the Station GUI.
 */
public class RecipeIcon {

    public enum IconType {
        ROYITEMS,
        VANILLA,
        HDB,
        BASEHEAD
    }

    private final String rawString;
    private final IconType iconType;
    private final ItemType royType;
    private final String royId;
    private final Material vanillaMat;
    private final String hdbId;
    private final String baseheadValue;
    private final int amount;
    private final String displayName;
    private final String lore;

    public RecipeIcon(String rawString, IconType iconType, ItemType royType, String royId,
                      Material vanillaMat, String hdbId, String baseheadValue, int amount,
                      String displayName, String lore) {
        this.rawString = rawString;
        this.iconType = iconType;
        this.royType = royType;
        this.royId = royId;
        this.vanillaMat = vanillaMat;
        this.hdbId = hdbId;
        this.baseheadValue = baseheadValue;
        this.amount = Math.max(1, Math.min(64, amount));
        this.displayName = displayName;
        this.lore = lore;
    }

    public static RecipeIcon parse(String raw) {
        if (raw == null || raw.isBlank()) {
            return new RecipeIcon("", IconType.VANILLA, null, null, Material.BARRIER, null, null, 1, null, null);
        }
        String s = raw.trim();

        // 1. royitems[type=<TYPE>,id=<ID>,amount=1-64]
        if (s.toLowerCase().startsWith("royitems[") && s.endsWith("]")) {
            Map<String, String> p = parseBracketParams(s.substring(8));
            ItemType iType = ItemType.fromString(p.getOrDefault("type", ""));
            String id = p.getOrDefault("id", "unknown");
            int amt = parseInt(p.getOrDefault("amount", "1"), 1);
            return new RecipeIcon(s, IconType.ROYITEMS, iType, id, null, null, null, amt, null, null);
        }

        // 2. vanilla[type=<MATERIAL>,amount=1-64,displayname='...',lore='...']
        if (s.toLowerCase().startsWith("vanilla[") && s.endsWith("]")) {
            Map<String, String> p = parseBracketParams(s.substring(7));
            String matName = p.getOrDefault("type", "STONE");
            Material mat = Material.matchMaterial(matName.toUpperCase());
            if (mat == null) mat = Material.STONE;
            int amt = parseInt(p.getOrDefault("amount", "1"), 1);
            String dn = p.get("displayname");
            String l = p.get("lore");
            return new RecipeIcon(s, IconType.VANILLA, null, null, mat, null, null, amt, dn, l);
        }

        // 3. hdb[id=12345,amount=1-64,displayname='...',lore='...']
        if (s.toLowerCase().startsWith("hdb[") && s.endsWith("]")) {
            Map<String, String> p = parseBracketParams(s.substring(3));
            String hdbId = p.getOrDefault("id", "1");
            int amt = parseInt(p.getOrDefault("amount", "1"), 1);
            String dn = p.get("displayname");
            String l = p.get("lore");
            return new RecipeIcon(s, IconType.HDB, null, null, null, hdbId, null, amt, dn, l);
        }

        // 4. basehead[value=<value>,amount=1-64,displayname='...',lore='...']
        if (s.toLowerCase().startsWith("basehead[") && s.endsWith("]")) {
            Map<String, String> p = parseBracketParams(s.substring(8));
            String val = p.getOrDefault("value", "");
            int amt = parseInt(p.getOrDefault("amount", "1"), 1);
            String dn = p.get("displayname");
            String l = p.get("lore");
            return new RecipeIcon(s, IconType.BASEHEAD, null, null, null, null, val, amt, dn, l);
        }

        // Fallback to material resolver
        ItemStack fallback = CustomItemProvider.resolve(s);
        return new RecipeIcon(s, IconType.VANILLA, null, null, fallback.getType(), null, null, 1, null, null);
    }

    private static int parseInt(String val, int def) {
        try {
            return Integer.parseInt(val.trim());
        } catch (NumberFormatException e) {
            return def;
        }
    }

    private static Map<String, String> parseBracketParams(String bracketed) {
        Map<String, String> map = new LinkedHashMap<>();
        if (bracketed.startsWith("[") && bracketed.endsWith("]")) {
            bracketed = bracketed.substring(1, bracketed.length() - 1);
        }
        Pattern pattern = Pattern.compile("([a-zA-Z0-9_-]+)\\s*=\\s*(?:'([^']*)'|\"([^\"]*)\"|([^,]+))");
        Matcher matcher = pattern.matcher(bracketed);
        while (matcher.find()) {
            String key = matcher.group(1).toLowerCase();
            String val = matcher.group(2) != null ? matcher.group(2)
                    : (matcher.group(3) != null ? matcher.group(3) : matcher.group(4));
            if (val != null) {
                map.put(key, val.trim());
            }
        }
        return map;
    }

    public ItemStack createItemStack(ItemRegistry registry, ItemBuilder builder,
                                     boolean hideDisplayName, boolean hideLore, boolean hideAttributes) {
        ItemStack item;

        switch (iconType) {
            case ROYITEMS -> {
                if (registry != null && builder != null) {
                    ItemDefinition def = registry.get(royType, royId);
                    if (def != null) {
                        item = builder.build(def, amount);
                    } else {
                        item = new ItemStack(Material.BARRIER, amount);
                    }
                } else {
                    item = new ItemStack(Material.PAPER, amount);
                }
            }
            case HDB -> {
                item = CustomItemProvider.resolve("hdb-" + hdbId);
                item.setAmount(amount);
            }
            case BASEHEAD -> {
                item = CustomItemProvider.createBase64Head(baseheadValue);
                item.setAmount(amount);
            }
            default -> {
                item = new ItemStack(vanillaMat != null ? vanillaMat : Material.STONE, amount);
            }
        }

        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            // Apply custom display name if defined
            if (displayName != null && !displayName.isBlank()) {
                meta.displayName(TextUtility.formatGui(displayName));
            } else if (hideDisplayName) {
                meta.displayName(TextUtility.formatGui("&r"));
            } else if (iconType == IconType.VANILLA && vanillaMat != null) {
                meta.displayName(RecipeIngredient.VanillaIngredient.createVanillaTranslatable(vanillaMat));
            }

            // Apply custom base lore if defined
            if (lore != null && !lore.isBlank()) {
                List<String> rawLore = Arrays.asList(lore.split("\\\\n|\\n"));
                meta.lore(TextUtility.formatGuiLore(rawLore));
            }

            if (hideLore) {
                meta.lore(new ArrayList<>());
            }

            if (hideAttributes) {
                meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES, ItemFlag.HIDE_ENCHANTS, ItemFlag.HIDE_UNBREAKABLE);
            }

            item.setItemMeta(meta);
        }

        return item;
    }

    public String getRawString() { return rawString; }
    public IconType getIconType() { return iconType; }
    public ItemType getRoyType() { return royType; }
    public String getRoyId() { return royId; }
    public Material getVanillaMat() { return vanillaMat; }
    public String getHdbId() { return hdbId; }
    public String getBaseheadValue() { return baseheadValue; }
    public int getAmount() { return amount; }
    public String getDisplayName() { return displayName; }
    public String getLore() { return lore; }
}
