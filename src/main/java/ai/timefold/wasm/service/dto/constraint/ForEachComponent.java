package ai.timefold.wasm.service.dto.constraint;

import org.jspecify.annotations.NullMarked;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

@NullMarked
public record ForEachComponent(String className) implements StreamComponent {
    @JsonCreator
    public ForEachComponent(@JsonProperty("className") String className) {
        this.className = className;
    }

    public ForEachComponent() {
        this(null);
    }

    @Override
    public String kind() {
        return "forEach";
    }
}
