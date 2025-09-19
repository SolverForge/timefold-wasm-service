package ai.timefold.wasm.service.classgen;

import java.util.function.IntBinaryOperator;
import java.util.function.IntConsumer;
import java.util.function.IntFunction;
import java.util.function.IntSupplier;
import java.util.function.IntUnaryOperator;

import ai.timefold.wasm.service.dto.DomainListAccessor;

import com.dylibso.chicory.runtime.Instance;

public final class WasmListAccessor {
    @FunctionalInterface
    private interface IntBiConsumer {
        void accept(int a, int b);
    }

    @FunctionalInterface
    private interface IntTriConsumer {
        void accept(int a, int b, int c);
    }

    private final Instance wasmInstance;

    private final IntSupplier createListFunction;
    private final IntBinaryOperator getListItemFunction;
    private final IntTriConsumer setListItemFunction;
    private final IntUnaryOperator getListSizeFunction;
    private final IntBiConsumer appendListFunction;
    private final IntTriConsumer insertListFunction;
    private final IntBiConsumer removeListFunction;
    private final IntConsumer deallocListFunction;

    public WasmListAccessor(Instance instance, DomainListAccessor domainListAccessor) {
        this.wasmInstance = instance;

        var domainCreateList = instance.export(domainListAccessor.createFunction());
        var domainGetListItem = instance.export(domainListAccessor.getItemFunction());
        var domainSetListItem = instance.export(domainListAccessor.setItemFunction());
        var domainGetListSize = instance.export(domainListAccessor.getSizeFunction());
        var domainAppendListItem = instance.export(domainListAccessor.appendFunction());
        var domainInsertListItem = instance.export(domainListAccessor.insertFunction());
        var domainRemoveListItem = instance.export(domainListAccessor.removeFunction());
        var domainDeallocListFunction = instance.export(domainListAccessor.deallocator());

        createListFunction = () -> (int) domainCreateList.apply()[0];
        getListItemFunction = (list, index) -> (int) domainGetListItem.apply(list, index)[0];
        setListItemFunction = domainSetListItem::apply;
        getListSizeFunction = list -> (int) domainGetListSize.apply(list)[0];
        appendListFunction = domainAppendListItem::apply;
        insertListFunction = domainInsertListItem::apply;
        removeListFunction = domainRemoveListItem::apply;
        deallocListFunction = domainDeallocListFunction::apply;
    }

    public WasmObject newInstance() {
        return WasmObject.ofExisting(wasmInstance, createListFunction.getAsInt());
    }

    public <Item_ extends WasmObject> Item_ getItem(WasmObject list, int index, IntFunction<Item_> memoryPointerToItem) {
        return WasmObject.ofExistingOrCreate(wasmInstance,
                getListItemFunction.applyAsInt(list.memoryPointer, index),
                memoryPointerToItem);
    }

    public void setItem(WasmObject list, int index, WasmObject item) {
        setListItemFunction.accept(list.memoryPointer, index, item.memoryPointer);
    }

    public int getLength(WasmObject list) {
        return getListSizeFunction.applyAsInt(list.memoryPointer);
    }

    public void append(WasmObject list, WasmObject item) {
        appendListFunction.accept(list.memoryPointer, item.memoryPointer);
    }

    public void insert(WasmObject list, int index, WasmObject item) {
        insertListFunction.accept(list.memoryPointer, index, item.memoryPointer);
    }

    public void remove(WasmObject list, int index) {
        removeListFunction.accept(list.memoryPointer, index);
    }

    public void deallocate(int memoryPointer) {
        deallocListFunction.accept(memoryPointer);
    }

    public Instance getWasmInstance() {
        return wasmInstance;
    }
}
