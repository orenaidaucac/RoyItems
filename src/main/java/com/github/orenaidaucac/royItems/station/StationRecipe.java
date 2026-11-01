package com.github.orenaidaucac.royItems.station;

import org.bukkit.configuration.ConfigurationSection;

import java.util.ArrayList;
import java.util.List;

/**
 * Encapsulates a crafting recipe inside a Station.
 */
public class StationRecipe {

    private final String id;
    private final List<RecipeIngredient> inputs = new ArrayList<>();
    private final List<RecipeIngredient> outputs = new ArrayList<>();
    private final RecipeIcon icon;
    private final List<Integer> slots = new ArrayList<>();
    private final int page;
    private final boolean hideDisplayName;
    private final boolean hideLore;
    private final boolean hideAttributes;
    private final int craftingTime;

    public StationRecipe(String id, List<RecipeIngredient> inputs, List<RecipeIngredient> outputs,
                         RecipeIcon icon, List<Integer> slots, int page,
                         boolean hideDisplayName, boolean hideLore, boolean hideAttributes,
                         int craftingTime) {
        this.id = id;
        if (inputs != null) this.inputs.addAll(inputs);
        if (outputs != null) this.outputs.addAll(outputs);
        this.icon = icon;
        if (slots != null) this.slots.addAll(slots);
        this.page = Math.max(1, page);
        this.hideDisplayName = hideDisplayName;
        this.hideLore = hideLore;
        this.hideAttributes = hideAttributes;
        this.craftingTime = Math.max(0, craftingTime);
    }

    public static StationRecipe fromSection(String id, ConfigurationSection sec) {
        if (sec == null) return null;

        List<RecipeIngredient> inputs = new ArrayList<>();
        for (String raw : sec.getStringList("input")) {
            RecipeIngredient ing = RecipeIngredient.parse(raw);
            if (ing != null) inputs.add(ing);
        }

        List<RecipeIngredient> outputs = new ArrayList<>();
        for (String raw : sec.getStringList("output")) {
            RecipeIngredient ing = RecipeIngredient.parse(raw);
            if (ing != null) outputs.add(ing);
        }

        RecipeIcon icon = null;
        if (sec.isList("icon")) {
            List<String> iconList = sec.getStringList("icon");
            if (!iconList.isEmpty()) {
                icon = RecipeIcon.parse(iconList.get(0));
            }
        } else if (sec.isString("icon")) {
            icon = RecipeIcon.parse(sec.getString("icon"));
        }
        if (icon == null) {
            icon = RecipeIcon.parse("STONE");
        }

        List<Integer> slots = StationBackground.parseSlots(sec.getStringList("slots"));
        int page = sec.getInt("page", 1);

        boolean hideName = sec.getBoolean("options.hide-displayname", false);
        boolean hideLore = sec.getBoolean("options.hide-lore", false);
        boolean hideAttr = sec.getBoolean("options.hide-attributes", false);
        int time = sec.getInt("options.crafting-time", 0);

        return new StationRecipe(id, inputs, outputs, icon, slots, page, hideName, hideLore, hideAttr, time);
    }

    public String getId() { return id; }
    public List<RecipeIngredient> getInputs() { return inputs; }
    public List<RecipeIngredient> getOutputs() { return outputs; }
    public RecipeIcon getIcon() { return icon; }
    public List<Integer> getSlots() { return slots; }
    public int getPage() { return page; }
    public boolean isHideDisplayName() { return hideDisplayName; }
    public boolean isHideLore() { return hideLore; }
    public boolean isHideAttributes() { return hideAttributes; }
    public int getCraftingTime() { return craftingTime; }
}
