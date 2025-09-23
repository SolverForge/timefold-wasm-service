package ai.timefold.wasm.service.dto.constraint.joiner;

import ai.timefold.wasm.service.classgen.DataStreamInfo;

import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.fasterxml.jackson.databind.annotation.JsonTypeIdResolver;

@JsonTypeInfo(use=JsonTypeInfo.Id.CUSTOM, property="relation", visible=true)
@JsonTypeIdResolver(DataJoinerTypeIdResolver.class)
public sealed interface DataJoiner permits AbstractComparisonJoiner, EqualJoiner, OverlappingJoiner {
    String relation();
    void loadJoinerInstance(DataStreamInfo dataStreamInfo);
}
