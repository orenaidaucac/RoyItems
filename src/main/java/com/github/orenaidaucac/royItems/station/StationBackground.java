package com.github.orenaidaucac.royItems.station;

import org.bukkit.configuration.ConfigurationSection;

import java.util.ArrayList;
import java.util.List;

/**
 * Decorative background item and interactive buttons in a station GUI.
 */
public class StationBackground {

    private final String id;
    private final String material;
    private final String name;
    private final List<String> lore;
    private final List<Integer> slots;
    private final List<String> clickCommands;

    public StationBackground(String id, String material, String name, List<String> lore,
                             List<Integer> slots, List<String> clickCommands) {
        this.id = id;
        this.material = material != null ? material : "BLACK_STAINED_GLASS_PANE";
        this.name = name;
        this.lore = lore != null ? lore : new ArrayList<>();
        this.slots = slots != null ? slots : new ArrayList<>();
        this.clickCommands = clickCommands != null ? clickCommands : new ArrayList<>();
    }

    public static StationBackground fromSection(String id, ConfigurationSection sec) {
        if (sec == null) return null;
        String mat = sec.getString("material", "BLACK_STAINED_GLASS_PANE");
        String name = sec.getString("name");
        List<String> lore = sec.getStringList("lore");
        List<String> rawSlots = sec.getStringList("slots");
        List<Integer> slots = parseSlots(rawSlots);
        List<String> clickCommands = sec.getStringList("click-commands");

        return new StationBackground(id, mat, name, lore, slots, clickCommands);
    }

    public static List<Integer> parseSlots(List<?> rawList) {
        List<Integer> slots = new ArrayList<>();
        if (rawList == null) return slots;

        for (Object obj : rawList) {
            if (obj instanceof Number num) {
                slots.add(num.intValue());
            } else if (obj instanceof String str) {
                String s = str.trim();
                if (s.contains("-")) {
                    String[] parts = s.split("-", 2);
                    try {
                        int start = Integer.parseInt(parts[0].trim());
                        int end = Integer.parseInt(parts[1].trim());
                        int min = Math.min(start, end);
                        int max = Math.max(start, end);
                        for (int i = min; i <= max; i++) {
                            slots.add(i);
                        }
                    } catch (NumberFormatException ignored) {}
                } else {
                    try {
                        slots.add(Integer.parseInt(s));
                    } catch (NumberFormatException ignored) {}
                }
            }
        }
        return slots;
    }

    public String getId() { return id; }
    public String getMaterial() { return material; }
    public String getName() { return name; }
    public List<String> getLore() { return lore; }
    public List<Integer> getSlots() { return slots; }
    public List<String> getClickCommands() { return clickCommands; }
}
