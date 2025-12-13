package ai.timefold.wasm.service.incremental;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Manages hash indices for fast entity lookups during incremental score calculation.
 * <p>
 * Indices enable O(1) lookups for join operations instead of O(n) scans.
 * For example, an index on "employee" allows quickly finding all shifts
 * assigned to a specific employee.
 */
public class IndexManager {
    private final Map<String, EntityIndex> indices = new ConcurrentHashMap<>();

    /**
     * Creates a new index for entity lookups.
     *
     * @param indexName Unique identifier for this index
     * @return The created or existing EntityIndex
     */
    public EntityIndex createIndex(String indexName) {
        return indices.computeIfAbsent(indexName, k -> new EntityIndex());
    }

    /**
     * Gets an existing index by name.
     *
     * @param indexName The index identifier
     * @return The EntityIndex, or null if not found
     */
    public EntityIndex getIndex(String indexName) {
        return indices.get(indexName);
    }

    /**
     * Removes all indices and their data.
     */
    public void clear() {
        indices.values().forEach(EntityIndex::clear);
        indices.clear();
    }

    /**
     * Returns the number of indices managed.
     */
    public int size() {
        return indices.size();
    }

    /**
     * Hash index for entity lookups.
     * <p>
     * Maps a key (e.g., Employee instance) to a set of entities (e.g., Shifts assigned to that Employee).
     */
    public static class EntityIndex {
        private final Map<Object, Set<Object>> hashIndex = new HashMap<>();

        /**
         * Adds an entity to the index under the given key.
         *
         * @param key The lookup key (e.g., an Employee)
         * @param entity The entity to index (e.g., a Shift)
         */
        public void add(Object key, Object entity) {
            if (key == null) {
                return; // Don't index null keys
            }
            hashIndex.computeIfAbsent(key, k -> new HashSet<>()).add(entity);
        }

        /**
         * Retrieves all entities indexed under the given key.
         *
         * @param key The lookup key
         * @return Set of entities, or empty set if key not found
         */
        public Set<Object> get(Object key) {
            if (key == null) {
                return Collections.emptySet();
            }
            Set<Object> entities = hashIndex.get(key);
            return entities != null ? entities : Collections.emptySet();
        }

        /**
         * Removes an entity from the index under the given key.
         *
         * @param key The lookup key
         * @param entity The entity to remove
         */
        public void remove(Object key, Object entity) {
            if (key == null) {
                return;
            }
            Set<Object> entities = hashIndex.get(key);
            if (entities != null) {
                entities.remove(entity);
                if (entities.isEmpty()) {
                    hashIndex.remove(key);
                }
            }
        }

        /**
         * Removes all entities indexed under the given key.
         *
         * @param key The lookup key to remove
         */
        public void removeKey(Object key) {
            if (key != null) {
                hashIndex.remove(key);
            }
        }

        /**
         * Clears all index data.
         */
        public void clear() {
            hashIndex.clear();
        }

        /**
         * Returns the number of unique keys in the index.
         */
        public int keyCount() {
            return hashIndex.size();
        }

        /**
         * Returns the total number of entities indexed (across all keys).
         */
        public int entityCount() {
            return hashIndex.values().stream()
                    .mapToInt(Set::size)
                    .sum();
        }
    }
}
