package ai.timefold.wasm.service.classgen;

import static ai.timefold.wasm.service.classgen.DomainObjectClassGenerator.getDescriptor;

import java.lang.constant.ClassDesc;

import ai.timefold.solver.core.api.function.TriFunction;
import ai.timefold.wasm.service.SolverResource;
import ai.timefold.wasm.service.dto.WasmFunction;
import ai.timefold.wasm.service.dto.constraint.DataStream;

import com.dylibso.chicory.runtime.Instance;

public enum FunctionType {
    PREDICATE(WasmFunction::asPredicate),
    MAPPER(WasmFunction::asFunction),
    LIST_MAPPER(WasmFunction::asToListFunction),
    TO_INT(WasmFunction::asToIntFunction);

    private final TriFunction<WasmFunction, Integer, Instance, Object> functionConvertor;

    FunctionType(TriFunction<WasmFunction, Integer, Instance, Object> functionConvertor) {
        this.functionConvertor = functionConvertor;
    }

    public ClassDesc getClassDescriptor(DataStream dataStream, int argCount) {
        return getDescriptor(switch (this) {
            case PREDICATE -> dataStream.getPredicateClassOfSize(argCount);
            case MAPPER, LIST_MAPPER -> dataStream.getFunctionClassOfSize(argCount);
            case TO_INT -> dataStream.getToIntFunctionClassOfSize(argCount);
        });
    }

    public Object getFunction(int size, WasmFunction wasmFunction) {
        return functionConvertor.apply(wasmFunction, size, SolverResource.INSTANCE.get());
    }
}
