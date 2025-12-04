package ai.timefold.wasm.service.dto;

import java.util.Comparator;
import java.util.function.BiFunction;
import java.util.function.BiPredicate;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.function.ToIntBiFunction;
import java.util.function.ToIntFunction;
import java.util.function.ToLongBiFunction;
import java.util.function.ToLongFunction;

import ai.timefold.solver.core.api.function.PentaFunction;
import ai.timefold.solver.core.api.function.PentaPredicate;
import ai.timefold.solver.core.api.function.QuadFunction;
import ai.timefold.solver.core.api.function.QuadPredicate;
import ai.timefold.solver.core.api.function.ToIntQuadFunction;
import ai.timefold.solver.core.api.function.ToIntTriFunction;
import ai.timefold.solver.core.api.function.ToLongQuadFunction;
import ai.timefold.solver.core.api.function.ToLongTriFunction;
import ai.timefold.solver.core.api.function.TriFunction;
import ai.timefold.solver.core.api.function.TriPredicate;
import ai.timefold.wasm.service.ExportCache;
import ai.timefold.wasm.service.PredicateCache;
import ai.timefold.wasm.service.SolverResource;
import ai.timefold.wasm.service.classgen.WasmList;
import ai.timefold.wasm.service.classgen.WasmObject;

import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

import com.dylibso.chicory.runtime.ExportFunction;
import com.dylibso.chicory.runtime.Instance;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonValue;

@NullMarked
public class WasmFunction {
    @JsonValue
    final String wasmFunctionName;

    @Nullable
    String relationFunctionName;
    @Nullable
    String hashFunctionName;
    @Nullable
    String comparatorFunctionName;


    @JsonCreator
    public WasmFunction(String functionName) {
        this.wasmFunctionName = functionName;
    }

    /**
     * Get an export function using the cached lookup from SolverResource.
     * Falls back to direct lookup if no cache is available.
     */
    private ExportFunction getExport(String name, Instance instance) {
        ExportCache cache = SolverResource.EXPORT_CACHE.get();
        if (cache != null) {
            return cache.get(name);
        }
        // Fallback for testing or cases where cache isn't set up
        return instance.export(name);
    }

    public Object asPredicate(int tupleSize, Instance instance) {
        var wasmFunction = getExport(wasmFunctionName, instance);
        var funcName = wasmFunctionName;
        return switch (tupleSize) {
            case 1 -> (Predicate<WasmObject>) a -> {
                var cache = SolverResource.PREDICATE_CACHE.get();
                int p1 = a.getMemoryPointer();
                if (cache != null) {
                    Boolean cached = cache.getUnary(funcName, p1);
                    if (cached != null) return cached;
                }
                boolean result = wasmFunction.apply(p1)[0] != 0;
                if (cache != null) cache.putUnary(funcName, p1, result);
                return result;
            };
            case 2 -> (BiPredicate<WasmObject, WasmObject>) (a, b) -> {
                var cache = SolverResource.PREDICATE_CACHE.get();
                int p1 = a.getMemoryPointer();
                int p2 = b.getMemoryPointer();
                if (cache != null) {
                    Boolean cached = cache.getBinary(funcName, p1, p2);
                    if (cached != null) return cached;
                }
                boolean result = wasmFunction.apply(p1, p2)[0] != 0;
                if (cache != null) cache.putBinary(funcName, p1, p2, result);
                return result;
            };
            case 3 -> (TriPredicate<WasmObject, WasmObject, WasmObject>) (a, b, c) -> {
                var cache = SolverResource.PREDICATE_CACHE.get();
                int p1 = a.getMemoryPointer();
                int p2 = b.getMemoryPointer();
                int p3 = c.getMemoryPointer();
                if (cache != null) {
                    Boolean cached = cache.getTernary(funcName, p1, p2, p3);
                    if (cached != null) return cached;
                }
                boolean result = wasmFunction.apply(p1, p2, p3)[0] != 0;
                if (cache != null) cache.putTernary(funcName, p1, p2, p3, result);
                return result;
            };
            case 4 -> (QuadPredicate<WasmObject, WasmObject, WasmObject, WasmObject>) (a, b, c, d) -> wasmFunction.apply(
                    a.getMemoryPointer(),
                    b.getMemoryPointer(),
                    c.getMemoryPointer(),
                    d.getMemoryPointer())[0] != 0;
            case 5 -> (PentaPredicate<WasmObject, WasmObject, WasmObject, WasmObject, WasmObject>) (a, b, c, d, e) -> wasmFunction.apply(
                    a.getMemoryPointer(),
                    b.getMemoryPointer(),
                    c.getMemoryPointer(),
                    d.getMemoryPointer(),
                    e.getMemoryPointer())[0] != 0;
            default -> throw new IllegalArgumentException("Unexpected value: " + tupleSize);
        };
    }

