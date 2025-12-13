package ai.timefold.wasm.service.dto;

import java.util.List;

import ai.timefold.wasm.service.dto.constraint.StreamComponent;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonValue;

import org.jspecify.annotations.Nullable;

public class WasmConstraint {
    String name;
    @JsonValue
    List<StreamComponent> streamComponentList;

    @Nullable
    IncrementalMetadata incrementalMetadata;

    @JsonCreator
    public WasmConstraint(List<StreamComponent> streamComponentList,
                          @Nullable @JsonProperty("incrementalMetadata") IncrementalMetadata incrementalMetadata) {
        this.name = null;
        this.streamComponentList = streamComponentList;
        this.incrementalMetadata = incrementalMetadata;
    }

    public String getName() {
        return name;
    }

    public List<StreamComponent> getStreamComponentList() {
        return streamComponentList;
    }

    @Nullable
    public IncrementalMetadata getIncrementalMetadata() {
        return incrementalMetadata;
    }

    /**
     * Returns true if this constraint supports incremental scoring.
     */
    public boolean isIncremental() {
        return incrementalMetadata != null &&
               incrementalMetadata.support() == IncrementalSupport.FULLY_INCREMENTAL;
    }

    @Override
    public String toString() {
        return "\"%s\": %s".formatted(name, streamComponentList);
    }

    public static enum IncrementalSupport {
        FULLY_INCREMENTAL,
        NON_INCREMENTAL
    }

    public static record IncrementalMetadata(
        String name,
        IncrementalSupport support,
        List<String> affectedEntities,
        @Nullable String reason
    ) {}
}
