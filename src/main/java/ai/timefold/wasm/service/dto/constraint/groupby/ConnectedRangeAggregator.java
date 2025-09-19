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

import org.jspecify.annotations.Nullable;

import com.fasterxml.jackson.annotation.JsonProperty;

public record ConnectedRangeAggregator(@Nullable @JsonProperty(value = "rangeMap", required = false) WasmFunction rangeMap,
                                       @JsonProperty(value = "rangeStartMap", required = true) WasmFunction rangeStartMap,
                                       @JsonProperty(value = "rangeEndMap", required = true) WasmFunction rangeEndMap,
                                       @JsonProperty(value = "fields", required = true) List<ConnectedRangeField> fields) implements Aggregator {
    public ConnectedRangeAggregator {
        if (fields == null || fields.isEmpty()) {
            throw new IllegalArgumentException("fields cannot be null or empty");
        }
    }

    public ConnectedRangeAggregator() {
        this(null, null, null, List.of(ConnectedRangeField.COUNT));
    }

    @Override
    public String name() {
        return "toConnectedRanges";
    }

    @Override
    public void loadAggregatorInstance(DataStreamInfo dataStreamInfo) {
        var codeBuilder = dataStreamInfo.codeBuilder();
        var constraintCollectorDesc = getDescriptor(dataStreamInfo.dataStream().getConstraintCollectorClass());

        var methodTypeDesc = MethodTypeDesc.of(constraintCollectorDesc,
                getDescriptor(dataStreamInfo.dataStream().getToLongFunctionClassOfSize(1)),
                getDescriptor(dataStreamInfo.dataStream().getToLongFunctionClassOfSize(1)));

        if (dataStreamInfo.dataStream().getTupleSize() != 1) {
            dataStreamInfo.loadFunction(FunctionType.MAPPER, rangeMap);
            methodTypeDesc = MethodTypeDesc.of(constraintCollectorDesc,
                    getDescriptor(dataStreamInfo.dataStream().getFunctionClass()),
                    getDescriptor(dataStreamInfo.dataStream().getToLongFunctionClassOfSize(1)),
                    getDescriptor(dataStreamInfo.dataStream().getToLongFunctionClassOfSize(1)));
        }
        dataStreamInfo.loadFunction(FunctionType.TO_LONG, rangeStartMap);
        dataStreamInfo.loadFunction(FunctionType.TO_LONG, rangeEndMap);

        codeBuilder.invokestatic(getDescriptor(ConstraintCollectors.class), "toConnectedRanges", methodTypeDesc);
        codeBuilder.new_(getDescriptor(ConnectedRangeFieldExtractor.class));
        codeBuilder.dup();
        codeBuilder.loadConstant(fields.size());
        codeBuilder.anewarray(getDescriptor(ConnectedRangeField.class));
        for (var i = 0; i < fields.size(); i++) {
            codeBuilder.dup();
            codeBuilder.loadConstant(i);
            codeBuilder.loadConstant(fields.get(i).name());
            codeBuilder.invokestatic(getDescriptor(ConnectedRangeField.class), "valueOf",
                    MethodTypeDesc.of(getDescriptor(ConnectedRangeField.class), getDescriptor(String.class)));
            codeBuilder.aastore();
        }
        codeBuilder.invokespecial(getDescriptor(ConnectedRangeFieldExtractor.class), "<init>", MethodTypeDesc.of(
                ClassDesc.ofDescriptor("V"),  getDescriptor(ConnectedRangeField.class).arrayType()
        ));
        codeBuilder.invokestatic(getDescriptor(ConstraintCollectors.class), "collectAndThen",
                MethodTypeDesc.of(constraintCollectorDesc,
                        constraintCollectorDesc, getDescriptor(Function.class)));
    }

}
