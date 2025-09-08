package ai.timefold.wasm.service.dto;

import java.util.Base64;
import java.util.function.Predicate;

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

    public Predicate<WasmObject> asPredicate(Instance instance) {
        var wasmFunction = instance.export(wasmFunctionName);
        return wasmObject -> wasmFunction.apply(wasmObject.getMemoryPointer())[0] != 0;
    }
}
