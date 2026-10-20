package com.github.orenaidaucac.royItems.tier_set;

/**
 * Represents an item quality tier loaded from item-tier.yml.
 */
public class ItemTier {
    private String id;
    private String displayName = "Common";
    private String color = "&f";
    private int weight = 0;

    public ItemTier() {}

    public ItemTier(String id, String displayName, String color, int weight) {
        this.id = id;
        this.displayName = displayName;
        this.color = color;
        this.weight = weight;
    }

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getDisplayName() { return displayName; }
    public void setDisplayName(String displayName) { this.displayName = displayName; }

    public String getColor() { return color; }
    public void setColor(String color) { this.color = color; }

    public int getWeight() { return weight; }
    public void setWeight(int weight) { this.weight = weight; }
}
