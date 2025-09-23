package ai.timefold.wasm.service.dto.constraint;

import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.fasterxml.jackson.databind.annotation.JsonTypeIdResolver;

@JsonTypeInfo(use=JsonTypeInfo.Id.CUSTOM, property="kind", visible=true)
@JsonTypeIdResolver(StreamComponentTypeIdResolver.class)
public sealed interface StreamComponent
        permits ComplementComponent, ExpandComponent, FilterComponent, FlattenLastComponent, ForEachComponent,
        ForEachUniquePairComponent, GroupByComponent, IfExistsComponent, IfNotExistsComponent, JoinComponent, MapComponent,
        PenalizeComponent, RewardComponent {
    String kind();
    default void applyToDataStream(DataStream dataStream) {
        // most datastream operations do not affect cardinality
    }
}
