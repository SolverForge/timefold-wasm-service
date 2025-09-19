package ai.timefold.wasm.service.dto;

import java.util.List;

import ai.timefold.wasm.service.dto.constraint.StreamComponent;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

public class WasmConstraint {
    String name;
    @JsonValue
    List<StreamComponent> streamComponentList;

    @JsonCreator
    public WasmConstraint(List<StreamComponent> streamComponentList) {
        this.name = null;
        this.streamComponentList = streamComponentList;
    }

    public String getName() {
        return name;
    }

    public List<StreamComponent> getStreamComponentList() {
        return streamComponentList;
    }

    @Override
    public String toString() {
        return "\"%s\": %s".formatted(name, streamComponentList);
    }
}
