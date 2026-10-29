package com.github.orenaidaucac.royItems.tier_set;

import net.kyori.adventure.text.format.NamedTextColor;

/**
 * Represents an item quality tier loaded from item-tier.yml.
 */
public class ItemTier {
    private String id;
    private String displayName = "Common";
    private String color = "&f";
    private int weight = 0;
    private String dropColor = null;

    public ItemTier() {}

    public ItemTier(String id, String displayName, String color, int weight) {
        this(id, displayName, color, weight, null);
    }

    public ItemTier(String id, String displayName, String color, int weight, String dropColor) {
        this.id = id;
        this.displayName = displayName;
        this.color = color;
        this.weight = weight;
        this.dropColor = dropColor;
    }

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getDisplayName() { return displayName; }
    public void setDisplayName(String displayName) { this.displayName = displayName; }

    public String getColor() { return color; }
    public void setColor(String color) { this.color = color; }

    public int getWeight() { return weight; }
    public void setWeight(int weight) { this.weight = weight; }

    public String getDropColor() { return dropColor; }
    public void setDropColor(String dropColor) { this.dropColor = dropColor; }

    /**
     * Resolves the NamedTextColor for Scoreboard Team glowing aura on ground drops.
     */
    public NamedTextColor getDropNamedTextColor() {
        if (dropColor != null && !dropColor.isEmpty()) {
            NamedTextColor named = resolveNamedTextColor(dropColor);
            if (named != null) return named;
        }
        return resolveNamedTextColor(color);
    }

    public static NamedTextColor resolveNamedTextColor(String input) {
        if (input == null || input.isEmpty()) return NamedTextColor.WHITE;

        String clean = input.trim().toLowerCase().replaceAll("[&§<>#]", "");

        return switch (clean) {
            case "0", "black" -> NamedTextColor.BLACK;
            case "1", "dark_blue", "darkblue" -> NamedTextColor.DARK_BLUE;
            case "2", "dark_green", "darkgreen" -> NamedTextColor.DARK_GREEN;
            case "3", "dark_aqua", "darkaqua" -> NamedTextColor.DARK_AQUA;
            case "4", "dark_red", "darkred" -> NamedTextColor.DARK_RED;
            case "5", "dark_purple", "darkpurple", "purple" -> NamedTextColor.DARK_PURPLE;
            case "6", "gold", "orange" -> NamedTextColor.GOLD;
            case "7", "gray", "grey" -> NamedTextColor.GRAY;
            case "8", "dark_gray", "darkgray", "dark_grey" -> NamedTextColor.DARK_GRAY;
            case "9", "blue" -> NamedTextColor.BLUE;
            case "a", "green", "lime" -> NamedTextColor.GREEN;
            case "b", "aqua", "cyan", "light_blue" -> NamedTextColor.AQUA;
            case "c", "red" -> NamedTextColor.RED;
            case "d", "light_purple", "lightpurple", "pink", "magenta" -> NamedTextColor.LIGHT_PURPLE;
            case "e", "yellow" -> NamedTextColor.YELLOW;
            case "f", "white" -> NamedTextColor.WHITE;
            default -> {
                NamedTextColor match = NamedTextColor.NAMES.value(clean);
                yield match != null ? match : NamedTextColor.WHITE;
            }
        };
    }
}
