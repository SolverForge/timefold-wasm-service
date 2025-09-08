package ai.timefold.wasm.service.dto.constraint;

import org.jspecify.annotations.NullMarked;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

@NullMarked
public record JoinComponent(String className) implements StreamComponent {
    @JsonCreator
    public JoinComponent(@JsonProperty("className") String className) {
        this.className = className;
    }

    public JoinComponent() {
        this(null);
    }

    @Override
    public String kind() {
        return "join";
    }

    @Override
    public void applyToDataStream(DataStream dataStream) {
        dataStream.incrementSize();
    }
}
