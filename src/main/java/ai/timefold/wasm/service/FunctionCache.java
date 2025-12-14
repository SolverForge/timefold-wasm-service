package ai.timefold.wasm.service;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

import org.jboss.logging.Logger;

/**
 * Caches ALL WASM function call results to avoid redundant calls.
 *
 * Uses VERSION-BASED LAZY INVALIDATION:
 * - Each entity pointer has a version number
 * - Cache entries store versions at time of caching
 * - On lookup, version mismatch = cache miss (no iteration needed)
 * - invalidateEntity() is O(1) - just increment version
 *
 * This solves the scaling problem where iterating through all keys
 * for an entity became O(n) with large datasets.
 */
public class FunctionCache {
    private static final Logger LOG = Logger.getLogger(FunctionCache.class);

    // Debug counters
    private final AtomicLong hits = new AtomicLong();
    private final AtomicLong misses = new AtomicLong();
    private final AtomicLong invalidations = new AtomicLong();
    private final AtomicLong staleReads = new AtomicLong();

    // Version per entity pointer - O(1) invalidation
    private final Map<Integer, Long> entityVersions = new ConcurrentHashMap<>();
    private long globalVersion = 0;

    // Cache entry includes versions of all involved entities
    private record BoolEntry(boolean value, long v1) {}
    private record BoolEntry2(boolean value, long v1, long v2) {}
    private record BoolEntry3(boolean value, long v1, long v2, long v3) {}
    private record BoolEntry4(boolean value, long v1, long v2, long v3, long v4) {}
    private record BoolEntry5(boolean value, long v1, long v2, long v3, long v4, long v5) {}

    private record IntEntry(int value, long v1) {}
    private record IntEntry2(int value, long v1, long v2) {}
    private record IntEntry3(int value, long v1, long v2, long v3) {}
    private record IntEntry4(int value, long v1, long v2, long v3, long v4) {}
    private record IntEntry5(int value, long v1, long v2, long v3, long v4, long v5) {}

    private record LongEntry(long value, long v1) {}
    private record LongEntry2(long value, long v1, long v2) {}
    private record LongEntry3(long value, long v1, long v2, long v3) {}
    private record LongEntry4(long value, long v1, long v2, long v3, long v4) {}

    // Cache keys (without version - version checked on read)
    private record UnaryKey(String functionName, int p1) {}
    private record BinaryKey(String functionName, int p1, int p2) {}
    private record TernaryKey(String functionName, int p1, int p2, int p3) {}
    private record QuadKey(String functionName, int p1, int p2, int p3, int p4) {}
    private record PentaKey(String functionName, int p1, int p2, int p3, int p4, int p5) {}

    // Boolean caches
    private final Map<UnaryKey, BoolEntry> boolUnary = new ConcurrentHashMap<>();
    private final Map<BinaryKey, BoolEntry2> boolBinary = new ConcurrentHashMap<>();
    private final Map<TernaryKey, BoolEntry3> boolTernary = new ConcurrentHashMap<>();
    private final Map<QuadKey, BoolEntry4> boolQuad = new ConcurrentHashMap<>();
    private final Map<PentaKey, BoolEntry5> boolPenta = new ConcurrentHashMap<>();

    // Integer caches
    private final Map<UnaryKey, IntEntry> intUnary = new ConcurrentHashMap<>();
    private final Map<BinaryKey, IntEntry2> intBinary = new ConcurrentHashMap<>();
    private final Map<TernaryKey, IntEntry3> intTernary = new ConcurrentHashMap<>();
    private final Map<QuadKey, IntEntry4> intQuad = new ConcurrentHashMap<>();
    private final Map<PentaKey, IntEntry5> intPenta = new ConcurrentHashMap<>();

    // Long caches
    private final Map<UnaryKey, LongEntry> longUnary = new ConcurrentHashMap<>();
    private final Map<BinaryKey, LongEntry2> longBinary = new ConcurrentHashMap<>();
    private final Map<TernaryKey, LongEntry3> longTernary = new ConcurrentHashMap<>();
    private final Map<QuadKey, LongEntry4> longQuad = new ConcurrentHashMap<>();

