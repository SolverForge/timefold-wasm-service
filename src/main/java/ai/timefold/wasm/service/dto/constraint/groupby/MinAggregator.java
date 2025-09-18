package ai.timefold.wasm.service.dto.constraint.groupby;

import static ai.timefold.wasm.service.classgen.DomainObjectClassGenerator.getDescriptor;

import java.lang.constant.MethodTypeDesc;

import ai.timefold.solver.core.api.score.stream.ConstraintCollectors;
import ai.timefold.wasm.service.classgen.DataStreamInfo;
import ai.timefold.wasm.service.classgen.FunctionType;
import ai.timefold.wasm.service.dto.WasmFunction;

import com.fasterxml.jackson.annotation.JsonProperty;

public record MinAggregator(
        @JsonProperty("map") WasmFunction map,
        @JsonProperty("comparator") String comparator) implements Aggregator{

    public MinAggregator {
        map.setComparatorFunctionName(comparator);
    }

    @Override
    public String name() {
        return "min";
    }

    @Override
    public void loadAggregatorInstance(DataStreamInfo dataStreamInfo) {
        var codeBuilder = dataStreamInfo.codeBuilder();
        var constraintCollectorDesc = getDescriptor(dataStreamInfo.dataStream().getConstraintCollectorClass());
        dataStreamInfo.loadFunction(FunctionType.MAPPER, map);

        codeBuilder.invokestatic(getDescriptor(ConstraintCollectors.class), "min",
                MethodTypeDesc.of(constraintCollectorDesc,
                        getDescriptor(dataStreamInfo.dataStream().getFunctionClass())));
    }
}
