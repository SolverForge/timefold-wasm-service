package ai.timefold.wasm.service.dto.constraint;

import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.fasterxml.jackson.databind.annotation.JsonTypeIdResolver;

@JsonTypeInfo(use=JsonTypeInfo.Id.CUSTOM, property="kind", visible=true)
@JsonTypeIdResolver(StreamComponentTypeIdResolver.class)
public sealed interface StreamComponent permits FilterComponent, ForEachComponent {
    String kind();
}
