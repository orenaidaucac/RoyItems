package com.github.orenaidaucac.royItems.station;

import com.github.orenaidaucac.royItems.core.text.TextUtility;
import com.github.orenaidaucac.royItems.integration.IntegrationManager;
import com.github.orenaidaucac.royItems.item.ItemBuilder;
import com.github.orenaidaucac.royItems.item.ItemDefinition;
import com.github.orenaidaucac.royItems.item.ItemPDCManager;
import com.github.orenaidaucac.royItems.item.ItemRegistry;
import com.github.orenaidaucac.royItems.item.ItemType;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Represents a polymorphic ingredient in a Station recipe (Inputs and Outputs).
 */
public interface RecipeIngredient {

    enum Type {
        ROYITEMS,
        VANILLA,
        MONEY,
        EXP,
        POINT,
        EXCELLENT_ECONOMY
    }

    Type getType();

    int getAmount();

    String getRawString();

    int getPlayerAmount(Player player, ItemRegistry registry, ItemPDCManager pdc, IntegrationManager integration);

    default int getInventoryAmount(Player player, ItemRegistry registry, ItemPDCManager pdc, IntegrationManager integration) {
        return getPlayerAmount(player, registry, pdc, integration);
    }

    default int getStorageAmount(Player player, IntegrationManager integration) {
        return 0;
    }

    default boolean hasEnough(Player player, ItemRegistry registry, ItemPDCManager pdc, IntegrationManager integration) {
        return hasEnough(player, registry, pdc, integration, 1);
    }

    default boolean hasEnough(Player player, ItemRegistry registry, ItemPDCManager pdc, IntegrationManager integration, int multiplier) {
        if (multiplier <= 0) return true;
        long totalNeed = (long) getAmount() * multiplier;
        return getPlayerAmount(player, registry, pdc, integration) >= totalNeed;
    }

    boolean take(Player player, ItemRegistry registry, ItemPDCManager pdc, IntegrationManager integration);

    default boolean take(Player player, ItemRegistry registry, ItemPDCManager pdc, IntegrationManager integration, int multiplier) {
        if (multiplier <= 0) return true;
        for (int i = 0; i < multiplier; i++) {
            if (!take(player, registry, pdc, integration)) return false;
        }
        return true;
    }

    default boolean give(Player player, ItemRegistry registry, ItemPDCManager pdc, ItemBuilder builder, IntegrationManager integration) {
        return give(player, registry, pdc, builder, integration, 1, false);
    }

    default boolean give(Player player, ItemRegistry registry, ItemPDCManager pdc, ItemBuilder builder, IntegrationManager integration, int multiplier) {
        return give(player, registry, pdc, builder, integration, multiplier, false);
    }

    boolean give(Player player, ItemRegistry registry, ItemPDCManager pdc, ItemBuilder builder, IntegrationManager integration, int multiplier, boolean dropOnGround);

    String getFormattedDisplayName(StationDynamicLore loreConfig, ItemRegistry registry);

    default net.kyori.adventure.text.Component getDisplayNameComponent(StationDynamicLore loreConfig, ItemRegistry registry) {
        return TextUtility.formatGui(getFormattedDisplayName(loreConfig, registry));
    }

    ItemStack toPreviewItemStack(ItemRegistry registry, ItemBuilder builder, StationDynamicLore loreConfig);

    // =========================================================================
    // PARSER FACTORY
    // =========================================================================

