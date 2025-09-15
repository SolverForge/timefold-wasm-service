package ai.timefold.wasm.service.classgen;

import java.lang.classfile.ClassBuilder;
import java.lang.classfile.CodeBuilder;
import java.lang.constant.ClassDesc;

import ai.timefold.wasm.service.dto.WasmFunction;
import ai.timefold.wasm.service.dto.constraint.DataStream;

public record DataStreamInfo(
                      ConstraintProviderClassGenerator constraintProviderClassGenerator,
                      ClassBuilder classBuilder,
                      CodeBuilder codeBuilder,
                      DataStream dataStream,
                      ClassDesc generatedClassDesc) {
    public ClassDesc loadFunction(FunctionType functionType, WasmFunction function) {
        return constraintProviderClassGenerator.loadFunction(this, functionType, function);
    }

    public ClassDesc loadFunction(FunctionType functionType, WasmFunction function, int extras) {
        return constraintProviderClassGenerator.loadFunction(this, extras, functionType, function);
    }
}
