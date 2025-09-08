package ai.timefold.wasm.service.dto.constraint;

import ai.timefold.wasm.service.dto.WasmFunction;

import org.jspecify.annotations.NullMarked;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonValue;

@NullMarked
public record FilterComponent(@JsonValue WasmFunction filter) implements StreamComponent {
    @JsonCreator
    public FilterComponent {
    }

    public FilterComponent() {
        this(null);
    }

    @Override
    public String kind() {
        return "filter";
    }
}
