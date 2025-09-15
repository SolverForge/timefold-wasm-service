package ai.timefold.wasm.service.dto;

import java.util.Base64;
import java.util.function.BiFunction;
import java.util.function.BiPredicate;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.function.ToIntBiFunction;
import java.util.function.ToIntFunction;

import ai.timefold.solver.core.api.function.PentaFunction;
import ai.timefold.solver.core.api.function.PentaPredicate;
import ai.timefold.solver.core.api.function.QuadFunction;
import ai.timefold.solver.core.api.function.QuadPredicate;
import ai.timefold.solver.core.api.function.ToIntQuadFunction;
import ai.timefold.solver.core.api.function.ToIntTriFunction;
import ai.timefold.solver.core.api.function.TriFunction;
import ai.timefold.solver.core.api.function.TriPredicate;
import ai.timefold.wasm.service.classgen.WasmObject;

import com.dylibso.chicory.runtime.Instance;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

public class WasmFunction {
    final String wasmFunctionName;

    @JsonCreator
    public WasmFunction(@JsonProperty("functionName") String functionName) {
        this.wasmFunctionName = functionName;
    }

    public Object asPredicate(int tupleSize, Instance instance) {
        var wasmFunction = instance.export(wasmFunctionName);
        return switch (tupleSize) {
            case 1 -> (Predicate<WasmObject>) a -> wasmFunction.apply(a.getMemoryPointer())[0] != 0;
            case 2 -> (BiPredicate<WasmObject, WasmObject>) (a, b) -> wasmFunction.apply(
                    a.getMemoryPointer(),
                    b.getMemoryPointer())[0] != 0;
            case 3 -> (TriPredicate<WasmObject, WasmObject, WasmObject>) (a, b, c) -> wasmFunction.apply(
                    a.getMemoryPointer(),
                    b.getMemoryPointer(),
                    c.getMemoryPointer())[0] != 0;
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
        var wasmFunction = instance.export(wasmFunctionName);
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

    public Object asToIntFunction(int tupleSize, Instance instance) {
        var wasmFunction = instance.export(wasmFunctionName);
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

    @Override
    public String toString() {
        return wasmFunctionName;
    }
}