    public Object asFunction(int tupleSize, Instance instance) {
        if (comparatorFunctionName == null) {
            if (relationFunctionName == null) {
                return asFunctionWithDefaultEqualsAndComparator(tupleSize, instance);
            } else {
                return asFunctionWithCustomEquals(tupleSize, instance);
            }
        } else {
            return asFunctionWithCustomComparator(tupleSize, instance);
        }
    }

    private Object asFunctionWithCustomComparator(int tupleSize, Instance instance) {
        var wasmFunction = getExport(wasmFunctionName, instance);
        var comparator = getComparator(instance);
        return switch (tupleSize) {
            case 1 -> (Function<WasmObject, WasmObject>) a -> WasmObject.ofExisting(instance, (int) wasmFunction.apply(a.getMemoryPointer())[0], comparator);
            case 2 -> (BiFunction<WasmObject, WasmObject, WasmObject>) (a, b) -> WasmObject.ofExisting(instance, (int) wasmFunction.apply(
                    a.getMemoryPointer(),
                    b.getMemoryPointer())[0], comparator);
            case 3 -> (TriFunction<WasmObject, WasmObject, WasmObject, WasmObject>) (a, b, c) -> WasmObject.ofExisting(instance, (int) wasmFunction.apply(
                    a.getMemoryPointer(),
                    b.getMemoryPointer(),
                    c.getMemoryPointer())[0], comparator);
            case 4 -> (QuadFunction<WasmObject, WasmObject, WasmObject, WasmObject, WasmObject>) (a, b, c, d) -> WasmObject.ofExisting(instance, (int) wasmFunction.apply(
                    a.getMemoryPointer(),
                    b.getMemoryPointer(),
                    c.getMemoryPointer(),
                    d.getMemoryPointer())[0], comparator);
            case 5 -> (PentaFunction<WasmObject, WasmObject, WasmObject, WasmObject, WasmObject, WasmObject>) (a, b, c, d, e) -> WasmObject.ofExisting(instance, (int) wasmFunction.apply(
                    a.getMemoryPointer(),
                    b.getMemoryPointer(),
                    c.getMemoryPointer(),
                    d.getMemoryPointer(),
                    e.getMemoryPointer())[0], comparator);
            default -> throw new IllegalArgumentException("Unexpected value: " + tupleSize);
        };
    }

    private Object asFunctionWithCustomEquals(int tupleSize, Instance instance) {
        var wasmFunction = getExport(wasmFunctionName, instance);
        var relation = getRelation(instance);
        var hasher = getHasher(instance);
        return switch (tupleSize) {
            case 1 -> (Function<WasmObject, WasmObject>) a -> WasmObject.ofExisting(instance, (int) wasmFunction.apply(a.getMemoryPointer())[0],
                    relation, hasher);
            case 2 -> (BiFunction<WasmObject, WasmObject, WasmObject>) (a, b) -> WasmObject.ofExisting(instance, (int) wasmFunction.apply(
                    a.getMemoryPointer(),
                    b.getMemoryPointer())[0], relation, hasher);
            case 3 -> (TriFunction<WasmObject, WasmObject, WasmObject, WasmObject>) (a, b, c) -> WasmObject.ofExisting(instance, (int) wasmFunction.apply(
                    a.getMemoryPointer(),
                    b.getMemoryPointer(),
                    c.getMemoryPointer())[0], relation, hasher);
            case 4 -> (QuadFunction<WasmObject, WasmObject, WasmObject, WasmObject, WasmObject>) (a, b, c, d) -> WasmObject.ofExisting(instance, (int) wasmFunction.apply(
                    a.getMemoryPointer(),
                    b.getMemoryPointer(),
                    c.getMemoryPointer(),
                    d.getMemoryPointer())[0], relation, hasher);
            case 5 -> (PentaFunction<WasmObject, WasmObject, WasmObject, WasmObject, WasmObject, WasmObject>) (a, b, c, d, e) -> WasmObject.ofExisting(instance, (int) wasmFunction.apply(
                    a.getMemoryPointer(),
                    b.getMemoryPointer(),
                    c.getMemoryPointer(),
                    d.getMemoryPointer(),
                    e.getMemoryPointer())[0], relation, hasher);
            default -> throw new IllegalArgumentException("Unexpected value: " + tupleSize);
        };
    }

