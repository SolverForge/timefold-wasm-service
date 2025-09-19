package ai.timefold.wasm.service.dto.constraint.groupby;

import static ai.timefold.wasm.service.classgen.DomainObjectClassGenerator.getDescriptor;

import java.lang.constant.ClassDesc;
import java.lang.constant.MethodTypeDesc;
import java.util.List;
import java.util.function.Function;

import ai.timefold.solver.core.api.score.stream.ConstraintCollectors;
import ai.timefold.wasm.service.classgen.DataStreamInfo;
import ai.timefold.wasm.service.classgen.FunctionType;
import ai.timefold.wasm.service.dto.WasmFunction;

import com.fasterxml.jackson.annotation.JsonProperty;

public record ConsecutiveAggregator(@JsonProperty(value = "map", required = true) WasmFunction map,
                                    @JsonProperty(value = "fields", required = true) List<ConsecutiveSequenceField> fields) implements Aggregator{
    public ConsecutiveAggregator {
        if (fields == null || fields.isEmpty()) {
            throw new IllegalArgumentException("fields cannot be null or empty");
        }
    }

    public ConsecutiveAggregator() {
        this(null, List.of(ConsecutiveSequenceField.COUNT));
    }

    @Override
    public String name() {
        return "toConsecutiveSequences";
    }

    @Override
    public void loadAggregatorInstance(DataStreamInfo dataStreamInfo) {
        var codeBuilder = dataStreamInfo.codeBuilder();
        var constraintCollectorDesc = getDescriptor(dataStreamInfo.dataStream().getConstraintCollectorClass());
        dataStreamInfo.loadFunction(FunctionType.TO_INT, map);

        codeBuilder.invokestatic(getDescriptor(ConstraintCollectors.class), "toConsecutiveSequences",
                MethodTypeDesc.of(constraintCollectorDesc,
                        getDescriptor(dataStreamInfo.dataStream().getToIntFunctionClass())));
        codeBuilder.new_(getDescriptor(ConsecutiveFieldExtractor.class));
        codeBuilder.dup();
        codeBuilder.loadConstant(fields.size());
        codeBuilder.anewarray(getDescriptor(ConsecutiveSequenceField.class));
        for (var i = 0; i < fields.size(); i++) {
            codeBuilder.dup();
            codeBuilder.loadConstant(i);
            codeBuilder.loadConstant(fields.get(i).name());
            codeBuilder.invokestatic(getDescriptor(ConsecutiveSequenceField.class), "valueOf",
                    MethodTypeDesc.of(getDescriptor(ConsecutiveSequenceField.class), getDescriptor(String.class)));
            codeBuilder.aastore();
        }
        codeBuilder.invokespecial(getDescriptor(ConsecutiveFieldExtractor.class), "<init>", MethodTypeDesc.of(
                ClassDesc.ofDescriptor("V"),  getDescriptor(ConsecutiveSequenceField.class).arrayType()
        ));
        codeBuilder.invokestatic(getDescriptor(ConstraintCollectors.class), "collectAndThen",
                MethodTypeDesc.of(constraintCollectorDesc,
                        constraintCollectorDesc, getDescriptor(Function.class)));
    }

}
