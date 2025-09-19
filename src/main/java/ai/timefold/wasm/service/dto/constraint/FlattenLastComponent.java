package ai.timefold.wasm.service.dto.constraint;

import ai.timefold.wasm.service.dto.WasmFunction;

import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

@NullMarked
public record FlattenLastComponent(@Nullable @JsonProperty("map") WasmFunction map) implements StreamComponent {
    @JsonCreator
    public FlattenLastComponent {
    }

    public FlattenLastComponent() {
        this(null);
    }

    @Override
    public String kind() {
        return "flattenLast";
    }
}