    private Object asFunctionWithDefaultEqualsAndComparator(int tupleSize, Instance instance) {
        var wasmFunction = getExport(wasmFunctionName, instance);
        return switch (tupleSize) {
            case 1 -> (Function<WasmObject, WasmObject>) a -> WasmObject.ofExisting(instance, (int) wasmFunction.apply(a.getMemoryPointer())[0]);
            case 2 -> (BiFunction<WasmObject, WasmObject, WasmObject>) (a, b) -> WasmObject.ofExisting(instance, (int) wasmFunction.apply(
                    a.getMemoryPointer(),
                    b.getMemoryPointer())[0]);
            case 3 -> (TriFunction<WasmObject, WasmObject, WasmObject, WasmObject>) (a, b, c) -> WasmObject.ofExisting(instance, (int) wasmFunction.apply(
                    a.getMemoryPointer(),
                    b.getMemoryPointer(),
                    c.getMemoryPointer())[0]);
            case 4 -> (QuadFunction<WasmObject, WasmObject, WasmObject, WasmObject, WasmObject>) (a, b, c, d) -> WasmObject.ofExisting(instance, (int) wasmFunction.apply(
                    a.getMemoryPointer(),
                    b.getMemoryPointer(),
                    c.getMemoryPointer(),
                    d.getMemoryPointer())[0]);
            case 5 -> (PentaFunction<WasmObject, WasmObject, WasmObject, WasmObject, WasmObject, WasmObject>) (a, b, c, d, e) -> WasmObject.ofExisting(instance, (int) wasmFunction.apply(
                    a.getMemoryPointer(),
                    b.getMemoryPointer(),
                    c.getMemoryPointer(),
                    d.getMemoryPointer(),
                    e.getMemoryPointer())[0]);
            default -> throw new IllegalArgumentException("Unexpected value: " + tupleSize);
        };
    }

    public Object asToListFunction(int tupleSize, Instance instance) {
        var wasmFunction = getExport(wasmFunctionName, instance);
        return switch (tupleSize) {
            case 1 -> (Function<WasmObject, WasmList<WasmObject>>) a -> WasmObject.ofExisting(instance, (int) wasmFunction.apply(a.getMemoryPointer())[0]).asList();
            case 2 -> (BiFunction<WasmObject, WasmObject, WasmList<WasmObject>>) (a, b) -> WasmObject.ofExisting(instance, (int) wasmFunction.apply(
                    a.getMemoryPointer(),
                    b.getMemoryPointer())[0]).asList();
            case 3 -> (TriFunction<WasmObject, WasmObject, WasmObject, WasmList<WasmObject>>) (a, b, c) -> WasmObject.ofExisting(instance, (int) wasmFunction.apply(
                    a.getMemoryPointer(),
                    b.getMemoryPointer(),
                    c.getMemoryPointer())[0]).asList();
            case 4 -> (QuadFunction<WasmObject, WasmObject, WasmObject, WasmObject, WasmList<WasmObject>>) (a, b, c, d) -> WasmObject.ofExisting(instance, (int) wasmFunction.apply(
                    a.getMemoryPointer(),
                    b.getMemoryPointer(),
                    c.getMemoryPointer(),
                    d.getMemoryPointer())[0]).asList();
            case 5 -> (PentaFunction<WasmObject, WasmObject, WasmObject, WasmObject, WasmObject, WasmList<WasmObject>>) (a, b, c, d, e) -> WasmObject.ofExisting(instance, (int) wasmFunction.apply(
                    a.getMemoryPointer(),
                    b.getMemoryPointer(),
                    c.getMemoryPointer(),
                    d.getMemoryPointer(),
                    e.getMemoryPointer())[0]).asList();
            default -> throw new IllegalArgumentException("Unexpected value: " + tupleSize);
        };
    }

