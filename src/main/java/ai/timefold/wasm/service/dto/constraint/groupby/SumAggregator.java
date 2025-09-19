package ai.timefold.wasm.service.dto.constraint.groupby;

import static ai.timefold.wasm.service.classgen.DomainObjectClassGenerator.getDescriptor;

import java.lang.constant.MethodTypeDesc;
import java.util.function.Function;

import ai.timefold.solver.core.api.score.stream.ConstraintCollectors;
import ai.timefold.wasm.service.classgen.DataStreamInfo;
import ai.timefold.wasm.service.classgen.FunctionType;
import ai.timefold.wasm.service.classgen.WasmObject;
import ai.timefold.wasm.service.dto.WasmFunction;

import com.fasterxml.jackson.annotation.JsonProperty;

public record SumAggregator(@JsonProperty("map") WasmFunction map) implements Aggregator{
    public SumAggregator() {
        this(null);
    }

    @Override
    public String name() {
        return "sum";
    }

    @Override
    public void loadAggregatorInstance(DataStreamInfo dataStreamInfo) {
        var codeBuilder = dataStreamInfo.codeBuilder();
        var constraintCollectorDesc = getDescriptor(dataStreamInfo.dataStream().getConstraintCollectorClass());
        dataStreamInfo.loadFunction(FunctionType.TO_INT, map);

        codeBuilder.invokestatic(getDescriptor(ConstraintCollectors.class), "sum",
                MethodTypeDesc.of(constraintCollectorDesc,
                        getDescriptor(dataStreamInfo.dataStream().getToIntFunctionClass())));
        codeBuilder.getstatic(getDescriptor(WasmObject.class), "WRAPPING_INT", getDescriptor(Function.class));
        codeBuilder.invokestatic(getDescriptor(ConstraintCollectors.class), "collectAndThen",
                MethodTypeDesc.of(constraintCollectorDesc,
                        constraintCollectorDesc, getDescriptor(Function.class)));
    }
}
