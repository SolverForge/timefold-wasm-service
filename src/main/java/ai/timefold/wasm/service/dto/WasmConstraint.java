package ai.timefold.wasm.service.dto;

import java.util.List;

import ai.timefold.wasm.service.dto.constraint.StreamComponent;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

public class WasmConstraint {
    String name;
    String weight;

    @JsonProperty("match")
    List<StreamComponent> streamComponentList;

    @JsonCreator
    public WasmConstraint(@JsonProperty("name") String name,
            @JsonProperty("weight") String weight,
            @JsonProperty("match") List<StreamComponent> streamComponentList) {
        this.name = name;
        this.weight = weight;
        this.streamComponentList = streamComponentList;
    }

    public String getName() {
        return name;
    }

    public String getWeight() {
        return weight;
    }

    public List<StreamComponent> getStreamComponentList() {
        return streamComponentList;
    }
}
