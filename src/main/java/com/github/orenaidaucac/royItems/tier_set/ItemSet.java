package com.github.orenaidaucac.royItems.tier_set;

import com.github.orenaidaucac.royItems.stat.StatModifier;
import com.github.orenaidaucac.royItems.stat.StatType;

import java.util.HashMap;
import java.util.Map;

/**
 * Represents an equipment set loaded from item-set.yml.
 */
public class ItemSet {
    private String id;
    private String displayName = "Equipment Set";
    // Pieces required -> (StatType -> StatModifier)
    private Map<Integer, Map<StatType, StatModifier>> bonuses = new HashMap<>();

    public ItemSet() {}

    public ItemSet(String id, String displayName, Map<Integer, Map<StatType, StatModifier>> bonuses) {
        this.id = id;
        this.displayName = displayName;
        this.bonuses = bonuses;
    }

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getDisplayName() { return displayName; }
    public void setDisplayName(String displayName) { this.displayName = displayName; }

    public Map<Integer, Map<StatType, StatModifier>> getBonuses() { return bonuses; }
    public void setBonuses(Map<Integer, Map<StatType, StatModifier>> bonuses) { this.bonuses = bonuses; }
}
