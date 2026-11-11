package com.github.orenaidaucac.royItems.tier_set;

import com.github.orenaidaucac.royItems.stat.StatModifier;
import com.github.orenaidaucac.royItems.stat.StatType;

import java.util.*;

/**
 * Represents an equipment set loaded from item-set.yml, supporting
 * multi-piece bonuses, configurable activation threshold, and dynamic active/inactive lore.
 */
public class ItemSet {
    private String id;
    private String displayName = "Equipment Set";
    private int requiredPieces = 0;
    private List<String> activeLore = new ArrayList<>();
    private List<String> inactiveLore = new ArrayList<>();
    // Pieces required -> (StatType -> StatModifier)
    private Map<Integer, Map<StatType, StatModifier>> bonuses = new HashMap<>();

    public ItemSet() {}

    public ItemSet(String id, String displayName, Map<Integer, Map<StatType, StatModifier>> bonuses) {
        this.id = id;
        this.displayName = displayName;
        this.bonuses = bonuses;
    }

    public ItemSet(String id, String displayName, int requiredPieces, List<String> activeLore,
                   List<String> inactiveLore, Map<Integer, Map<StatType, StatModifier>> bonuses) {
        this.id = id;
        this.displayName = displayName;
        this.requiredPieces = requiredPieces;
        if (activeLore != null) this.activeLore = new ArrayList<>(activeLore);
        if (inactiveLore != null) this.inactiveLore = new ArrayList<>(inactiveLore);
        if (bonuses != null) this.bonuses = bonuses;
    }

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getDisplayName() { return displayName; }
    public void setDisplayName(String displayName) { this.displayName = displayName; }

    public int getRequiredPieces() {
        if (requiredPieces > 0) return requiredPieces;
        if (bonuses != null && !bonuses.isEmpty()) {
            return Collections.min(bonuses.keySet());
        }
        return 1;
    }

    public void setRequiredPieces(int requiredPieces) {
        this.requiredPieces = requiredPieces;
    }

    public List<String> getActiveLore() {
        return activeLore;
    }

    public void setActiveLore(List<String> activeLore) {
        this.activeLore = activeLore != null ? new ArrayList<>(activeLore) : new ArrayList<>();
    }

    public List<String> getInactiveLore() {
        return inactiveLore;
    }

    public void setInactiveLore(List<String> inactiveLore) {
        this.inactiveLore = inactiveLore != null ? new ArrayList<>(inactiveLore) : new ArrayList<>();
    }

    /**
     * Resolves the lore lines for the set based on whether the set bonus is currently active.
     * Falls back to displayName if the respective lore list is empty.
     */
    public List<String> getLore(boolean active) {
        if (active) {
            if (activeLore != null && !activeLore.isEmpty()) {
                return Collections.unmodifiableList(activeLore);
            }
        } else {
            if (inactiveLore != null && !inactiveLore.isEmpty()) {
                return Collections.unmodifiableList(inactiveLore);
            }
        }
        if (displayName != null && !displayName.isEmpty()) {
            return List.of(displayName);
        }
        return List.of(id != null ? id : "");
    }

    /**
     * Checks if the equipped piece count satisfies the activation condition.
     */
    public boolean isBonusActive(int pieceCount) {
        return pieceCount >= getRequiredPieces();
    }

    public Map<Integer, Map<StatType, StatModifier>> getBonuses() { return bonuses; }
    public void setBonuses(Map<Integer, Map<StatType, StatModifier>> bonuses) { this.bonuses = bonuses; }
}
