package ai.timefold.wasm.service.dto.constraint.groupby;

import static ai.timefold.wasm.service.classgen.DomainObjectClassGenerator.getDescriptor;

import java.lang.constant.ClassDesc;
import java.lang.constant.MethodTypeDesc;
import java.util.Objects;
import java.util.function.Function;
import java.util.function.Predicate;

import ai.timefold.solver.core.api.score.stream.ConstraintCollectors;
import ai.timefold.wasm.service.classgen.DataStreamInfo;
import ai.timefold.wasm.service.classgen.FunctionType;
import ai.timefold.wasm.service.classgen.WasmObject;
import ai.timefold.wasm.service.dto.WasmFunction;

import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

@NullMarked
public record ConditionalAggregator(@JsonProperty("predicate") WasmFunction predicate,
                                    @JsonProperty("collector") Aggregator aggregator) implements Aggregator {
    @JsonCreator
    public ConditionalAggregator {

    }

    public ConditionalAggregator() {
        this(null, null);
    }

    @Override
    public String name() {
        return "conditionally";
    }

    @Override
    public void loadAggregatorInstance(DataStreamInfo dataStreamInfo) {
        var codeBuilder = dataStreamInfo.codeBuilder();
        dataStreamInfo.loadFunction(FunctionType.PREDICATE, predicate);
        aggregator.loadAggregatorInstance(dataStreamInfo);
        codeBuilder.invokestatic(getDescriptor(ConstraintCollectors.class),
                "conditionally", MethodTypeDesc.of(getDescriptor(dataStreamInfo.dataStream().getConstraintCollectorClass()),
                        getDescriptor(dataStreamInfo.dataStream().getPredicateClass()),
                        getDescriptor(dataStreamInfo.dataStream().getConstraintCollectorClass())));
    }
}
