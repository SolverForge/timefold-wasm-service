package ai.timefold.wasm.service.dto.constraint.groupby;

import static ai.timefold.wasm.service.classgen.DomainObjectClassGenerator.getDescriptor;
import static ai.timefold.wasm.service.classgen.DomainObjectClassGenerator.intDesc;

import java.lang.classfile.BootstrapMethodEntry;
import java.lang.classfile.constantpool.ConstantPoolBuilder;
import java.lang.classfile.constantpool.InvokeDynamicEntry;
import java.lang.classfile.constantpool.MemberRefEntry;
import java.lang.classfile.constantpool.MethodRefEntry;
import java.lang.classfile.constantpool.NameAndTypeEntry;
import java.lang.constant.ClassDesc;
import java.lang.constant.DirectMethodHandleDesc;
import java.lang.constant.MethodTypeDesc;
import java.util.List;
import java.util.Objects;
import java.util.function.Function;

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
public record CountAggregator(@Nullable @JsonProperty("distinct") Boolean countDistinct,
                              @Nullable @JsonProperty("map") WasmFunction map) implements Aggregator {
    @JsonCreator
    public CountAggregator {

    }

    public CountAggregator() {
        this(null, null);
    }

    public boolean isDistinct() {
        return Objects.requireNonNullElse(countDistinct, false);
    }

    @Override
    public String name() {
        return "count";
    }

    @Override
    public void loadAggregatorInstance(DataStreamInfo dataStreamInfo) {
        var codeBuilder = dataStreamInfo.codeBuilder();
        String functionName;
        if (isDistinct()) {
            functionName = "countDistinct";
        } else {
            functionName = "count" + dataStreamInfo.dataStream().getSizeSuffix();
        }
        ClassDesc[] methodParameterTypes;
        if (map == null) {
            methodParameterTypes = new ClassDesc[0];
        } else {
            methodParameterTypes = new ClassDesc[] {
                    getDescriptor(dataStreamInfo.dataStream().getFunctionClass())
            };
            dataStreamInfo.loadFunction(FunctionType.MAPPER, map);
        }

        var constraintCollectorDesc = getDescriptor(dataStreamInfo.dataStream().getConstraintCollectorClass());
        codeBuilder.invokestatic(getDescriptor(ConstraintCollectors.class), functionName,
                MethodTypeDesc.of(constraintCollectorDesc,
                        methodParameterTypes));
        codeBuilder.getstatic(getDescriptor(WasmObject.class), "WRAPPING_INT", getDescriptor(Function.class));
        codeBuilder.invokestatic(getDescriptor(ConstraintCollectors.class), "collectAndThen",
                MethodTypeDesc.of(constraintCollectorDesc,
                                  constraintCollectorDesc, getDescriptor(Function.class)));
    }
}
