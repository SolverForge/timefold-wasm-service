package ai.timefold.wasm.service.classgen;

import java.util.Comparator;
import java.util.Objects;
import java.util.function.BiPredicate;
import java.util.function.Function;
import java.util.function.IntFunction;
import java.util.function.ToIntFunction;

import ai.timefold.wasm.service.SolverResource;

import com.dylibso.chicory.runtime.Instance;

public class WasmObject implements Comparable<WasmObject> {
    public final Instance wasmInstance;
    public final int memoryPointer;
    private final Comparator<Integer> comparator;
    private final ToIntFunction<Integer> hasher;
    private final BiPredicate<Integer, Integer> equalRelation;

    public static final Function<Integer, WasmObject> WRAPPING_INT = WasmObject::wrappingInt;
    public static final Function<Double, WasmObject> WRAPPING_DOUBLE = WasmObject::wrappingDouble;
    public static final Function<WasmObject, WasmList<WasmObject>> TO_LIST = WasmObject::asList;
    public static final Function<Object, WasmObject> CONSTANT_NULL = _ -> new WasmObject(SolverResource.INSTANCE.get(), 0);

    private static final BiPredicate<Integer, Integer> DEFAULT_EQUALS = Integer::equals;
    private static final ToIntFunction<Integer> DEFAULT_HASH = Object::hashCode;
    private static final Comparator<Integer> DEFAULT_COMPARATOR = Comparator.comparingInt(memoryAddress -> memoryAddress);

    public WasmObject() {
        // Required for cloning
        memoryPointer = 0;
        wasmInstance = null;
        equalRelation = DEFAULT_EQUALS;
        hasher = DEFAULT_HASH;
        comparator = DEFAULT_COMPARATOR;
    }

    public WasmObject(Allocator allocator, Instance wasmInstance, int size) {
        this.wasmInstance = wasmInstance;
        memoryPointer = allocator.allocate(size);
        equalRelation = DEFAULT_EQUALS;
        hasher = DEFAULT_HASH;
        comparator = DEFAULT_COMPARATOR;
    }

    public WasmObject(Instance wasmInstance, int memoryPointer) {
        this.wasmInstance = wasmInstance;
        this.memoryPointer = memoryPointer;
        equalRelation = DEFAULT_EQUALS;
        hasher = DEFAULT_HASH;
        comparator = DEFAULT_COMPARATOR;
    }

    public WasmObject(Instance wasmInstance, int memoryPointer,
            BiPredicate<Integer, Integer> equalRelation,
            ToIntFunction<Integer> hasher,
            Comparator<Integer> comparator) {
        this.wasmInstance = wasmInstance;
        this.memoryPointer = memoryPointer;
        this.equalRelation = Objects.requireNonNullElse(equalRelation, DEFAULT_EQUALS);
        this.hasher = Objects.requireNonNullElse(hasher, DEFAULT_HASH);
        this.comparator = Objects.requireNonNullElse(comparator, DEFAULT_COMPARATOR);
    }

    public WasmObject(Instance wasmInstance, int memoryPointer,
            Comparator<Integer> comparator) {
        this.wasmInstance = wasmInstance;
        this.memoryPointer = memoryPointer;
        this.equalRelation = (a, b) -> a.compareTo(b) == 0;
        this.hasher = _ -> 0;
        this.comparator = comparator;
    }

    public int getMemoryPointer() {
        return memoryPointer;
    }

    protected int readIntField(int fieldOffset) {
        return wasmInstance.memory().readInt(memoryPointer + fieldOffset);
    }

    protected long readLongField(int fieldOffset) {
        return wasmInstance.memory().readLong(memoryPointer + fieldOffset);
    }

    protected float readFloatField(int fieldOffset) {
        return wasmInstance.memory().readFloat(memoryPointer + fieldOffset);
    }

    protected double readDoubleField(int fieldOffset) {
        return wasmInstance.memory().readDouble(memoryPointer + fieldOffset);
    }

    protected WasmObject readReferenceField(int fieldOffset) {
        var pointer = wasmInstance.memory().readI32(memoryPointer + fieldOffset);
        if (pointer == 0) {
            return null;
        }
        return ofExisting(wasmInstance, (int) pointer);
    }

    public WasmList<WasmObject> asList() {
        return WasmList.ofExisting(memoryPointer, WasmObject.class);
    }

    public static WasmObject wrappingInt(int value) {
        return new WasmObject(null, value);
    }

    public static WasmObject wrappingDouble(double value) {
        return new WasmObject(null, Float.floatToIntBits((float) value));
    }

    public static WasmObject ofExisting(Instance wasmInstance,
            int memoryPointer) {
        return new WasmObject(wasmInstance, memoryPointer);
    }

    public static WasmObject ofExisting(Instance wasmInstance,
            int memoryPointer, Comparator<Integer> comparator) {
        return new WasmObject(wasmInstance, memoryPointer, comparator);
    }

    public static WasmObject ofExisting(Instance wasmInstance,
            int memoryPointer, BiPredicate<Integer, Integer> equalRelation,
            ToIntFunction<Integer> hasher) {
        return new WasmObject(wasmInstance, memoryPointer, equalRelation,
                hasher, null);
    }

    public static WasmObject ofExistingOrDefault(Instance wasmInstance,
            int memoryPointer, WasmObject defaultValue) {
        return defaultValue;
    }

    @SuppressWarnings("unchecked")
    public static <Item_ extends WasmObject> Item_ ofExistingOrCreate(Instance wasmInstance,
            int memoryPointer, IntFunction<Item_> factory) {
        return factory.apply(memoryPointer);
    }

    @Override
    public int compareTo(WasmObject o) {
        return comparator.compare(memoryPointer, o.memoryPointer);
    }

    @Override
    public boolean equals(Object object) {
        if (!(object instanceof WasmObject that)) {
            return false;
        }
        return equalRelation.test(memoryPointer, that.memoryPointer);
    }

    @Override
    public int hashCode() {
        return hasher.applyAsInt(memoryPointer);
    }

    @Override
    public String toString() {
        return "%s(pointer=%x)".formatted(getClass().getSimpleName(), memoryPointer);
    }
}
