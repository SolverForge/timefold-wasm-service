package ai.timefold.wasm.service;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import ai.timefold.wasm.service.classgen.WasmObject;

/**
 * Caches predicate evaluation results to avoid redundant WASM calls.
 *
 * When a constraint filter like sameEmployee(shift1, shift2) is evaluated,
 * the result only changes if the relevant fields of shift1 or shift2 change.
 * By caching results keyed by memory pointers, we can skip WASM calls when
 * the same pair is evaluated again without field changes.
 *
 * The cache is invalidated when entities are modified during move application.
 * This is tracked via entity version numbers or explicit invalidation.
 */
public class PredicateCache {

    /**
     * Cache key for unary predicates (single entity).
     */
    private record UnaryKey(String functionName, int pointer) {}

    /**
     * Cache key for binary predicates (two entities).
     */
    private record BinaryKey(String functionName, int pointer1, int pointer2) {}

    /**
     * Cache key for ternary predicates.
     */
    private record TernaryKey(String functionName, int p1, int p2, int p3) {}

    private final Map<UnaryKey, Boolean> unaryCache = new ConcurrentHashMap<>();
    private final Map<BinaryKey, Boolean> binaryCache = new ConcurrentHashMap<>();
    private final Map<TernaryKey, Boolean> ternaryCache = new ConcurrentHashMap<>();

    // Version counter - incremented on each invalidation
    private volatile long version = 0;

    /**
     * Get cached result for a unary predicate, or null if not cached.
     */
    public Boolean getUnary(String functionName, int pointer) {
        return unaryCache.get(new UnaryKey(functionName, pointer));
    }

    /**
     * Cache result for a unary predicate.
     */
    public void putUnary(String functionName, int pointer, boolean result) {
        unaryCache.put(new UnaryKey(functionName, pointer), result);
    }

    /**
     * Get cached result for a binary predicate, or null if not cached.
     */
    public Boolean getBinary(String functionName, int pointer1, int pointer2) {
        return binaryCache.get(new BinaryKey(functionName, pointer1, pointer2));
    }

    /**
     * Cache result for a binary predicate.
     */
    public void putBinary(String functionName, int pointer1, int pointer2, boolean result) {
        binaryCache.put(new BinaryKey(functionName, pointer1, pointer2), result);
    }

    /**
     * Get cached result for a ternary predicate.
     */
    public Boolean getTernary(String functionName, int p1, int p2, int p3) {
        return ternaryCache.get(new TernaryKey(functionName, p1, p2, p3));
    }

    /**
     * Cache result for a ternary predicate.
     */
    public void putTernary(String functionName, int p1, int p2, int p3, boolean result) {
        ternaryCache.put(new TernaryKey(functionName, p1, p2, p3), result);
    }

    /**
     * Invalidate cache entries involving the given entity pointer.
     * Called when an entity's planning variable is changed.
     */
    public void invalidateEntity(int pointer) {
        // Remove all entries involving this pointer
        unaryCache.keySet().removeIf(k -> k.pointer == pointer);
        binaryCache.keySet().removeIf(k -> k.pointer1 == pointer || k.pointer2 == pointer);
        ternaryCache.keySet().removeIf(k -> k.p1 == pointer || k.p2 == pointer || k.p3 == pointer);
        version++;
    }

    /**
     * Clear all cached results. Called at the start of solving or on major changes.
     */
    public void clear() {
        unaryCache.clear();
        binaryCache.clear();
        ternaryCache.clear();
        version++;
    }

    /**
     * Get statistics about cache usage.
     */
    public String getStats() {
        return String.format("PredicateCache[unary=%d, binary=%d, ternary=%d, version=%d]",
                unaryCache.size(), binaryCache.size(), ternaryCache.size(), version);
    }

    public long getVersion() {
        return version;
    }
}
