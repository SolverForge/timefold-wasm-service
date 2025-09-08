package ai.timefold.wasm.service.classgen;

import java.lang.ref.Cleaner;
import java.lang.reflect.Array;
import java.util.ArrayList;
import java.util.List;

import org.apache.commons.collections4.map.ConcurrentReferenceHashMap;

import com.dylibso.chicory.runtime.Instance;
import com.dylibso.chicory.runtime.Memory;

public class WasmObject {
    private final static Cleaner cleaner = Cleaner.create();
    @SuppressWarnings({"rawtypes", "unchecked"})
    private final static ConcurrentReferenceHashMap<Instance, ConcurrentReferenceHashMap<Integer, WasmObject>> referenceMap = (ConcurrentReferenceHashMap) ConcurrentReferenceHashMap.builder()
            .weakKeys().strongValues().get();

    public final Instance wasmInstance;
    public final int memoryPointer;
    private final int size;

    private List<WasmObject> ownedObjects;

    private static int nextPtr = Integer.SIZE; // 0 is null

    public WasmObject() {
        // Required for cloning
        memoryPointer = 0;
        size = 0;
        wasmInstance = null;
        ownedObjects = null;
    }

    public WasmObject(Instance wasmInstance, int size) {
        this.wasmInstance = wasmInstance;
        this.size = size;
        @SuppressWarnings({"rawtypes", "unchecked"})
        var map = referenceMap.computeIfAbsent(wasmInstance,
                ignored -> (ConcurrentReferenceHashMap) ConcurrentReferenceHashMap.builder().strongKeys().weakValues().get());

        var newPointer = nextPtr;
        nextPtr += size;
        memoryPointer = newPointer;
        cleaner.register(this, () -> {
            // TODO: free memory
            //wasmInstance.memory().apply(newPointer);
        });
        map.put(memoryPointer, this);
    }

    public WasmObject(Instance wasmInstance, int memoryPointer, int size) {
        this.wasmInstance = wasmInstance;
        this.memoryPointer = memoryPointer;
        this.size = size;
    }

    public int getMemoryPointer() {
        return memoryPointer;
    }

    protected int allocateString(String value) {
        if (ownedObjects == null) {
            ownedObjects = new ArrayList<>();
        }
        var length = value.getBytes().length;
        var wasmString = new WasmObject(wasmInstance, length);
        wasmInstance.memory().writeString(wasmString.memoryPointer, value);
        ownedObjects.add(wasmString);
        return wasmString.memoryPointer;
    }

    protected WasmObject allocateArray(int arrayLength, int elementSize) {
        if (ownedObjects == null) {
            ownedObjects = new ArrayList<>();
        }
        var wasmArray = new WasmObject(wasmInstance, elementSize * (arrayLength + 1));
        ownedObjects.add(wasmArray);
        wasmArray.writeIntField(0, arrayLength);
        return wasmArray;
    }

    public Object toJavaArray(int elementSize, Class<?> elementClass) {
        var arrayLength = readIntField(0);
        var out = Array.newInstance(elementClass, arrayLength);
        for (int i = 0; i < arrayLength; i++) {
            Array.set(out, i, readReferenceField(elementSize * (i+1)));
        }
        return out;
    }

    protected Memory getMemory() {
        return wasmInstance.memory();
    }

    protected void writeIntField(int fieldOffset, int value) {
        wasmInstance.memory().writeI32(memoryPointer + fieldOffset, value);
    }

    protected void writeLongField(int fieldOffset, long value) {
        wasmInstance.memory().writeLong(memoryPointer + fieldOffset, value);
    }

    protected void writeFloatField(int fieldOffset, float value) {
        wasmInstance.memory().writeF32(memoryPointer + fieldOffset, value);
    }

    protected void writeDoubleField(int fieldOffset, double value) {
        wasmInstance.memory().writeF64(memoryPointer + fieldOffset, value);
    }

    protected void writeReferenceField(int fieldOffset, int value) {
        wasmInstance.memory().writeI32(memoryPointer + fieldOffset, value);
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

    public static WasmObject ofExisting(Instance wasmInstance,
            int memoryPointer) {
        return referenceMap.get(wasmInstance).computeIfAbsent(memoryPointer, ignored -> new WasmObject(wasmInstance, memoryPointer, 0));
    }
}
