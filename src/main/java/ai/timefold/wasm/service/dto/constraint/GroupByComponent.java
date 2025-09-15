package ai.timefold.wasm.service.dto.constraint;

import java.util.Collections;
import java.util.List;
import java.util.Objects;

import ai.timefold.wasm.service.dto.WasmFunction;
import ai.timefold.wasm.service.dto.constraint.groupby.Aggregator;

import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

@NullMarked
public final class GroupByComponent implements StreamComponent {
    private final @JsonProperty("keys") List<WasmFunction> keys;
    private final @JsonProperty("aggregators") List<Aggregator> aggregators;

    public GroupByComponent() {
        this(null, null);
    }

    @JsonCreator
    public GroupByComponent(
            @Nullable @JsonProperty("keys") List<WasmFunction> keys,
            @Nullable @JsonProperty("aggregators") List<Aggregator> aggregators) {
        this.keys = Objects.requireNonNullElse(keys, Collections.emptyList());
        this.aggregators = Objects.requireNonNullElse(aggregators, Collections.emptyList());
    }

    @Override
    public String kind() {
        return "groupBy";
    }

    public List<WasmFunction> getKeys() {
        return keys;
    }

    public List<Aggregator> getAggregators() {
        return aggregators;
    }

    @Override
    public void applyToDataStream(DataStream dataStream) {
        dataStream.setSize(keys.size() + aggregators.size());
    }
}
