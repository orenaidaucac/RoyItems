package com.github.orenaidaucac.royItems.item;

import java.util.*;
import java.util.concurrent.locks.ReentrantReadWriteLock;

/**
 * Thread-safe, insertion-ordered registry holding all loaded ItemDefinitions.
 * Preserves the file configuration order (top-to-bottom).
 */
public class ItemRegistry {

    // Key format: "TYPE:ID" (upper-case TYPE, lower-case ID)
    private final Map<String, ItemDefinition> definitions = new LinkedHashMap<>();
    private final ReentrantReadWriteLock rwLock = new ReentrantReadWriteLock();

    private String buildKey(ItemType type, String id) {
        if (type == null || id == null) return null;
        return type.name() + ":" + id.trim().toLowerCase();
    }

    public void register(ItemDefinition definition) {
        if (definition == null) return;
        String key = buildKey(definition.getType(), definition.getId());
        if (key != null) {
            rwLock.writeLock().lock();
            try {
                definitions.put(key, definition);
            } finally {
                rwLock.writeLock().unlock();
            }
        }
    }

    public void unregister(ItemType type, String id) {
        String key = buildKey(type, id);
        if (key != null) {
            rwLock.writeLock().lock();
            try {
                definitions.remove(key);
            } finally {
                rwLock.writeLock().unlock();
            }
        }
    }

    public ItemDefinition get(ItemType type, String id) {
        String key = buildKey(type, id);
        if (key == null) return null;
        rwLock.readLock().lock();
        try {
            return definitions.get(key);
        } finally {
            rwLock.readLock().unlock();
        }
    }

    public boolean has(ItemType type, String id) {
        String key = buildKey(type, id);
        if (key == null) return false;
        rwLock.readLock().lock();
        try {
            return definitions.containsKey(key);
        } finally {
            rwLock.readLock().unlock();
        }
    }

    public List<ItemDefinition> getByType(ItemType type) {
        List<ItemDefinition> list = new ArrayList<>();
        if (type == null) return list;
        rwLock.readLock().lock();
        try {
            for (ItemDefinition def : definitions.values()) {
                if (def.getType() == type) {
                    list.add(def);
                }
            }
            return list;
        } finally {
            rwLock.readLock().unlock();
        }
    }

    public Collection<ItemDefinition> getAll() {
        rwLock.readLock().lock();
        try {
            return Collections.unmodifiableList(new ArrayList<>(definitions.values()));
        } finally {
            rwLock.readLock().unlock();
        }
    }

    public void clear() {
        rwLock.writeLock().lock();
        try {
            definitions.clear();
        } finally {
            rwLock.writeLock().unlock();
        }
    }

    public int size() {
        rwLock.readLock().lock();
        try {
            return definitions.size();
        } finally {
            rwLock.readLock().unlock();
        }
    }
}
