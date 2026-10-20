package com.github.orenaidaucac.royItems.integration;

import com.github.orenaidaucac.royItems.item.ItemDefinition;
import com.github.orenaidaucac.royItems.item.ItemPDCManager;
import com.github.orenaidaucac.royItems.item.ItemRegistry;
import com.github.orenaidaucac.royItems.item.ItemType;
import com.github.orenaidaucac.royItems.stat.PlayerStatCache;
import com.github.orenaidaucac.royItems.stat.StatManager;
import com.github.orenaidaucac.royItems.stat.StatType;
import me.clip.placeholderapi.expansion.PlaceholderExpansion;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.text.DecimalFormat;

/**
 * PlaceholderAPI expansion for RoyItems.
 */
public class RoyPlaceholderExpansion extends PlaceholderExpansion {

    private final StatManager statManager;
    private final ItemPDCManager pdcManager;
    private final ItemRegistry registry;
    private final DecimalFormat df = new DecimalFormat("#,##0.##");

    public RoyPlaceholderExpansion(StatManager statManager, ItemPDCManager pdcManager, ItemRegistry registry) {
        this.statManager = statManager;
        this.pdcManager = pdcManager;
        this.registry = registry;
    }

    @Override
    public @NotNull String getIdentifier() {
        return "royitems";
    }

    @Override
    public @NotNull String getAuthor() {
        return "orenaidaucac";
    }

    @Override
    public @NotNull String getVersion() {
        return "2026.1";
    }

    @Override
    public boolean persist() {
        return true;
    }

    @Override
    public @Nullable String onPlaceholderRequest(Player player, @NotNull String params) {
        if (player == null) return "";

        // %royitems_stat_<stat>%
        if (params.startsWith("stat_")) {
            String statKey = params.substring(5);
            StatType type = StatType.fromKey(statKey);
            if (type != null) {
                PlayerStatCache cache = statManager.getCache(player);
                return df.format(cache.calculateTotal(type, 0));
            }
        }

        // Hand item placeholders
        ItemStack hand = player.getInventory().getItemInMainHand();
        if (pdcManager.isRoyItem(hand)) {
            ItemType type = pdcManager.getType(hand);
            String id = pdcManager.getId(hand);
            ItemDefinition def = registry.get(type, id);

            if (params.equalsIgnoreCase("hand_type")) {
                return type != null ? type.name() : "";
            }
            if (params.equalsIgnoreCase("hand_id")) {
                return id != null ? id : "";
            }
            if (params.equalsIgnoreCase("hand_name")) {
                return def != null && def.getName() != null ? def.getName() : id;
            }
            if (params.equalsIgnoreCase("hand_tier")) {
                return def != null && def.getItemTier() != null ? def.getItemTier() : "";
            }
            if (params.equalsIgnoreCase("hand_version")) {
                return String.valueOf(pdcManager.getVersion(hand));
            }
        }

        return null;
    }
}
