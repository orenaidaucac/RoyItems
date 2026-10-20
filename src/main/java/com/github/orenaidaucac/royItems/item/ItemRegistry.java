package com.github.orenaidaucac.royItems.item;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Thread-safe registry holding all loaded ItemDefinitions.
 */
public class ItemRegistry {

    // Key format: "TYPE:ID" (upper-case TYPE, lower-case ID)
    private final Map<String, ItemDefinition> definitions = new ConcurrentHashMap<>();

    private String buildKey(ItemType type, String id) {
        if (type == null || id == null) return null;
        return type.name() + ":" + id.trim().toLowerCase();
    }

    public void register(ItemDefinition definition) {
        if (definition == null) return;
        String key = buildKey(definition.getType(), definition.getId());
        if (key != null) {
            definitions.put(key, definition);
        }
    }

    public void unregister(ItemType type, String id) {
        String key = buildKey(type, id);
        if (key != null) {
            definitions.remove(key);
        }
    }

    public ItemDefinition get(ItemType type, String id) {
        String key = buildKey(type, id);
        if (key == null) return null;
        return definitions.get(key);
    }

    public boolean has(ItemType type, String id) {
        String key = buildKey(type, id);
        return key != null && definitions.containsKey(key);
    }

    public List<ItemDefinition> getByType(ItemType type) {
        List<ItemDefinition> list = new ArrayList<>();
        if (type == null) return list;
        for (ItemDefinition def : definitions.values()) {
            if (def.getType() == type) {
                list.add(def);
            }
        }
        return list;
    }

    public Collection<ItemDefinition> getAll() {
        return Collections.unmodifiableCollection(definitions.values());
    }

    public void clear() {
        definitions.clear();
    }

    public int size() {
        return definitions.size();
    }
}