    private long getVersion(int pointer) {
        return entityVersions.getOrDefault(pointer, 0L);
    }

    // ========== Boolean (Predicate) Methods ==========

    public Boolean getBool1(String fn, int p1) {
        var entry = boolUnary.get(new UnaryKey(fn, p1));
        if (entry == null) return null;
        if (entry.v1 != getVersion(p1)) {
            staleReads.incrementAndGet();
            return null;
        }
        hits.incrementAndGet();
        return entry.value;
    }

    public void putBool1(String fn, int p1, boolean result) {
        misses.incrementAndGet();
        boolUnary.put(new UnaryKey(fn, p1), new BoolEntry(result, getVersion(p1)));
    }

    public Boolean getBool2(String fn, int p1, int p2) {
        var entry = boolBinary.get(new BinaryKey(fn, p1, p2));
        if (entry == null) return null;
        if (entry.v1 != getVersion(p1) || entry.v2 != getVersion(p2)) {
            staleReads.incrementAndGet();
            return null;
        }
        long h = hits.incrementAndGet();
        if (h % 10000 == 0) {
            LOG.infof("Cache stats: hits=%d, misses=%d, stale=%d, invalidations=%d",
                    h, misses.get(), staleReads.get(), invalidations.get());
        }
        return entry.value;
    }

    public void putBool2(String fn, int p1, int p2, boolean result) {
        misses.incrementAndGet();
        boolBinary.put(new BinaryKey(fn, p1, p2), new BoolEntry2(result, getVersion(p1), getVersion(p2)));
    }

    public Boolean getBool3(String fn, int p1, int p2, int p3) {
        var entry = boolTernary.get(new TernaryKey(fn, p1, p2, p3));
        if (entry == null) return null;
        if (entry.v1 != getVersion(p1) || entry.v2 != getVersion(p2) || entry.v3 != getVersion(p3)) {
            staleReads.incrementAndGet();
            return null;
        }
        hits.incrementAndGet();
        return entry.value;
    }

    public void putBool3(String fn, int p1, int p2, int p3, boolean result) {
        misses.incrementAndGet();
        boolTernary.put(new TernaryKey(fn, p1, p2, p3), new BoolEntry3(result, getVersion(p1), getVersion(p2), getVersion(p3)));
    }

    public Boolean getBool4(String fn, int p1, int p2, int p3, int p4) {
        var entry = boolQuad.get(new QuadKey(fn, p1, p2, p3, p4));
        if (entry == null) return null;
        if (entry.v1 != getVersion(p1) || entry.v2 != getVersion(p2) || entry.v3 != getVersion(p3) || entry.v4 != getVersion(p4)) {
            staleReads.incrementAndGet();
            return null;
        }
        hits.incrementAndGet();
        return entry.value;
    }

    public void putBool4(String fn, int p1, int p2, int p3, int p4, boolean result) {
        misses.incrementAndGet();
        boolQuad.put(new QuadKey(fn, p1, p2, p3, p4), new BoolEntry4(result, getVersion(p1), getVersion(p2), getVersion(p3), getVersion(p4)));
    }

    public Boolean getBool5(String fn, int p1, int p2, int p3, int p4, int p5) {
        var entry = boolPenta.get(new PentaKey(fn, p1, p2, p3, p4, p5));
        if (entry == null) return null;
        if (entry.v1 != getVersion(p1) || entry.v2 != getVersion(p2) || entry.v3 != getVersion(p3) ||
                entry.v4 != getVersion(p4) || entry.v5 != getVersion(p5)) {
            staleReads.incrementAndGet();
            return null;
        }
        hits.incrementAndGet();
        return entry.value;
    }

    public void putBool5(String fn, int p1, int p2, int p3, int p4, int p5, boolean result) {
        misses.incrementAndGet();
        boolPenta.put(new PentaKey(fn, p1, p2, p3, p4, p5),
                new BoolEntry5(result, getVersion(p1), getVersion(p2), getVersion(p3), getVersion(p4), getVersion(p5)));
    }

    // ========== Integer (Mapper/ToInt) Methods ==========