    static RecipeIngredient parse(String raw) {
        if (raw == null || raw.isBlank()) return null;
        String s = raw.trim();

        // 1. RoyItems: royitems[type=<TYPE>,id=<ID>,amount=10]
        if (s.toLowerCase().startsWith("royitems[") && s.endsWith("]")) {
            Map<String, String> params = parseBracketParams(s.substring(8));
            ItemType itemType = ItemType.fromString(params.getOrDefault("type", ""));
            String id = params.getOrDefault("id", "unknown");
            int amount = parseInt(params.getOrDefault("amount", "1"), 1);
            return new RoyItemIngredient(s, itemType, id, amount);
        }

        // 2. Vanilla: vanilla[type=<MATERIAL>,amount=10,displayname='...',lore='...']
        if (s.toLowerCase().startsWith("vanilla[") && s.endsWith("]")) {
            Map<String, String> params = parseBracketParams(s.substring(7));
            String matName = params.getOrDefault("type", "STONE");
            Material mat = Material.matchMaterial(matName.toUpperCase());
            if (mat == null) mat = Material.STONE;
            int amount = parseInt(params.getOrDefault("amount", "1"), 1);
            String displayName = params.get("displayname");
            String lore = params.get("lore");
            return new VanillaIngredient(s, mat, amount, displayName, lore);
        }

        // 3. Vault Money: money=10000
        if (s.toLowerCase().startsWith("money=")) {
            int amount = parseInt(s.substring(6).trim(), 0);
            return new MoneyIngredient(s, amount);
        }

        // 4. Experience: exp=10000
        if (s.toLowerCase().startsWith("exp=")) {
            int amount = parseInt(s.substring(4).trim(), 0);
            return new ExpIngredient(s, amount);
        }

        // 5. PlayerPoints: point=1000 or points=1000
        if (s.toLowerCase().startsWith("point=") || s.toLowerCase().startsWith("points=")) {
            int idx = s.indexOf('=');
            int amount = parseInt(s.substring(idx + 1).trim(), 0);
            return new PointIngredient(s, amount);
        }

        // 6. ExcellentEconomy: excellenteconnomy:<ECONOMY_ID>=10000 or excellenteconomy:<ECONOMY_ID>=10000
        if (s.toLowerCase().startsWith("excellenteconnomy:") || s.toLowerCase().startsWith("excellenteconomy:")) {
            int colonIdx = s.indexOf(':');
            int eqIdx = s.indexOf('=');
            if (eqIdx > colonIdx) {
                String currencyId = s.substring(colonIdx + 1, eqIdx).trim();
                int amount = parseInt(s.substring(eqIdx + 1).trim(), 0);
                return new ExcellentEconomyIngredient(s, currencyId, amount);
            }
        }

        return null;
    }

    private static int parseInt(String val, int def) {
        try {
            return Math.max(1, Integer.parseInt(val.trim()));
        } catch (NumberFormatException e) {
            return def;
        }
    }

