package ai.timefold.wasm.service.dto.constraint;

import ai.timefold.wasm.service.dto.WasmFunction;

import org.jspecify.annotations.NullMarked;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnore;

@NullMarked
public final class FilterComponent implements StreamComponent {
    WasmFunction filter;

    @Override
    public String kind() {
        return "filter";
    }

    public FilterComponent() {}

    @JsonCreator
    public FilterComponent(WasmFunction filter) {
        this.filter = filter;
    }

    @JsonIgnore
    public WasmFunction getFilter() {
        return filter;
    }
}