    public Integer getInt1(String fn, int p1) {
        var entry = intUnary.get(new UnaryKey(fn, p1));
        if (entry == null) return null;
        if (entry.v1 != getVersion(p1)) {
            staleReads.incrementAndGet();
            return null;
        }
        hits.incrementAndGet();
        return entry.value;
    }

    public void putInt1(String fn, int p1, int result) {
        misses.incrementAndGet();
        intUnary.put(new UnaryKey(fn, p1), new IntEntry(result, getVersion(p1)));
    }

    public Integer getInt2(String fn, int p1, int p2) {
        var entry = intBinary.get(new BinaryKey(fn, p1, p2));
        if (entry == null) return null;
        if (entry.v1 != getVersion(p1) || entry.v2 != getVersion(p2)) {
            staleReads.incrementAndGet();
            return null;
        }
        hits.incrementAndGet();
        return entry.value;
    }

    public void putInt2(String fn, int p1, int p2, int result) {
        misses.incrementAndGet();
        intBinary.put(new BinaryKey(fn, p1, p2), new IntEntry2(result, getVersion(p1), getVersion(p2)));
    }

    public Integer getInt3(String fn, int p1, int p2, int p3) {
        var entry = intTernary.get(new TernaryKey(fn, p1, p2, p3));
        if (entry == null) return null;
        if (entry.v1 != getVersion(p1) || entry.v2 != getVersion(p2) || entry.v3 != getVersion(p3)) {
            staleReads.incrementAndGet();
            return null;
        }
        hits.incrementAndGet();
        return entry.value;
    }

    public void putInt3(String fn, int p1, int p2, int p3, int result) {
        misses.incrementAndGet();
        intTernary.put(new TernaryKey(fn, p1, p2, p3), new IntEntry3(result, getVersion(p1), getVersion(p2), getVersion(p3)));
    }

    public Integer getInt4(String fn, int p1, int p2, int p3, int p4) {
        var entry = intQuad.get(new QuadKey(fn, p1, p2, p3, p4));
        if (entry == null) return null;
        if (entry.v1 != getVersion(p1) || entry.v2 != getVersion(p2) || entry.v3 != getVersion(p3) || entry.v4 != getVersion(p4)) {
            staleReads.incrementAndGet();
            return null;
        }
        hits.incrementAndGet();
        return entry.value;
    }

    public void putInt4(String fn, int p1, int p2, int p3, int p4, int result) {
        misses.incrementAndGet();
        intQuad.put(new QuadKey(fn, p1, p2, p3, p4), new IntEntry4(result, getVersion(p1), getVersion(p2), getVersion(p3), getVersion(p4)));
    }

    public Integer getInt5(String fn, int p1, int p2, int p3, int p4, int p5) {
        var entry = intPenta.get(new PentaKey(fn, p1, p2, p3, p4, p5));
        if (entry == null) return null;
        if (entry.v1 != getVersion(p1) || entry.v2 != getVersion(p2) || entry.v3 != getVersion(p3) ||
                entry.v4 != getVersion(p4) || entry.v5 != getVersion(p5)) {
            staleReads.incrementAndGet();
            return null;
        }
        hits.incrementAndGet();
        return entry.value;
    }

    public void putInt5(String fn, int p1, int p2, int p3, int p4, int p5, int result) {
        misses.incrementAndGet();
        intPenta.put(new PentaKey(fn, p1, p2, p3, p4, p5),
                new IntEntry5(result, getVersion(p1), getVersion(p2), getVersion(p3), getVersion(p4), getVersion(p5)));
    }

    // ========== Long (ToLong) Methods ==========

    public Long getLong1(String fn, int p1) {
        var entry = longUnary.get(new UnaryKey(fn, p1));
        if (entry == null) return null;
        if (entry.v1 != getVersion(p1)) {
            staleReads.incrementAndGet();
            return null;
        }
        hits.incrementAndGet();
        return entry.value;
    }

    public void putLong1(String fn, int p1, long result) {
        misses.incrementAndGet();
        longUnary.put(new UnaryKey(fn, p1), new LongEntry(result, getVersion(p1)));
    }

