package ai.timefold.wasm.service;

import java.util.HashMap;
import java.util.Map;

import com.dylibso.chicory.runtime.ExportFunction;
import com.dylibso.chicory.runtime.Instance;

/**
 * Caches WASM export function lookups to avoid repeated string-based lookups.
 *
 * Each call to instance.export(name) does a string lookup in the export table.
 * Since constraint evaluation calls the same functions thousands of times per solve,
 * caching these lookups significantly reduces overhead.
 */
public class ExportCache {
    private final Instance instance;
    private final Map<String, ExportFunction> cache = new HashMap<>();

    public ExportCache(Instance instance) {
        this.instance = instance;
    }

    /**
     * Get an exported function by name, using cache if available.
     */
    public ExportFunction get(String name) {
        return cache.computeIfAbsent(name, instance::export);
    }

    /**
     * Get the underlying instance.
     */
    public Instance getInstance() {
        return instance;
    }
}
