package ai.timefold.wasm.service.dto;

import java.util.Map;

import org.jspecify.annotations.NullMarked;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

@NullMarked
public class DomainObject {
    String name;

    @JsonValue
    Map<String, FieldDescriptor> fieldDescriptorMap;

    public DomainObject(String name, Map<String, FieldDescriptor> fieldDescriptorMap) {
        this.name = name;
        this.fieldDescriptorMap = fieldDescriptorMap;
    }

    @JsonCreator
    public DomainObject(Map<String, FieldDescriptor> fieldDescriptorMap) {
        this.name = null;
        this.fieldDescriptorMap = fieldDescriptorMap;
    }

    void setName(String name) {
        this.name = name;
    }

    public String getName() {
        return name;
    }

    public Map<String, FieldDescriptor> getFieldDescriptorMap() {
        return fieldDescriptorMap;
    }
}
