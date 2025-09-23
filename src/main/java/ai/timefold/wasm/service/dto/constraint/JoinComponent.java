package ai.timefold.wasm.service.dto.constraint;

import java.util.Collections;
import java.util.List;
import java.util.Objects;

import ai.timefold.wasm.service.dto.constraint.joiner.DataJoiner;

import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

@NullMarked
public record JoinComponent(@JsonProperty("className") String className, @Nullable @JsonProperty("joiners") List<DataJoiner> joiners) implements StreamComponent {
    @JsonCreator
    public JoinComponent {
    }

    public JoinComponent() {
        this(null, null);
    }

    public JoinComponent(String className) {
        this(className, Collections.emptyList());
    }

    @Override
    public String kind() {
        return "join";
    }

    public List<DataJoiner> getJoiners() {
        if (joiners == null) {
            return Collections.emptyList();
        }
        return joiners;
    }

    @Override
    public void applyToDataStream(DataStream dataStream) {
        dataStream.incrementSize();
    }
}
