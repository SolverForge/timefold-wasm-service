package ai.timefold.wasm.service.dto.constraint.groupby;

import static ai.timefold.wasm.service.classgen.DomainObjectClassGenerator.getDescriptor;

import java.lang.constant.MethodTypeDesc;

import ai.timefold.solver.core.api.score.stream.ConstraintCollectors;
import ai.timefold.wasm.service.classgen.DataStreamInfo;
import ai.timefold.wasm.service.classgen.FunctionType;
import ai.timefold.wasm.service.dto.WasmFunction;

import org.jspecify.annotations.NullMarked;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

@NullMarked
public record CollectAndThenAggregator(@JsonProperty("collector") Aggregator aggregator,
                                       @JsonProperty("mapper") WasmFunction mapper) implements Aggregator {
    @JsonCreator
    public CollectAndThenAggregator {

    }

    public CollectAndThenAggregator() {
        this(null, null);
    }

    @Override
    public String name() {
        return "collectAndThen";
    }

    @Override
    public void loadAggregatorInstance(DataStreamInfo dataStreamInfo) {
        var codeBuilder = dataStreamInfo.codeBuilder();
        aggregator.loadAggregatorInstance(dataStreamInfo);
        dataStreamInfo.loadFunction(FunctionType.MAPPER, mapper);
        codeBuilder.invokestatic(getDescriptor(ConstraintCollectors.class),
                "collectAndThen", MethodTypeDesc.of(getDescriptor(dataStreamInfo.dataStream().getConstraintCollectorClass()),
                        getDescriptor(dataStreamInfo.dataStream().getConstraintCollectorClass()),
                        getDescriptor(dataStreamInfo.dataStream().getFunctionClassOfSize(1))));
    }
}
