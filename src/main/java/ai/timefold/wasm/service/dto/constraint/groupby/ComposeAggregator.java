package ai.timefold.wasm.service.dto.constraint.groupby;

import static ai.timefold.wasm.service.classgen.DomainObjectClassGenerator.getDescriptor;

import java.lang.constant.MethodTypeDesc;
import java.util.List;

import ai.timefold.solver.core.api.score.stream.ConstraintCollectors;
import ai.timefold.wasm.service.classgen.DataStreamInfo;
import ai.timefold.wasm.service.classgen.FunctionType;
import ai.timefold.wasm.service.dto.WasmFunction;

import org.jspecify.annotations.NullMarked;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

@NullMarked
public record ComposeAggregator(@JsonProperty("collectors") List<Aggregator> aggregators,
                                @JsonProperty("combiner") WasmFunction combiner) implements Aggregator {
    @JsonCreator
    public ComposeAggregator {

    }

    public ComposeAggregator() {
        this(null, null);
    }

    @Override
    public String name() {
        return "compose";
    }

    @Override
    public void loadAggregatorInstance(DataStreamInfo dataStreamInfo) {
        var codeBuilder = dataStreamInfo.codeBuilder();
        var constraintCollectorDesc = getDescriptor(dataStreamInfo.dataStream().getConstraintCollectorClass());
        var methodDescriptor = switch (aggregators.size()) {
            case 2 -> MethodTypeDesc.of(constraintCollectorDesc,
                    constraintCollectorDesc, constraintCollectorDesc,
                    getDescriptor(dataStreamInfo.dataStream().getFunctionClassOfSize(2)));
            case 3 -> MethodTypeDesc.of(constraintCollectorDesc,
                    constraintCollectorDesc, constraintCollectorDesc, constraintCollectorDesc,
                    getDescriptor(dataStreamInfo.dataStream().getFunctionClassOfSize(3)));
            case 4 -> MethodTypeDesc.of(constraintCollectorDesc,
                    constraintCollectorDesc, constraintCollectorDesc, constraintCollectorDesc, constraintCollectorDesc,
                    getDescriptor(dataStreamInfo.dataStream().getFunctionClassOfSize(4)));
            default -> throw new IllegalArgumentException("Invalid collector count; expected 2 to 4 but got %d.".formatted(aggregators.size()));
        };
        for (Aggregator aggregator : aggregators) {
            aggregator.loadAggregatorInstance(dataStreamInfo);
        }
        dataStreamInfo.loadFunctionOfSize(FunctionType.MAPPER, combiner, aggregators.size());
        codeBuilder.invokestatic(getDescriptor(ConstraintCollectors.class),
                "compose", methodDescriptor);
    }
}
