package ai.timefold.wasm.service.dto.constraint;

import org.jspecify.annotations.NullMarked;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;

@NullMarked
public final class ForEachComponent implements StreamComponent {
    String className;

    @Override
    public String kind() {
        return "each";
    }

    public ForEachComponent() {}

    @JsonCreator
    public ForEachComponent(@JsonProperty("className") String className) {
        this.className = className;
    }

    @JsonIgnore
    public String getClassName() {
        return className;
    }
}
