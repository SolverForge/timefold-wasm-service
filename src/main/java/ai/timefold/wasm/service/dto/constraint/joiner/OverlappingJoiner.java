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

public record OverlappingJoiner(@Nullable @JsonProperty("startMap") WasmFunction startMap,
                                @Nullable @JsonProperty("endMap") WasmFunction endMap,
                                @Nullable @JsonProperty("leftStartMap") WasmFunction leftStartMap,
                                @Nullable @JsonProperty("leftEndMap") WasmFunction leftEndMap,
                                @Nullable @JsonProperty("rightStartMap") WasmFunction rightStartMap,
                                @Nullable @JsonProperty("rightEndMap") WasmFunction rightEndMap,
                                @Nullable @JsonProperty("comparator") WasmFunction comparator) implements DataJoiner {
    public OverlappingJoiner() {
        this(null, null, null, null, null, null, null);
    }

    @JsonCreator
    public OverlappingJoiner {
        if (comparator != null) {
            Stream.of(startMap, endMap,
                    leftStartMap, leftEndMap,
                    rightStartMap,
                    rightEndMap).filter(Objects::nonNull)
                    .forEach(map -> {
                        map.setComparatorFunctionName(comparator.getWasmFunctionName());
                    });
        }
        if (startMap != null) {
            if (endMap == null) {
                throw new IllegalArgumentException("endMap must not be null if startMap is specified");
            }
            if (Stream.of(leftStartMap, leftEndMap,
                          rightStartMap, rightEndMap).anyMatch(Objects::nonNull)) {
                throw new IllegalArgumentException("left/right start/end maps must not be specified if startMap and endMap are used.");
            }
        } else {
            if (!Stream.of(leftStartMap, leftEndMap,
                           rightStartMap, rightEndMap).allMatch(Objects::nonNull)) {
                throw new IllegalArgumentException("All left/right start/end maps must be specified if startMap/endMap are not used.");
            }
        }
    }

    @Override
    public String relation() {
        return "overlapping";
    }

    @Override
    public void loadJoinerInstance(DataStreamInfo dataStreamInfo) {
        var codeBuilder = dataStreamInfo.codeBuilder();
        if (startMap != null) {
            var mapFuncDesc = dataStreamInfo.loadFunctionOfSize(FunctionType.MAPPER, startMap, 1);
            dataStreamInfo.loadFunctionOfSize(FunctionType.MAPPER, endMap, 1);
            codeBuilder.invokestatic(getDescriptor(Joiners.class), "overlapping",
                    MethodTypeDesc.of(getDescriptor(dataStreamInfo.dataStream().getJoinerClass()),
                            mapFuncDesc, mapFuncDesc));
        } else {
            var leftFuncDesc = dataStreamInfo.loadFunction(FunctionType.MAPPER, leftStartMap);
            dataStreamInfo.loadFunction(FunctionType.MAPPER, leftEndMap);
            var rightFuncDesc = dataStreamInfo.loadFunctionOfSize(FunctionType.MAPPER, rightStartMap, 1);
            dataStreamInfo.loadFunctionOfSize(FunctionType.MAPPER, rightEndMap, 1);

            codeBuilder.invokestatic(getDescriptor(Joiners.class), "overlapping",
                    MethodTypeDesc.of(getDescriptor(dataStreamInfo.dataStream().getJoinerClass()),
                            leftFuncDesc, leftFuncDesc, rightFuncDesc, rightFuncDesc));
        }
    }
}
