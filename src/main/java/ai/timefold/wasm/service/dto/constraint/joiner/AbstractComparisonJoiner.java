package ai.timefold.wasm.service.dto.constraint.joiner;

import static ai.timefold.wasm.service.classgen.DomainObjectClassGenerator.getDescriptor;

import java.lang.constant.MethodTypeDesc;
import java.util.Objects;
import java.util.stream.Stream;

import ai.timefold.solver.core.api.score.stream.Joiners;
import ai.timefold.wasm.service.classgen.DataStreamInfo;
import ai.timefold.wasm.service.classgen.FunctionType;
import ai.timefold.wasm.service.dto.WasmFunction;

import org.jspecify.annotations.Nullable;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

public sealed abstract class AbstractComparisonJoiner implements DataJoiner
        permits GreaterThanJoiner, GreaterThanOrEqualJoiner, LessThanJoiner, LessThanOrEqualJoiner {
    @Nullable @JsonProperty("map") WasmFunction map;
    @Nullable @JsonProperty("leftMap") WasmFunction leftMap;
    @Nullable @JsonProperty("rightMap") WasmFunction rightMap;
    @JsonProperty("comparator") WasmFunction comparator;

    public AbstractComparisonJoiner(
            @Nullable WasmFunction map,
            @Nullable WasmFunction leftMap,
            @Nullable WasmFunction rightMap,
            WasmFunction comparator) {
        this.map = map;
        this.leftMap = leftMap;
        this.rightMap = rightMap;
        this.comparator = comparator;

        Stream.of(map, leftMap, rightMap).filter(Objects::nonNull)
                .forEach(wasmMap -> {
                    wasmMap.setComparatorFunctionName(comparator.getWasmFunctionName());
                });
        if (map != null && (leftMap != null || rightMap != null)) {
            throw new IllegalArgumentException("map cannot be specified along with leftMap or rightMap");
        } else if (map == null && leftMap == null && rightMap == null) {
            throw new IllegalArgumentException("no maps specified");
        } else if ((leftMap != null && rightMap == null) ||   (leftMap == null && rightMap != null)) {
            throw new IllegalArgumentException("leftMap and rightMap must both be specified if map is not given");
        }
    }

    @Override
    public void loadJoinerInstance(DataStreamInfo dataStreamInfo) {
        var codeBuilder = dataStreamInfo.codeBuilder();
        if (map != null) {
            var mapFuncDesc = dataStreamInfo.loadFunctionOfSize(FunctionType.MAPPER, map, 1);
            codeBuilder.invokestatic(getDescriptor(Joiners.class),
                    relation(), MethodTypeDesc.of(getDescriptor(dataStreamInfo.dataStream().getJoinerClass()),
                            mapFuncDesc
                    ));
        } else {
            var leftFuncDesc = dataStreamInfo.loadFunction(FunctionType.MAPPER, leftMap);
            var rightFuncDesc = dataStreamInfo.loadFunctionOfSize(FunctionType.MAPPER, rightMap, 1);
            codeBuilder.invokestatic(getDescriptor(Joiners.class),
                    relation(), MethodTypeDesc.of(getDescriptor(dataStreamInfo.dataStream().getJoinerClass()),
                            leftFuncDesc, rightFuncDesc
                            ));
        }
    }
}