    public Object asToIntFunction(int tupleSize, Instance instance) {
        var wasmFunction = getExport(wasmFunctionName, instance);
        return switch (tupleSize) {
            case 1 -> (ToIntFunction<WasmObject>) a -> (int) wasmFunction.apply(a.getMemoryPointer())[0];
            case 2 -> (ToIntBiFunction<WasmObject, WasmObject>) (a, b) -> (int) wasmFunction.apply(
                    a.getMemoryPointer(),
                    b.getMemoryPointer())[0];
            case 3 -> (ToIntTriFunction<WasmObject, WasmObject, WasmObject>) (a, b, c) -> (int) wasmFunction.apply(
                    a.getMemoryPointer(),
                    b.getMemoryPointer(),
                    c.getMemoryPointer())[0];
            case 4 -> (ToIntQuadFunction<WasmObject, WasmObject, WasmObject, WasmObject>) (a, b, c, d) -> (int) wasmFunction.apply(
                    a.getMemoryPointer(),
                    b.getMemoryPointer(),
                    c.getMemoryPointer(),
                    d.getMemoryPointer())[0];
            default -> throw new IllegalArgumentException("Unexpected value: " + tupleSize);
        };
    }

    public Object asToLongFunction(int tupleSize, Instance instance) {
        var wasmFunction = getExport(wasmFunctionName, instance);
        return switch (tupleSize) {
            case 1 -> (ToLongFunction<WasmObject>) a -> wasmFunction.apply(a.getMemoryPointer())[0];
            case 2 -> (ToLongBiFunction<WasmObject, WasmObject>) (a, b) -> wasmFunction.apply(
                    a.getMemoryPointer(),
                    b.getMemoryPointer())[0];
            case 3 -> (ToLongTriFunction<WasmObject, WasmObject, WasmObject>) (a, b, c) -> wasmFunction.apply(
                    a.getMemoryPointer(),
                    b.getMemoryPointer(),
                    c.getMemoryPointer())[0];
            case 4 -> (ToLongQuadFunction<WasmObject, WasmObject, WasmObject, WasmObject>) (a, b, c, d) -> wasmFunction.apply(
                    a.getMemoryPointer(),
                    b.getMemoryPointer(),
                    c.getMemoryPointer(),
                    d.getMemoryPointer())[0];
            default -> throw new IllegalArgumentException("Unexpected value: " + tupleSize);
        };
    }

    private Comparator<Integer> getComparator(Instance instance) {
        var wasmComparator = getExport(comparatorFunctionName, instance);
        return (a, b) -> (int) wasmComparator.apply(a, b)[0];
    }

    private BiPredicate<Integer, Integer> getRelation(Instance instance) {
        var wasmRelation = getExport(relationFunctionName, instance);
        return (a, b) -> wasmRelation.apply(a, b)[0] != 0;
    }

    private ToIntFunction<Integer> getHasher(Instance instance) {
        var wasmHasher = getExport(hashFunctionName, instance);
        return a -> (int) wasmHasher.apply(a)[0];
    }

    public String getWasmFunctionName() {
        return wasmFunctionName;
    }

    public void setRelationFunctionName(@Nullable String relationFunctionName) {
        this.relationFunctionName = relationFunctionName;
    }

    public void setHashFunctionName(@Nullable String hashFunctionName) {
        this.hashFunctionName = hashFunctionName;
    }

    public void setComparatorFunctionName(String comparatorFunctionName) {
        this.comparatorFunctionName = comparatorFunctionName;
    }

    @Override
    public String toString() {
        return wasmFunctionName;
    }
}
