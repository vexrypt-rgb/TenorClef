package adris.altoclef.knowledge;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Small in-memory cache of selected {@link KnowledgeFact}s (not a full DB).
 * Bounded LRU by insertion/access order.
 */
public final class KnowledgeFactCache {

    public static final int DEFAULT_CAPACITY = 64;

    private final int capacity;
    private final LinkedHashMap<String, KnowledgeFact<?>> map;

    public KnowledgeFactCache() {
        this(DEFAULT_CAPACITY);
    }

    public KnowledgeFactCache(int capacity) {
        if (capacity < 1) {
            throw new IllegalArgumentException("capacity");
        }
        this.capacity = capacity;
        this.map = new LinkedHashMap<>(capacity + 1, 0.75f, true) {
            @Override
            protected boolean removeEldestEntry(Map.Entry<String, KnowledgeFact<?>> eldest) {
                return size() > KnowledgeFactCache.this.capacity;
            }
        };
    }

    public int capacity() {
        return capacity;
    }

    public int size() {
        return map.size();
    }

    public void clear() {
        map.clear();
    }

    public void put(String key, KnowledgeFact<?> fact) {
        if (key == null) {
            throw new IllegalArgumentException("key");
        }
        if (fact == null || !fact.isKnown()) {
            map.remove(key);
            return;
        }
        map.put(key, fact);
    }

    @SuppressWarnings("unchecked")
    public <T> KnowledgeFact<T> get(String key) {
        KnowledgeFact<?> fact = map.get(key);
        if (fact == null) {
            return KnowledgeFact.unknown();
        }
        return (KnowledgeFact<T>) fact;
    }

    public boolean contains(String key) {
        return map.containsKey(key);
    }

    public KnowledgeFact<?> remove(String key) {
        KnowledgeFact<?> removed = map.remove(key);
        return removed != null ? removed : KnowledgeFact.unknown();
    }
}
