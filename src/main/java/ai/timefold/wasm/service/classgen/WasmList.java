package ai.timefold.wasm.service.classgen;

import java.lang.reflect.InvocationTargetException;
import java.util.AbstractList;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.IntFunction;

import ai.timefold.wasm.service.SolverResource;

import org.apache.commons.collections4.map.ConcurrentReferenceHashMap;

import com.dylibso.chicory.runtime.Instance;

public final class WasmList<Item_ extends WasmObject> extends AbstractList<Item_> {
    @SuppressWarnings({"unchecked", "rawtypes"})
    private static final ConcurrentReferenceHashMap<Instance, Map<Integer, WasmList<?>>> wasmInstanceToListCache = (ConcurrentReferenceHashMap) new ConcurrentReferenceHashMap.Builder<>()
            .weakKeys().strongValues().get();

    private final WasmListAccessor listAccessor;
    private final WasmObject wasmList;
    private final IntFunction<Item_> itemFromPointer;

    private int cachedSize;
    private List<Item_> cachedItemList;

    public WasmList(WasmListAccessor listAccessor, WasmObject wasmList,
            Class<Item_> itemClass) {
        this.listAccessor = listAccessor;
        this.wasmList = wasmList;
        cachedSize = listAccessor.getLength(wasmList);
        cachedItemList = new ArrayList<>(cachedSize);

        var wasmInstance = listAccessor.getWasmInstance();
        try {
            var itemClassConstructor = itemClass.getConstructor(Instance.class, int.class);
            itemFromPointer = pointer -> {
                try {
                    return itemClassConstructor.newInstance(wasmInstance, pointer);
                } catch (InstantiationException | IllegalAccessException | InvocationTargetException e) {
                    throw new RuntimeException(e);
                }
            };
        } catch (NoSuchMethodException e) {
            throw new RuntimeException(e);
        }

        for (int i = 0; i < cachedSize; i++) {
            cachedItemList.add(listAccessor.getItem(wasmList, i, itemFromPointer));
        }
    }

    private WasmList(int wasmListPointer, Class<Item_> itemClass) {
        this(SolverResource.LIST_ACCESSOR.get(), WasmObject.ofExisting(
                SolverResource.LIST_ACCESSOR.get().getWasmInstance(), wasmListPointer
        ), itemClass);
    }

    @SuppressWarnings("unchecked")
    public static <Item_ extends WasmObject> WasmList<Item_> ofExisting(int memoryPointer, Class<Item_> itemClass) {
        if (memoryPointer == 0) {
            return null;
        }
        return (WasmList<Item_>) wasmInstanceToListCache.computeIfAbsent(SolverResource.LIST_ACCESSOR.get().getWasmInstance(), _ ->
                (ConcurrentReferenceHashMap) ConcurrentReferenceHashMap.builder()
                        .weakValues().get())
                .computeIfAbsent(memoryPointer, ignored -> new WasmList<>(memoryPointer, itemClass));
    }

    @SuppressWarnings("unchecked")
    public static <Item_ extends WasmObject> WasmList<Item_> createNew(Class<Item_> itemClass) {
        var listAccessor = SolverResource.LIST_ACCESSOR.get();

        var backingObject = listAccessor.newInstance();
        return new WasmList<>(listAccessor, backingObject, itemClass);
    }

    @Override
    public Item_ get(int index) {
        return cachedItemList.get(index);
    }

    @Override
    @SuppressWarnings("unchecked")
    public Item_ set(int index, Item_ element) {
        var old = get(index);
        listAccessor.setItem(wasmList, index, element);
        cachedItemList.set(index, element);
        return old;
    }

    @Override
    public int size() {
        return cachedSize;
    }

    @Override
    public void add(int index, Item_ element) {
        if (index == cachedSize) {
            listAccessor.append(wasmList, element);
            cachedItemList.add(element);
        } else {
            listAccessor.insert(wasmList, index, element);
            cachedItemList.add(index, element);
        }
        cachedSize++;
    }

    @Override
    public Item_ remove(int index) {
        var old = get(index);
        listAccessor.remove(wasmList, index);
        cachedItemList.remove(index);
        cachedSize--;
        return old;
    }

    public int getMemoryAddress() {
        return wasmList.memoryPointer;
    }

    public WasmObject getWasmObject() {
        return wasmList;
    }
}
