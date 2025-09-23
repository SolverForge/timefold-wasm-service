package ai.timefold.wasm.service.dto.constraint.groupby;

import static ai.timefold.wasm.service.classgen.DomainObjectClassGenerator.getDescriptor;

import java.lang.constant.MethodTypeDesc;
import java.util.function.Function;

import ai.timefold.solver.core.api.score.stream.ConstraintCollectors;
import ai.timefold.solver.core.api.score.stream.common.LoadBalance;
import ai.timefold.wasm.service.classgen.DataStreamInfo;
import ai.timefold.wasm.service.classgen.FunctionType;
import ai.timefold.wasm.service.classgen.WasmObject;
import ai.timefold.wasm.service.dto.WasmFunction;

import org.jspecify.annotations.Nullable;

import com.fasterxml.jackson.annotation.JsonProperty;

public record LoadBalanceAggregator(@JsonProperty("map") WasmFunction map,
                                    @Nullable @JsonProperty("load") WasmFunction load) implements Aggregator {
    public static Function<LoadBalance<?>, WasmObject> LOAD_BALANCE_TO_FLOAT = loadBalance -> WasmObject.wrappingDouble(loadBalance.unfairness().doubleValue());

    public LoadBalanceAggregator() {
        this(null, null);
    }

    @Override
    public String name() {
        return "loadBalance";
    }

    @Override
    public void loadAggregatorInstance(DataStreamInfo dataStreamInfo) {
        var codeBuilder = dataStreamInfo.codeBuilder();
        var constraintCollectorDesc = getDescriptor(dataStreamInfo.dataStream().getConstraintCollectorClass());
        dataStreamInfo.loadFunction(FunctionType.MAPPER, map);

        if (load != null) {
            dataStreamInfo.loadFunction(FunctionType.TO_LONG, load);
            codeBuilder.invokestatic(getDescriptor(ConstraintCollectors.class), "loadBalance",
                    MethodTypeDesc.of(constraintCollectorDesc,
                            getDescriptor(dataStreamInfo.dataStream().getFunctionClass()),
                            getDescriptor(dataStreamInfo.dataStream().getToLongFunctionClass())));
            codeBuilder.getstatic(getDescriptor(LoadBalanceAggregator.class), "LOAD_BALANCE_TO_FLOAT", getDescriptor(Function.class));
            codeBuilder.invokestatic(getDescriptor(ConstraintCollectors.class), "collectAndThen",
                    MethodTypeDesc.of(constraintCollectorDesc,
                            constraintCollectorDesc, getDescriptor(Function.class)));
        } else {
            codeBuilder.invokestatic(getDescriptor(ConstraintCollectors.class), "loadBalance",
                    MethodTypeDesc.of(constraintCollectorDesc,
                            getDescriptor(dataStreamInfo.dataStream().getFunctionClass())));
            codeBuilder.getstatic(getDescriptor(LoadBalanceAggregator.class), "LOAD_BALANCE_TO_FLOAT", getDescriptor(Function.class));
            codeBuilder.invokestatic(getDescriptor(ConstraintCollectors.class), "collectAndThen",
                    MethodTypeDesc.of(constraintCollectorDesc,
                            constraintCollectorDesc, getDescriptor(Function.class)));
        }
    }
}
