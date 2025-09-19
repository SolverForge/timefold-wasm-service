package ai.timefold.wasm.service.dto.constraint.groupby;

import java.lang.ref.Cleaner;
import java.util.function.Function;

import ai.timefold.solver.core.api.score.stream.common.ConnectedRangeChain;
import ai.timefold.wasm.service.SolverResource;
import ai.timefold.wasm.service.classgen.WasmList;
import ai.timefold.wasm.service.classgen.WasmObject;

public record ConnectedRangeFieldExtractor(ConnectedRangeField[] fields)
        implements Function<ConnectedRangeChain<?, Integer, Integer>, WasmObject> {
    private static final Cleaner CLEANER = Cleaner.create();

    @Override
    public WasmObject apply(ConnectedRangeChain<?, Integer, Integer> sequenceChain) {
        var listAccessor = SolverResource.LIST_ACCESSOR.get();
        var out = WasmList.createNew(WasmObject.class);
        for (var sequence : sequenceChain.getConnectedRanges()) {
            var fieldList = WasmList.createNew(WasmObject.class);
            for (var field : fields) {
                fieldList.add(WasmObject.wrappingInt(field.extractField(sequence)));
            }
            var fieldListBackingObject = fieldList.getWasmObject();
            var fieldListBackingObjectAddress = fieldListBackingObject.memoryPointer;

            out.add(fieldListBackingObject);
            CLEANER.register(fieldListBackingObject, () -> {
                listAccessor.deallocate(fieldListBackingObjectAddress);
            });
        }
        var outBackingObject = out.getWasmObject();
        var outBackingObjectAddress = outBackingObject.memoryPointer;
        CLEANER.register(outBackingObject, () -> {
            listAccessor.deallocate(outBackingObjectAddress);
        });
        return outBackingObject;
    }
}