    public Long getLong2(String fn, int p1, int p2) {
        var entry = longBinary.get(new BinaryKey(fn, p1, p2));
        if (entry == null) return null;
        if (entry.v1 != getVersion(p1) || entry.v2 != getVersion(p2)) {
            staleReads.incrementAndGet();
            return null;
        }
        hits.incrementAndGet();
        return entry.value;
    }

    public void putLong2(String fn, int p1, int p2, long result) {
        misses.incrementAndGet();
        longBinary.put(new BinaryKey(fn, p1, p2), new LongEntry2(result, getVersion(p1), getVersion(p2)));
    }

    public Long getLong3(String fn, int p1, int p2, int p3) {
        var entry = longTernary.get(new TernaryKey(fn, p1, p2, p3));
        if (entry == null) return null;
        if (entry.v1 != getVersion(p1) || entry.v2 != getVersion(p2) || entry.v3 != getVersion(p3)) {
            staleReads.incrementAndGet();
            return null;
        }
        hits.incrementAndGet();
        return entry.value;
    }

    public void putLong3(String fn, int p1, int p2, int p3, long result) {
        misses.incrementAndGet();
        longTernary.put(new TernaryKey(fn, p1, p2, p3), new LongEntry3(result, getVersion(p1), getVersion(p2), getVersion(p3)));
    }

    public Long getLong4(String fn, int p1, int p2, int p3, int p4) {
        var entry = longQuad.get(new QuadKey(fn, p1, p2, p3, p4));
        if (entry == null) return null;
        if (entry.v1 != getVersion(p1) || entry.v2 != getVersion(p2) || entry.v3 != getVersion(p3) || entry.v4 != getVersion(p4)) {
            staleReads.incrementAndGet();
            return null;
        }
        hits.incrementAndGet();
        return entry.value;
    }

    public void putLong4(String fn, int p1, int p2, int p3, int p4, long result) {
        misses.incrementAndGet();
        longQuad.put(new QuadKey(fn, p1, p2, p3, p4), new LongEntry4(result, getVersion(p1), getVersion(p2), getVersion(p3), getVersion(p4)));
    }

    // ========== O(1) Invalidation ==========

    /**
     * Invalidate all cached results involving the given entity pointer.
     * O(1) - just bumps the version number. Stale entries detected lazily on read.
     */
    public void invalidateEntity(int pointer) {
        long newVersion = entityVersions.compute(pointer, (k, v) -> (v == null ? 0L : v) + 1);
        long inv = invalidations.incrementAndGet();
        globalVersion++;

        if (inv % 1000 == 0) {
            int totalEntries = boolUnary.size() + boolBinary.size() + boolTernary.size() + boolQuad.size() + boolPenta.size() +
                    intUnary.size() + intBinary.size() + intTernary.size() + intQuad.size() + intPenta.size() +
                    longUnary.size() + longBinary.size() + longTernary.size() + longQuad.size();
            LOG.infof("Inv#%d ptr=%x newVer=%d entities=%d entries=%d",
                    inv, pointer, newVersion, entityVersions.size(), totalEntries);
        }
    }

    public void clear() {
        boolUnary.clear();
        boolBinary.clear();
        boolTernary.clear();
        boolQuad.clear();
        boolPenta.clear();
        intUnary.clear();
        intBinary.clear();
        intTernary.clear();
        intQuad.clear();
        intPenta.clear();
        longUnary.clear();
        longBinary.clear();
        longTernary.clear();
        longQuad.clear();
        entityVersions.clear();
        globalVersion++;
    }

    public long getVersion() {
        return globalVersion;
    }

    public String getStats() {
        int boolCount = boolUnary.size() + boolBinary.size() + boolTernary.size() + boolQuad.size() + boolPenta.size();
        int intCount = intUnary.size() + intBinary.size() + intTernary.size() + intQuad.size() + intPenta.size();
        int longCount = longUnary.size() + longBinary.size() + longTernary.size() + longQuad.size();
        return String.format("FunctionCache[bool=%d, int=%d, long=%d, entities=%d, hits=%d, misses=%d, stale=%d]",
                boolCount, intCount, longCount, entityVersions.size(), hits.get(), misses.get(), staleReads.get());
    }
}
