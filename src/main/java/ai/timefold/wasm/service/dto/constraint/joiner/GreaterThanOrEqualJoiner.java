package ai.timefold.wasm.service.dto.constraint.joiner;

import ai.timefold.wasm.service.dto.WasmFunction;

import org.jspecify.annotations.Nullable;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

public final class GreaterThanOrEqualJoiner extends AbstractComparisonJoiner {
    public GreaterThanOrEqualJoiner() {

    }

    @JsonCreator
    public GreaterThanOrEqualJoiner(@Nullable @JsonProperty("map") WasmFunction map,
            @Nullable @JsonProperty("leftMap") WasmFunction leftMap,
            @Nullable @JsonProperty("rightMap") WasmFunction rightMap,
            @JsonProperty("map") WasmFunction comparator) {
        super(map, leftMap, rightMap, comparator);
    }

    @Override
    public String relation() {
        return "greaterThanOrEqual";
    }
}