    private static Map<String, String> parseBracketParams(String bracketed) {
        Map<String, String> map = new LinkedHashMap<>();
        if (bracketed.startsWith("[") && bracketed.endsWith("]")) {
            bracketed = bracketed.substring(1, bracketed.length() - 1);
        }

        // Regex parsing supporting quoted strings (e.g. displayname='Hello World', lore='Line1\nLine2')
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

    // =========================================================================
    // INGREDIENT IMPLEMENTATIONS
    // =========================================================================

    class RoyItemIngredient implements RecipeIngredient {
        private final String raw;
        private final ItemType itemType;
        private final String id;
        private final int amount;

        public RoyItemIngredient(String raw, ItemType itemType, String id, int amount) {
            this.raw = raw;
            this.itemType = itemType;
            this.id = id;
            this.amount = amount;
        }

        @Override public Type getType() { return Type.ROYITEMS; }
        @Override public int getAmount() { return amount; }
        @Override public String getRawString() { return raw; }
        public ItemType getItemType() { return itemType; }
        public String getId() { return id; }

        @Override
        public int getInventoryAmount(Player player, ItemRegistry registry, ItemPDCManager pdc, IntegrationManager integration) {
            if (player == null || pdc == null) return 0;
            int total = 0;
            for (ItemStack item : player.getInventory().getContents()) {
                if (item != null && item.getType() != Material.AIR) {
                    if (pdc.isRoyItem(item)) {
                        String royId = pdc.getId(item);
                        ItemType rType = pdc.getType(item);
                        if (id.equalsIgnoreCase(royId) && (itemType == null || itemType == rType)) {
                            total += item.getAmount();
                        }
                    }
                }
            }
            return total;
        }

        @Override
        public int getStorageAmount(Player player, IntegrationManager integration) {
            if (player == null || integration == null) return 0;
            // VirtualStorage & ExtraStorage strictly support storing MATERIAL type
            if (itemType != null && itemType != ItemType.MATERIAL) {
                return 0;
            }
            String storageKey = "ROYITEMS:MATERIAL:" + id.toLowerCase(Locale.ROOT);
            int total = 0;
            if (integration.hasVirtualStorage()) {
                total += (int) Math.min(Integer.MAX_VALUE, integration.getVirtualStorageHook().getAmount(player, storageKey));
            }
            if (integration.hasExtraStorage()) {
                total += integration.getExtraStorageHook().getAmount(player, storageKey);
            }
            return total;
        }

        @Override
        public int getPlayerAmount(Player player, ItemRegistry registry, ItemPDCManager pdc, IntegrationManager integration) {
            return getInventoryAmount(player, registry, pdc, integration) + getStorageAmount(player, integration);
        }

        @Override
        public boolean take(Player player, ItemRegistry registry, ItemPDCManager pdc, IntegrationManager integration) {
            return take(player, registry, pdc, integration, 1);
        }

        @Override
        public boolean take(Player player, ItemRegistry registry, ItemPDCManager pdc, IntegrationManager integration, int multiplier) {
            if (multiplier <= 0) return true;
            if (!hasEnough(player, registry, pdc, integration, multiplier)) return false;

            long totalNeedLong = (long) amount * multiplier;
            if (totalNeedLong > Integer.MAX_VALUE) return false;
            int need = (int) totalNeedLong;

            int invAmount = getInventoryAmount(player, registry, pdc, integration);
            int takeFromInv = Math.min(invAmount, need);
            int takeFromStorage = need - takeFromInv;

            // 1. Prioritize taking from Inventory first
            if (takeFromInv > 0) {
                int remainingInvToTake = takeFromInv;
                ItemStack[] contents = player.getInventory().getContents();
                for (int i = 0; i < contents.length; i++) {
                    ItemStack item = contents[i];
                    if (item != null && item.getType() != Material.AIR && pdc != null && pdc.isRoyItem(item)) {
                        String royId = pdc.getId(item);
                        ItemType rType = pdc.getType(item);
                        if (id.equalsIgnoreCase(royId) && (itemType == null || itemType == rType)) {
                            int count = item.getAmount();
                            if (count <= remainingInvToTake) {
                                remainingInvToTake -= count;
                                player.getInventory().setItem(i, null);
                            } else {
                                item.setAmount(count - remainingInvToTake);
                                remainingInvToTake = 0;
                                break;
                            }
                            if (remainingInvToTake <= 0) break;
                        }
                    }
                }
                player.updateInventory();
            }

            // 2. Take remaining shortage from VirtualStorage and/or ExtraStorage
            if (takeFromStorage > 0) {
                int shortage = takeFromStorage;
                String storageKey = "ROYITEMS:MATERIAL:" + id.toLowerCase(Locale.ROOT);

                // Priority 1: Take from RoyMC-VirtualStorage
                if (integration != null && integration.hasVirtualStorage()) {
                    long inVs = integration.getVirtualStorageHook().getAmount(player, storageKey);
                    if (inVs > 0) {
                        int takeVs = (int) Math.min(shortage, inVs);
                        if (integration.getVirtualStorageHook().take(player, storageKey, takeVs)) {
                            shortage -= takeVs;
                        }
                    }
                }

                // Priority 2: Take remaining from ExtraStorage if needed
                if (shortage > 0 && integration != null && integration.hasExtraStorage()) {
                    long inEs = integration.getExtraStorageHook().getAmount(player, storageKey);
                    if (inEs > 0) {
                        int takeEs = (int) Math.min(shortage, inEs);
                        if (integration.getExtraStorageHook().take(player, storageKey, takeEs)) {
                            shortage -= takeEs;
                        }
                    }
                }

                if (shortage > 0) {
                    // Safety rollback: if storage subtraction failed unexpectedly, restore inventory items
                    if (takeFromInv > 0 && registry != null) {
                        ItemDefinition def = registry.get(itemType, id);
                        if (def != null && com.github.orenaidaucac.royItems.RoyItems.getInstance() != null) {
                            ItemStack restored = com.github.orenaidaucac.royItems.RoyItems.getInstance().getItemBuilder().build(def, takeFromInv);
                            player.getInventory().addItem(restored);
                            player.updateInventory();
                        }
                    }
                    return false;
                }
            }

            return true;
        }

        @Override
        public boolean give(Player player, ItemRegistry registry, ItemPDCManager pdc, ItemBuilder builder, IntegrationManager integration, int multiplier) {
            return give(player, registry, pdc, builder, integration, multiplier, false);
        }

        @Override
        public boolean give(Player player, ItemRegistry registry, ItemPDCManager pdc, ItemBuilder builder, IntegrationManager integration, int multiplier, boolean dropOnGround) {
            if (player == null || registry == null || builder == null) return false;
            ItemDefinition def = registry.get(itemType, id);
            if (def == null) return false;

            int total = amount * Math.max(1, multiplier);
            ItemStack sample = builder.build(def, 1);
            int maxStack = sample.getMaxStackSize() > 0 ? sample.getMaxStackSize() : 64;

            int left = total;
            boolean allFit = true;
            while (left > 0) {
                int stackSize = Math.min(left, maxStack);
                ItemStack stack = builder.build(def, stackSize);
                HashMap<Integer, ItemStack> leftover = player.getInventory().addItem(stack);
                if (!leftover.isEmpty()) {
                    allFit = false;
                    if (dropOnGround) {
                        for (ItemStack drop : leftover.values()) {
                            player.getWorld().dropItemNaturally(player.getLocation(), drop);
                        }
                    }
                }
                left -= stackSize;
            }
            player.updateInventory();
            return allFit;
        }

        @Override
        public String getFormattedDisplayName(StationDynamicLore loreConfig, ItemRegistry registry) {
            if (registry != null) {
                ItemDefinition def = registry.get(itemType, id);
                if (def != null && def.getName() != null && !def.getName().isBlank()) {
                    return def.getName();
                }
            }
            return id;
        }

        @Override
        public net.kyori.adventure.text.Component getDisplayNameComponent(StationDynamicLore loreConfig, ItemRegistry registry) {
            if (registry != null) {
                ItemDefinition def = registry.get(itemType, id);
                if (def != null && def.getName() != null && !def.getName().isBlank()) {
                    return TextUtility.formatGui(def.getName());
                }
            }
            return net.kyori.adventure.text.Component.text(id);
        }

        @Override
        public ItemStack toPreviewItemStack(ItemRegistry registry, ItemBuilder builder, StationDynamicLore loreConfig) {
            if (registry != null && builder != null) {
                ItemDefinition def = registry.get(itemType, id);
                if (def != null) {
                    return builder.build(def, amount);
                }
            }
            return new ItemStack(Material.PAPER, amount);
        }
    }

    class VanillaIngredient implements RecipeIngredient {
        private final String raw;
        private final Material material;
        private final int amount;
        private final String displayName;
        private final String lore;

        public VanillaIngredient(String raw, Material material, int amount, String displayName, String lore) {
            this.raw = raw;
            this.material = material != null ? material : Material.STONE;
            this.amount = amount;
            this.displayName = displayName;
            this.lore = lore;
        }

        @Override public Type getType() { return Type.VANILLA; }
        @Override public int getAmount() { return amount; }
        @Override public String getRawString() { return raw; }
        public Material getMaterial() { return material; }
        public String getDisplayName() { return displayName; }
        public String getLore() { return lore; }

        @Override
        public int getInventoryAmount(Player player, ItemRegistry registry, ItemPDCManager pdc, IntegrationManager integration) {
            if (player == null) return 0;
            int total = 0;
            for (ItemStack item : player.getInventory().getContents()) {
                if (item != null && item.getType() == material) {
                    // Do not count custom RoyItems as vanilla items
                    if (pdc != null && pdc.isRoyItem(item)) continue;
                    total += item.getAmount();
                }
            }
            return total;
        }

        @Override
        public int getStorageAmount(Player player, IntegrationManager integration) {
            if (player == null || integration == null) return 0;
            String storageKey = material.name();
            int total = 0;
            if (integration.hasVirtualStorage()) {
                total += (int) Math.min(Integer.MAX_VALUE, integration.getVirtualStorageHook().getAmount(player, storageKey));
            }
            if (integration.hasExtraStorage()) {
                total += integration.getExtraStorageHook().getAmount(player, storageKey);
            }
            return total;
        }

        @Override
        public int getPlayerAmount(Player player, ItemRegistry registry, ItemPDCManager pdc, IntegrationManager integration) {
            return getInventoryAmount(player, registry, pdc, integration) + getStorageAmount(player, integration);
        }

        @Override
        public boolean take(Player player, ItemRegistry registry, ItemPDCManager pdc, IntegrationManager integration) {
            return take(player, registry, pdc, integration, 1);
        }

        @Override
        public boolean take(Player player, ItemRegistry registry, ItemPDCManager pdc, IntegrationManager integration, int multiplier) {
            if (multiplier <= 0) return true;
            if (!hasEnough(player, registry, pdc, integration, multiplier)) return false;

            long totalNeedLong = (long) amount * multiplier;
            if (totalNeedLong > Integer.MAX_VALUE) return false;
            int need = (int) totalNeedLong;

            int invAmount = getInventoryAmount(player, registry, pdc, integration);
            int takeFromInv = Math.min(invAmount, need);
            int takeFromStorage = need - takeFromInv;

            // 1. Prioritize taking from Inventory first
            if (takeFromInv > 0) {
                int remainingInvToTake = takeFromInv;
                ItemStack[] contents = player.getInventory().getContents();
                for (int i = 0; i < contents.length; i++) {
                    ItemStack item = contents[i];
                    if (item != null && item.getType() == material) {
                        if (pdc != null && pdc.isRoyItem(item)) continue;
                        int count = item.getAmount();
                        if (count <= remainingInvToTake) {
                            remainingInvToTake -= count;
                            player.getInventory().setItem(i, null);
                        } else {
                            item.setAmount(count - remainingInvToTake);
                            remainingInvToTake = 0;
                            break;
                        }
                        if (remainingInvToTake <= 0) break;
                    }
                }
                player.updateInventory();
            }

            // 2. Take remaining shortage from VirtualStorage and/or ExtraStorage
            if (takeFromStorage > 0) {
                int shortage = takeFromStorage;
                String storageKey = material.name();

                // Priority 1: Take from RoyMC-VirtualStorage
                if (integration != null && integration.hasVirtualStorage()) {
                    long inVs = integration.getVirtualStorageHook().getAmount(player, storageKey);
                    if (inVs > 0) {
                        int takeVs = (int) Math.min(shortage, inVs);
                        if (integration.getVirtualStorageHook().take(player, storageKey, takeVs)) {
                            shortage -= takeVs;
                        }
                    }
                }

                // Priority 2: Take remaining from ExtraStorage if needed
                if (shortage > 0 && integration != null && integration.hasExtraStorage()) {
                    long inEs = integration.getExtraStorageHook().getAmount(player, storageKey);
                    if (inEs > 0) {
                        int takeEs = (int) Math.min(shortage, inEs);
                        if (integration.getExtraStorageHook().take(player, storageKey, takeEs)) {
                            shortage -= takeEs;
                        }
                    }
                }

                if (shortage > 0) {
                    // Rollback inventory deduction if storage failed
                    if (takeFromInv > 0) {
                        player.getInventory().addItem(new ItemStack(material, takeFromInv));
                        player.updateInventory();
                    }
                    return false;
                }
            }

            return true;
        }

        @Override
        public boolean give(Player player, ItemRegistry registry, ItemPDCManager pdc, ItemBuilder builder, IntegrationManager integration, int multiplier) {
            return give(player, registry, pdc, builder, integration, multiplier, false);
        }

        @Override
        public boolean give(Player player, ItemRegistry registry, ItemPDCManager pdc, ItemBuilder builder, IntegrationManager integration, int multiplier, boolean dropOnGround) {
            if (player == null) return false;
            int total = amount * Math.max(1, multiplier);
            int left = total;
            int maxStack = material.getMaxStackSize() > 0 ? material.getMaxStackSize() : 64;
            boolean allFit = true;
            while (left > 0) {
                int stackSize = Math.min(left, maxStack);
                ItemStack stack = new ItemStack(material, stackSize);
                if (displayName != null || lore != null) {
                    ItemMeta meta = stack.getItemMeta();
                    if (meta != null) {
                        if (displayName != null && !displayName.isBlank()) {
                            meta.displayName(TextUtility.formatGui(displayName));
                        }
                        if (lore != null && !lore.isBlank()) {
                            List<String> loreLines = Arrays.asList(lore.split("\\\\n|\\n"));
                            meta.lore(TextUtility.formatGuiLore(loreLines));
                        }
                        stack.setItemMeta(meta);
                    }
                }
                HashMap<Integer, ItemStack> leftover = player.getInventory().addItem(stack);
                if (!leftover.isEmpty()) {
                    allFit = false;
                    if (dropOnGround) {
                        for (ItemStack drop : leftover.values()) {
                            player.getWorld().dropItemNaturally(player.getLocation(), drop);
                        }
                    }
                }
                left -= stackSize;
            }
            player.updateInventory();
            return allFit;
        }

        @Override
        public String getFormattedDisplayName(StationDynamicLore loreConfig, ItemRegistry registry) {
            if (displayName != null && !displayName.isBlank()) {
                return displayName;
            }
            return formatVanillaName(material);
        }

        @Override
        public net.kyori.adventure.text.Component getDisplayNameComponent(StationDynamicLore loreConfig, ItemRegistry registry) {
            if (displayName != null && !displayName.isBlank()) {
                return TextUtility.formatGui(displayName);
            }
            return createVanillaTranslatable(material);
        }

        public static net.kyori.adventure.text.Component createVanillaTranslatable(Material material) {
            if (material == null) return net.kyori.adventure.text.Component.empty();
            if (org.bukkit.Bukkit.getServer() != null) {
                try {
                    return net.kyori.adventure.text.Component.translatable(material)
                            .decoration(net.kyori.adventure.text.format.TextDecoration.ITALIC, false);
                } catch (Throwable ignored) {}
            }
            String name = material.name().toLowerCase();
            String prefix = (name.endsWith("_block") || name.endsWith("_ore") || name.equals("stone") || name.contains("air")) ? "block" : "item";
            return net.kyori.adventure.text.Component.translatable(prefix + ".minecraft." + name)
                    .decoration(net.kyori.adventure.text.format.TextDecoration.ITALIC, false);
        }

        public static String formatVanillaName(Material mat) {
            if (mat == null) return "Stone";
            String[] words = mat.name().split("_");
            StringBuilder sb = new StringBuilder();
            for (String w : words) {
                if (w.isEmpty()) continue;
                if (!sb.isEmpty()) sb.append(" ");
                sb.append(Character.toUpperCase(w.charAt(0))).append(w.substring(1).toLowerCase());
            }
            return sb.toString();
        }

        @Override
        public ItemStack toPreviewItemStack(ItemRegistry registry, ItemBuilder builder, StationDynamicLore loreConfig) {
            ItemStack stack = new ItemStack(material, Math.min(amount, 64));
            ItemMeta meta = stack.getItemMeta();
            if (meta != null) {
                if (displayName != null && !displayName.isBlank()) {
                    meta.displayName(TextUtility.formatGui(displayName));
                } else {
                    meta.displayName(createVanillaTranslatable(material));
                }
                if (lore != null && !lore.isBlank()) {
                    List<String> loreLines = Arrays.asList(lore.split("\\\\n|\\n"));
                    meta.lore(TextUtility.formatGuiLore(loreLines));
                }
                stack.setItemMeta(meta);
            }
            return stack;
        }
    }

    class MoneyIngredient implements RecipeIngredient {
        private final String raw;
        private final int amount;

        public MoneyIngredient(String raw, int amount) {
            this.raw = raw;
            this.amount = amount;
        }

        @Override public Type getType() { return Type.MONEY; }
        @Override public int getAmount() { return amount; }
        @Override public String getRawString() { return raw; }

        @Override
        public int getPlayerAmount(Player player, ItemRegistry registry, ItemPDCManager pdc, IntegrationManager integration) {
            if (integration == null || integration.getVaultHook() == null) return 0;
            return (int) integration.getVaultHook().getBalance(player);
        }

        @Override
        public boolean hasEnough(Player player, ItemRegistry registry, ItemPDCManager pdc, IntegrationManager integration) {
            if (integration != null && integration.getVaultHook() != null) {
                return integration.getVaultHook().hasMoney(player, amount);
            }
            return false;
        }

        @Override
        public boolean take(Player player, ItemRegistry registry, ItemPDCManager pdc, IntegrationManager integration) {
            return take(player, registry, pdc, integration, 1);
        }

        @Override
        public boolean take(Player player, ItemRegistry registry, ItemPDCManager pdc, IntegrationManager integration, int multiplier) {
            if (multiplier <= 0) return true;
            if (integration != null && integration.getVaultHook() != null) {
                return integration.getVaultHook().withdraw(player, (double) amount * multiplier);
            }
            return false;
        }

        @Override
        public boolean give(Player player, ItemRegistry registry, ItemPDCManager pdc, ItemBuilder builder, IntegrationManager integration, int multiplier) {
            return give(player, registry, pdc, builder, integration, multiplier, false);
        }

        @Override
        public boolean give(Player player, ItemRegistry registry, ItemPDCManager pdc, ItemBuilder builder, IntegrationManager integration, int multiplier, boolean dropOnGround) {
            if (integration != null && integration.getVaultHook() != null) {
                return integration.getVaultHook().deposit(player, (double) amount * Math.max(1, multiplier));
            }
            return false;
        }

        @Override
        public String getFormattedDisplayName(StationDynamicLore loreConfig, ItemRegistry registry) {
            return loreConfig != null ? loreConfig.getNameVault() : "<gold>Vault Money</gold>";
        }

        @Override
        public ItemStack toPreviewItemStack(ItemRegistry registry, ItemBuilder builder, StationDynamicLore loreConfig) {
            ItemStack is = new ItemStack(Material.SUNFLOWER, 1);
            ItemMeta meta = is.getItemMeta();
            if (meta != null) {
                meta.displayName(TextUtility.formatGui(getFormattedDisplayName(loreConfig, registry) + ": <white>" + amount + "$</white>"));
                is.setItemMeta(meta);
            }
            return is;
        }
    }

    class ExpIngredient implements RecipeIngredient {
        private final String raw;
        private final int amount;

        public ExpIngredient(String raw, int amount) {
            this.raw = raw;
            this.amount = amount;
        }

        @Override public Type getType() { return Type.EXP; }
        @Override public int getAmount() { return amount; }
        @Override public String getRawString() { return raw; }

        @Override
        public int getPlayerAmount(Player player, ItemRegistry registry, ItemPDCManager pdc, IntegrationManager integration) {
            return ExperienceUtils.getPlayerExp(player);
        }

        @Override
        public boolean take(Player player, ItemRegistry registry, ItemPDCManager pdc, IntegrationManager integration) {
            return take(player, registry, pdc, integration, 1);
        }

        @Override
        public boolean take(Player player, ItemRegistry registry, ItemPDCManager pdc, IntegrationManager integration, int multiplier) {
            if (multiplier <= 0) return true;
            int total = amount * multiplier;
            if (ExperienceUtils.getPlayerExp(player) < total) return false;
            ExperienceUtils.takeExp(player, total);
            return true;
        }

        @Override
        public boolean give(Player player, ItemRegistry registry, ItemPDCManager pdc, ItemBuilder builder, IntegrationManager integration, int multiplier) {
            return give(player, registry, pdc, builder, integration, multiplier, false);
        }

        @Override
        public boolean give(Player player, ItemRegistry registry, ItemPDCManager pdc, ItemBuilder builder, IntegrationManager integration, int multiplier, boolean dropOnGround) {
            ExperienceUtils.giveExp(player, amount * Math.max(1, multiplier));
            return true;
        }

        @Override
        public String getFormattedDisplayName(StationDynamicLore loreConfig, ItemRegistry registry) {
            return loreConfig != null ? loreConfig.getNameExp() : "<green>Experience</green>";
        }

        @Override
        public ItemStack toPreviewItemStack(ItemRegistry registry, ItemBuilder builder, StationDynamicLore loreConfig) {
            ItemStack is = new ItemStack(Material.EXPERIENCE_BOTTLE, 1);
            ItemMeta meta = is.getItemMeta();
            if (meta != null) {
                meta.displayName(TextUtility.formatGui(getFormattedDisplayName(loreConfig, registry) + ": <white>" + amount + " EXP</white>"));
                is.setItemMeta(meta);
            }
            return is;
        }
    }

    class PointIngredient implements RecipeIngredient {
        private final String raw;
        private final int amount;

        public PointIngredient(String raw, int amount) {
            this.raw = raw;
            this.amount = amount;
        }

        @Override public Type getType() { return Type.POINT; }
        @Override public int getAmount() { return amount; }
        @Override public String getRawString() { return raw; }

        @Override
        public int getPlayerAmount(Player player, ItemRegistry registry, ItemPDCManager pdc, IntegrationManager integration) {
            if (integration != null && integration.getPlayerPointsHook() != null) {
                return integration.getPlayerPointsHook().getPoints(player);
            }
            return 0;
        }

        @Override
        public boolean take(Player player, ItemRegistry registry, ItemPDCManager pdc, IntegrationManager integration) {
            return take(player, registry, pdc, integration, 1);
        }

        @Override
        public boolean take(Player player, ItemRegistry registry, ItemPDCManager pdc, IntegrationManager integration, int multiplier) {
            if (multiplier <= 0) return true;
            if (integration != null && integration.getPlayerPointsHook() != null) {
                return integration.getPlayerPointsHook().takePoints(player, amount * multiplier);
            }
            return false;
        }

        @Override
        public boolean give(Player player, ItemRegistry registry, ItemPDCManager pdc, ItemBuilder builder, IntegrationManager integration, int multiplier) {
            return give(player, registry, pdc, builder, integration, multiplier, false);
        }

        @Override
        public boolean give(Player player, ItemRegistry registry, ItemPDCManager pdc, ItemBuilder builder, IntegrationManager integration, int multiplier, boolean dropOnGround) {
            if (integration != null && integration.getPlayerPointsHook() != null) {
                return integration.getPlayerPointsHook().givePoints(player, amount * Math.max(1, multiplier));
            }
            return false;
        }

        @Override
        public String getFormattedDisplayName(StationDynamicLore loreConfig, ItemRegistry registry) {
            return loreConfig != null ? loreConfig.getNamePlayerPoints() : "<light_purple>PlayerPoints</light_purple>";
        }

        @Override
        public ItemStack toPreviewItemStack(ItemRegistry registry, ItemBuilder builder, StationDynamicLore loreConfig) {
            ItemStack is = new ItemStack(Material.AMETHYST_SHARD, 1);
            ItemMeta meta = is.getItemMeta();
            if (meta != null) {
                meta.displayName(TextUtility.formatGui(getFormattedDisplayName(loreConfig, registry) + ": <white>" + amount + "</white>"));
                is.setItemMeta(meta);
            }
            return is;
        }
    }

    class ExcellentEconomyIngredient implements RecipeIngredient {
        private final String raw;
        private final String currencyId;
        private final int amount;

        public ExcellentEconomyIngredient(String raw, String currencyId, int amount) {
            this.raw = raw;
            this.currencyId = currencyId != null ? currencyId : "default";
            this.amount = amount;
        }

        @Override public Type getType() { return Type.EXCELLENT_ECONOMY; }
        @Override public int getAmount() { return amount; }
        @Override public String getRawString() { return raw; }
        public String getCurrencyId() { return currencyId; }

        @Override
        public int getPlayerAmount(Player player, ItemRegistry registry, ItemPDCManager pdc, IntegrationManager integration) {
            if (integration != null && integration.getExcellentEconomyHook() != null) {
                return (int) integration.getExcellentEconomyHook().getBalance(player, currencyId);
            }
            return 0;
        }

        @Override
        public boolean take(Player player, ItemRegistry registry, ItemPDCManager pdc, IntegrationManager integration) {
            return take(player, registry, pdc, integration, 1);
        }

        @Override
        public boolean take(Player player, ItemRegistry registry, ItemPDCManager pdc, IntegrationManager integration, int multiplier) {
            if (multiplier <= 0) return true;
            if (integration != null && integration.getExcellentEconomyHook() != null) {
                return integration.getExcellentEconomyHook().withdraw(player, currencyId, (double) amount * multiplier);
            }
            return false;
        }

        @Override
        public boolean give(Player player, ItemRegistry registry, ItemPDCManager pdc, ItemBuilder builder, IntegrationManager integration, int multiplier) {
            return give(player, registry, pdc, builder, integration, multiplier, false);
        }

        @Override
        public boolean give(Player player, ItemRegistry registry, ItemPDCManager pdc, ItemBuilder builder, IntegrationManager integration, int multiplier, boolean dropOnGround) {
            if (integration != null && integration.getExcellentEconomyHook() != null) {
                return integration.getExcellentEconomyHook().deposit(player, currencyId, (double) amount * Math.max(1, multiplier));
            }
            return false;
        }

        @Override
        public String getFormattedDisplayName(StationDynamicLore loreConfig, ItemRegistry registry) {
            if (loreConfig != null && loreConfig.getNameExcellentEconomy() != null) {
                return loreConfig.getNameExcellentEconomy().replace("%currency%", currencyId);
            }
            return "<aqua>Economy (" + currencyId + ")</aqua>";
        }

        @Override
        public ItemStack toPreviewItemStack(ItemRegistry registry, ItemBuilder builder, StationDynamicLore loreConfig) {
            ItemStack is = new ItemStack(Material.EMERALD, 1);
            ItemMeta meta = is.getItemMeta();
            if (meta != null) {
                meta.displayName(TextUtility.formatGui(getFormattedDisplayName(loreConfig, registry) + ": <white>" + amount + "</white>"));
                is.setItemMeta(meta);
            }
            return is;
        }
    }
}
