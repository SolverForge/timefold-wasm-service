package ai.timefold.wasm.service.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

public record DomainListAccessor(@JsonProperty("new") String createFunction,
                                 @JsonProperty("get") String getItemFunction,
                                 @JsonProperty("set") String setItemFunction,
                                 @JsonProperty("length") String getSizeFunction,
                                 @JsonProperty("append") String appendFunction,
                                 @JsonProperty("insert") String insertFunction,
                                 @JsonProperty("remove") String removeFunction,
                                 @JsonProperty("deallocator") String deallocator) {
}
