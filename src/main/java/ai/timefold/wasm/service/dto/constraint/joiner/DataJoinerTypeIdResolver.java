package ai.timefold.wasm.service.dto.constraint.joiner;

import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.util.Arrays;
import java.util.Map;
import java.util.stream.Collectors;

import ai.timefold.wasm.service.dto.constraint.groupby.Aggregator;

import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.fasterxml.jackson.databind.DatabindContext;
import com.fasterxml.jackson.databind.JavaType;
import com.fasterxml.jackson.databind.jsontype.TypeIdResolver;
import com.fasterxml.jackson.databind.type.TypeFactory;

public class DataJoinerTypeIdResolver implements TypeIdResolver {
    Map<String, JavaType> idToType;

    @Override
    public void init(JavaType baseType) {
        record Pair(String id, JavaType type) {}
        idToType = Arrays.stream(DataJoiner.class.getPermittedSubclasses())
                .map(c -> {
                    try {
                        var instance = (DataJoiner) c.getConstructor().newInstance();
                        return new Pair(instance.relation(), TypeFactory.defaultInstance().constructType(c));
                    } catch (InstantiationException | IllegalAccessException | InvocationTargetException |
                            NoSuchMethodException e) {
                        throw new RuntimeException(e);
                    }
                }).collect(Collectors.toMap(Pair::id, Pair::type));
    }

    @Override
    public String idFromValue(Object value) {
        if (value instanceof Aggregator aggregator) {
            return aggregator.name();
        }
        throw new IllegalArgumentException("Unsupported value " + value);
    }

    @Override
    public String idFromValueAndType(Object value, Class<?> suggestedType) {
        return idFromValue(value);
    }

    @Override
    public String idFromBaseType() {
        throw new UnsupportedOperationException();
    }

    @Override
    public JavaType typeFromId(DatabindContext context, String id) throws IOException {
        return idToType.get(id);
    }

    @Override
    public String getDescForKnownTypeIds() {
        return String.join(",", idToType.keySet());
    }

    @Override
    public JsonTypeInfo.Id getMechanism() {
        return JsonTypeInfo.Id.CUSTOM;
    }
}
