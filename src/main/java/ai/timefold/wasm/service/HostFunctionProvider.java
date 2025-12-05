package ai.timefold.wasm.service;

import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import ai.timefold.wasm.service.dto.DomainObject;
import ai.timefold.wasm.service.dto.FieldDescriptor;
import ai.timefold.wasm.service.dto.PlanningProblem;
import ai.timefold.wasm.service.dto.annotation.DomainPlanningScore;

import com.dylibso.chicory.runtime.ExportFunction;
import com.dylibso.chicory.runtime.HostFunction;
import com.dylibso.chicory.runtime.Instance;
import com.dylibso.chicory.wasm.types.FunctionType;
import com.dylibso.chicory.wasm.types.ValType;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * Provides host functions required by WASM modules for solving planning problems.
 *
 * These functions bridge between the WASM runtime and Java, handling:
 * - JSON parsing/serialization of problem data (dynamically based on domain model)
 * - List operations (allocation, access, modification)
 * - Utility functions (rounding)
 *
 * The functions are imported by WASM modules under the "host" namespace.
 */
public class HostFunctionProvider {
    // Standard WASM word size: 4 bytes (32 bits = i32)
    // WASM linear memory uses 4-byte aligned fields for i32 values.
    private static final int WORD_SIZE = Integer.BYTES;

    // List structure offsets (12 bytes total):
    // [size: i32][capacity: i32][backing_array_ptr: i32]
    private static final int SIZE_OFFSET = 0;
    private static final int CAPACITY_OFFSET = WORD_SIZE;
    private static final int BACKING_ARRAY_OFFSET = WORD_SIZE * 2;
    private static final int LIST_HEADER_SIZE = WORD_SIZE * 3;
    private static final int INITIAL_CAPACITY = 4;

    private final ObjectMapper objectMapper;
    private final Map<String, DomainObject> domainObjectMap;

    public HostFunctionProvider(ObjectMapper objectMapper, PlanningProblem planningProblem) {
        this.objectMapper = objectMapper;
        this.domainObjectMap = planningProblem.getDomainObjectMap();
    }

    /**
     * Creates all host functions needed by the WASM module.
     *
     * @return List of host functions to be imported into the WASM instance
     */
    public List<HostFunction> createHostFunctions() {
        return List.of(
                createParseSchedule(),
                createScheduleString(),
                createNewList(),
                createGetItem(),
                createSetItem(),
                createSize(),
                createAppend(),
                createInsert(),
                createRemove(),
                createRound(),
                createStringEquals()
        );
    }

    // ========== Domain Model Helpers ==========

    /**
     * Find the solution class (the one with a DomainObjectMapper).
     */
    private String findSolutionClass() {
        for (var entry : domainObjectMap.entrySet()) {
            if (entry.getValue().getDomainObjectMapper() != null) {
                return entry.getKey();
            }
        }
        throw new IllegalStateException("No solution class found (must have a DomainObjectMapper)");
    }

    /**
     * Calculate the size of a domain object in WASM memory.
     */
    private int calculateObjectSize(DomainObject def) {
        int size = 0;
        for (FieldDescriptor field : def.getFieldDescriptorMap().values()) {
            size += getFieldSize(field.getType());
        }
        return Math.max(size, WORD_SIZE); // Minimum 1 word
    }

    /**
     * Get the size of a field type in bytes.
     */
    private int getFieldSize(String type) {
        return switch (type) {
            case "long", "double", "LocalDate" -> 8; // LocalDate stored as epoch day (long)
            default -> WORD_SIZE; // int, float, pointers, arrays all use 4 bytes
        };
    }

    /**
     * Check if a type is a primitive (not an object reference or array).
     */
    private boolean isPrimitiveType(String type) {
        return type.equals("int") || type.equals("long") ||
               type.equals("float") || type.equals("double") ||
               type.equals("boolean") || type.equals("String") ||
               type.equals("LocalDate");
    }

    /**
     * Check if a field has the PlanningScore annotation.
     */
    private boolean hasPlanningScoreAnnotation(FieldDescriptor field) {
        if (field.getAnnotations() == null) return false;
        return field.getAnnotations().stream()
                .anyMatch(a -> a instanceof DomainPlanningScore);
    }

    // ========== hparseSchedule ==========

    /**
     * hparseSchedule(length: i32, ptr: i32) -> i32
     *
     * Parses a JSON schedule string from WASM memory and creates native WASM objects
     * dynamically based on the domain model.
     * Returns pointer to the allocated schedule structure.
     */
    private HostFunction createParseSchedule() {
        return new HostFunction("host", "hparseSchedule",
                FunctionType.of(List.of(ValType.I32, ValType.I32), List.of(ValType.I32)),
                (instance, args) -> {
                    var scheduleString = instance.memory().readString((int) args[1], (int) args[0]);
                    var alloc = instance.export("alloc");
                    var newList = instance.export("newList");
                    var append = instance.export("append");

                    try {
                        var parsedJson = objectMapper.reader().readTree(scheduleString);

                        // Find solution class and its definition
                        String solutionClassName = findSolutionClass();
                        DomainObject solutionDef = domainObjectMap.get(solutionClassName);

                        // We need to parse collections first to build lookup maps for references
                        Map<String, Map<Object, Integer>> entityMaps = new HashMap<>();

                        // First pass: parse all collections and build entity maps
                        int offset = 0;
                        for (var entry : solutionDef.getFieldDescriptorMap().entrySet()) {
                            String fieldName = entry.getKey();
                            FieldDescriptor field = entry.getValue();

                            if (field.getType().endsWith("[]") && parsedJson.has(fieldName)) {
                                String elementType = field.getType().replace("[]", "");
                                DomainObject elementDef = domainObjectMap.get(elementType);

                                if (elementDef != null) {
                                    var arrayNode = parsedJson.get(fieldName);
                                    Map<Object, Integer> entityMap = new HashMap<>();

                                    for (int i = 0; i < arrayNode.size(); i++) {
                                        JsonNode elementJson = arrayNode.get(i);
                                        // Find the planning ID field to use as key
                                        Object planningId = findPlanningId(elementDef, elementJson);
                                        if (planningId != null) {
                                            entityMap.put(planningId, i);
                                        }
                                    }

                                    entityMaps.put(elementType, entityMap);
                                }
                            }
                            offset += getFieldSize(field.getType());
                        }

                        // Allocate solution object
                        int solutionSize = calculateObjectSize(solutionDef);
                        int solution = (int) alloc.apply(solutionSize)[0];

                        // Second pass: parse all fields and write to memory
                        offset = 0;
                        Map<String, Integer> listPointers = new HashMap<>();

                        for (var entry : solutionDef.getFieldDescriptorMap().entrySet()) {
                            String fieldName = entry.getKey();
                            FieldDescriptor field = entry.getValue();

                            if (hasPlanningScoreAnnotation(field)) {
                                // Skip score field - it's not in the input JSON
                                offset += getFieldSize(field.getType());
                                continue;
                            }

                            if (field.getType().endsWith("[]")) {
                                // Collection field
                                int listPtr = parseCollectionField(instance, alloc, newList, append,
                                        fieldName, field, parsedJson, entityMaps, listPointers);
                                instance.memory().writeI32(solution + offset, listPtr);
                                listPointers.put(field.getType().replace("[]", ""), listPtr);
                            } else if (parsedJson.has(fieldName)) {
                                // Primitive or object field
                                writePrimitiveField(instance, alloc, solution + offset,
                                        field, parsedJson.get(fieldName));
                            }

                            offset += getFieldSize(field.getType());
                        }

                        return new long[] { solution };
                    } catch (JsonProcessingException e) {
                        throw new RuntimeException(e);
                    }
                });
    }

    /**
     * Find the planning ID value from a JSON object based on domain definition.
     */
    private Object findPlanningId(DomainObject def, JsonNode json) {
        for (var entry : def.getFieldDescriptorMap().entrySet()) {
            FieldDescriptor field = entry.getValue();
            if (field.getAnnotations() != null) {
                boolean isPlanningId = field.getAnnotations().stream()
                        .anyMatch(a -> a.getClass().getSimpleName().equals("DomainPlanningId"));
                if (isPlanningId && json.has(entry.getKey())) {
                    JsonNode idNode = json.get(entry.getKey());
                    if (idNode.isInt()) return idNode.asInt();
                    if (idNode.isLong()) return idNode.asLong();
                    if (idNode.isTextual()) return idNode.asText();
                    return idNode.toString();
                }
            }
        }
        return null;
    }

    /**
     * Parse a collection field from JSON and return the list pointer.
     */
    private int parseCollectionField(Instance instance, ExportFunction alloc, ExportFunction newList,
            ExportFunction append, String fieldName, FieldDescriptor field, JsonNode json,
            Map<String, Map<Object, Integer>> entityMaps, Map<String, Integer> listPointers) {

        String elementType = field.getType().replace("[]", "");
        DomainObject elementDef = domainObjectMap.get(elementType);

        int list = (int) newList.apply()[0];

        if (!json.has(fieldName)) {
            return list;
        }

        JsonNode arrayNode = json.get(fieldName);

        for (int i = 0; i < arrayNode.size(); i++) {
            JsonNode elementJson = arrayNode.get(i);
            int element = parseObject(instance, alloc, elementType, elementDef, elementJson,
                    entityMaps, listPointers);
            append.apply(list, element);
        }

        return list;
    }

    /**
     * Parse a single object from JSON and return its pointer.
     */
    private int parseObject(Instance instance, ExportFunction alloc, String className,
            DomainObject def, JsonNode json, Map<String, Map<Object, Integer>> entityMaps,
            Map<String, Integer> listPointers) {

        int size = calculateObjectSize(def);
        int obj = (int) alloc.apply(size)[0];

        int offset = 0;
        for (var entry : def.getFieldDescriptorMap().entrySet()) {
            String fieldName = entry.getKey();
            FieldDescriptor field = entry.getValue();

            if (json.has(fieldName)) {
                JsonNode fieldValue = json.get(fieldName);

                if (field.getType().endsWith("[]")) {
                    // Nested array - not common but handle it
                    // For now, skip nested arrays in entities
                } else if (isPrimitiveType(field.getType())) {
                    writePrimitiveField(instance, alloc, obj + offset, field, fieldValue);
                } else {
                    // Object reference - look up by planning ID
                    writeObjectReference(instance, obj + offset, field.getType(), fieldValue,
                            entityMaps, listPointers);
                }
            }

            offset += getFieldSize(field.getType());
        }

        return obj;
    }

    /**
     * Write a primitive field value to WASM memory.
     */
    private void writePrimitiveField(Instance instance, ExportFunction alloc, int ptr,
            FieldDescriptor field, JsonNode value) {
        switch (field.getType()) {
            case "int" -> instance.memory().writeI32(ptr, value.asInt());
            case "long" -> {
                // Write 64-bit value as two 32-bit writes (little-endian)
                long longVal = value.asLong();
                instance.memory().writeI32(ptr, (int) longVal);
                instance.memory().writeI32(ptr + 4, (int) (longVal >> 32));
            }
            case "float" -> instance.memory().writeF32(ptr, (float) value.asDouble());
            case "double" -> instance.memory().writeF64(ptr, value.asDouble());
            case "boolean" -> instance.memory().writeI32(ptr, value.asBoolean() ? 1 : 0);
            case "String" -> {
                String str = value.asText();
                int strPtr = (int) alloc.apply(str.getBytes().length + 1)[0];
                instance.memory().writeCString(strPtr, str);
                instance.memory().writeI32(ptr, strPtr);
            }
            case "LocalDate" -> {
                // Parse ISO date string (e.g., "2024-01-15") and store as epoch day (long)
                LocalDate date = LocalDate.parse(value.asText());
                long epochDay = date.toEpochDay();
                instance.memory().writeI32(ptr, (int) epochDay);
                instance.memory().writeI32(ptr + 4, (int) (epochDay >> 32));
            }
            default -> {
                // Unknown primitive type, try to write as int
                if (value.isInt()) {
                    instance.memory().writeI32(ptr, value.asInt());
                }
            }
        }
    }

    /**
     * Write an object reference by looking up the referenced entity.
     */
    private void writeObjectReference(Instance instance, int ptr, String refType, JsonNode value,
            Map<String, Map<Object, Integer>> entityMaps, Map<String, Integer> listPointers) {

        if (value.isNull()) {
            instance.memory().writeI32(ptr, 0);
            return;
        }

        // Get the entity map for this type
        Map<Object, Integer> entityMap = entityMaps.get(refType);
        Integer listPtr = listPointers.get(refType);

        if (entityMap == null || listPtr == null) {
            instance.memory().writeI32(ptr, 0);
            return;
        }

        // Find the planning ID in the reference
        DomainObject refDef = domainObjectMap.get(refType);
        Object planningId = findPlanningId(refDef, value);

        if (planningId != null && entityMap.containsKey(planningId)) {
            int index = entityMap.get(planningId);
            // Get the actual pointer from the list
            var getItem = instance.export("getItem");
            int entityPtr = (int) getItem.apply(listPtr, index)[0];
            instance.memory().writeI32(ptr, entityPtr);
        } else {
            instance.memory().writeI32(ptr, 0);
        }
    }

    // ========== hscheduleString ==========

    /**
     * hscheduleString(schedule: i32) -> i32
     *
     * Serializes a WASM schedule object back to JSON string dynamically based on domain model.
     * Returns pointer to the allocated string in WASM memory.
     */
    private HostFunction createScheduleString() {
        return new HostFunction("host", "hscheduleString",
                FunctionType.of(List.of(ValType.I32), List.of(ValType.I32)),
                (instance, args) -> {
                    int schedule = (int) args[0];
                    var alloc = instance.export("alloc");
                    var listSize = instance.export("size");
                    var getItem = instance.export("getItem");

                    String solutionClassName = findSolutionClass();
                    DomainObject solutionDef = domainObjectMap.get(solutionClassName);

                    StringBuilder out = new StringBuilder("{");
                    serializeSolutionObject(instance, listSize, getItem, schedule, solutionDef, out);
                    out.append("}");

                    var outString = out.toString();
                    var memoryString = (int) alloc.apply(outString.getBytes().length + 1)[0];
                    instance.memory().writeCString(memoryString, outString);
                    return new long[] { memoryString };
                });
    }

    /**
     * Serialize a solution object to JSON.
     */
    private void serializeSolutionObject(Instance instance, ExportFunction listSize,
            ExportFunction getItem, int ptr, DomainObject def, StringBuilder out) {

        boolean first = true;
        int offset = 0;

        for (var entry : def.getFieldDescriptorMap().entrySet()) {
            String fieldName = entry.getKey();
            FieldDescriptor field = entry.getValue();

            // Skip score field in serialization
            if (hasPlanningScoreAnnotation(field)) {
                offset += getFieldSize(field.getType());
                continue;
            }

            if (!first) out.append(", ");
            first = false;

            out.append("\"").append(fieldName).append("\": ");

            if (field.getType().endsWith("[]")) {
                serializeCollection(instance, listSize, getItem, ptr + offset, field, out);
            } else if (isPrimitiveType(field.getType())) {
                serializePrimitive(instance, ptr + offset, field.getType(), out);
            } else {
                serializeObjectReference(instance, listSize, getItem, ptr + offset, field.getType(), out);
            }

            offset += getFieldSize(field.getType());
        }
    }

    /**
     * Serialize a collection field to JSON.
     */
    private void serializeCollection(Instance instance, ExportFunction listSize,
            ExportFunction getItem, int ptr, FieldDescriptor field, StringBuilder out) {

        String elementType = field.getType().replace("[]", "");
        DomainObject elementDef = domainObjectMap.get(elementType);

        int listPtr = instance.memory().readInt(ptr);
        int length = (int) listSize.apply((long) listPtr)[0];

        out.append("[");
        for (int i = 0; i < length; i++) {
            if (i > 0) out.append(", ");
            int elementPtr = (int) getItem.apply((long) listPtr, (long) i)[0];
            out.append("{");
            serializeEntityObject(instance, listSize, getItem, elementPtr, elementDef, out);
            out.append("}");
        }
        out.append("]");
    }

    /**
     * Serialize an entity object (not the solution) to JSON.
     */
    private void serializeEntityObject(Instance instance, ExportFunction listSize,
            ExportFunction getItem, int ptr, DomainObject def, StringBuilder out) {

        boolean first = true;
        int offset = 0;

        for (var entry : def.getFieldDescriptorMap().entrySet()) {
            String fieldName = entry.getKey();
            FieldDescriptor field = entry.getValue();

            if (!first) out.append(", ");
            first = false;

            out.append("\"").append(fieldName).append("\": ");

            if (field.getType().endsWith("[]")) {
                // Nested arrays in entities - serialize as empty for now
                out.append("[]");
            } else if (isPrimitiveType(field.getType())) {
                serializePrimitive(instance, ptr + offset, field.getType(), out);
            } else {
                serializeObjectReference(instance, listSize, getItem, ptr + offset, field.getType(), out);
            }

            offset += getFieldSize(field.getType());
        }
    }

    /**
     * Serialize a primitive field to JSON.
     */
    private void serializePrimitive(Instance instance, int ptr, String type, StringBuilder out) {
        switch (type) {
            case "int" -> out.append(instance.memory().readInt(ptr));
            case "long" -> out.append(instance.memory().readLong(ptr));
            case "float" -> out.append(instance.memory().readFloat(ptr));
            case "double" -> out.append(instance.memory().readDouble(ptr));
            case "boolean" -> out.append(instance.memory().readInt(ptr) != 0);
            case "String" -> {
                int strPtr = instance.memory().readInt(ptr);
                if (strPtr == 0) {
                    out.append("null");
                } else {
                    String str = instance.memory().readCString(strPtr);
                    out.append("\"").append(escapeJson(str)).append("\"");
                }
            }
            case "LocalDate" -> {
                // Read epoch day (long) and convert back to ISO date string
                long epochDay = instance.memory().readLong(ptr);
                LocalDate date = LocalDate.ofEpochDay(epochDay);
                out.append("\"").append(date.toString()).append("\"");
            }
            default -> out.append(instance.memory().readInt(ptr));
        }
    }

    /**
     * Serialize an object reference to JSON.
     */
    private void serializeObjectReference(Instance instance, ExportFunction listSize,
            ExportFunction getItem, int ptr, String refType, StringBuilder out) {

        int refPtr = instance.memory().readInt(ptr);

        if (refPtr == 0) {
            out.append("null");
            return;
        }

        DomainObject refDef = domainObjectMap.get(refType);
        if (refDef == null) {
            out.append("null");
            return;
        }

        out.append("{");
        serializeEntityObject(instance, listSize, getItem, refPtr, refDef, out);
        out.append("}");
    }

    /**
     * Escape special characters in JSON strings.
     */
    private String escapeJson(String str) {
        return str.replace("\\", "\\\\")
                  .replace("\"", "\\\"")
                  .replace("\n", "\\n")
                  .replace("\r", "\\r")
                  .replace("\t", "\\t");
    }

    // ========== List Operations ==========

    /**
     * hnewList() -> i32
     *
     * Allocates a new empty list structure in WASM memory.
     * List structure: [size: i32][capacity: i32][backing_array_ptr: i32]
     */
    private HostFunction createNewList() {
        return new HostFunction("host", "hnewList",
                FunctionType.of(List.of(), List.of(ValType.I32)),
                (instance, args) -> {
                    var alloc = instance.export("alloc");
                    var listInstance = (int) alloc.apply(LIST_HEADER_SIZE)[0];

                    // Allocate initial backing array with INITIAL_CAPACITY
                    var backingArray = (int) alloc.apply((long) WORD_SIZE * INITIAL_CAPACITY)[0];

                    instance.memory().writeI32(listInstance + SIZE_OFFSET, 0);
                    instance.memory().writeI32(listInstance + CAPACITY_OFFSET, INITIAL_CAPACITY);
                    instance.memory().writeI32(listInstance + BACKING_ARRAY_OFFSET, backingArray);

                    return new long[] { listInstance };
                });
    }

    /**
     * hgetItem(list: i32, index: i32) -> i32
     *
     * Gets an item from a list at the specified index.
     */
    private HostFunction createGetItem() {
        return new HostFunction("host", "hgetItem",
                FunctionType.of(List.of(ValType.I32, ValType.I32), List.of(ValType.I32)),
                (instance, args) -> {
                    var listInstance = (int) args[0];
                    var backingArray = (int) instance.memory().readI32(listInstance + BACKING_ARRAY_OFFSET);
                    int itemIndex = (int) args[1];
                    var item = instance.memory().readI32(backingArray + (WORD_SIZE * itemIndex));

                    return new long[] { item };
                });
    }

    /**
     * hsetItem(list: i32, index: i32, item: i32)
     *
     * Sets an item in a list at the specified index.
     */
    private HostFunction createSetItem() {
        return new HostFunction("host", "hsetItem",
                FunctionType.of(List.of(ValType.I32, ValType.I32, ValType.I32), List.of()),
                (instance, args) -> {
                    var listInstance = (int) args[0];
                    var backingArray = (int) instance.memory().readI32(listInstance + BACKING_ARRAY_OFFSET);
                    int itemIndex = (int) args[1];
                    var item = (int) args[2];
                    instance.memory().writeI32(backingArray + (WORD_SIZE * itemIndex), item);
                    return new long[] {};
                });
    }

    /**
     * hsize(list: i32) -> i32
     *
     * Returns the size of a list.
     */
    private HostFunction createSize() {
        return new HostFunction("host", "hsize",
                FunctionType.of(List.of(ValType.I32), List.of(ValType.I32)),
                (instance, args) -> {
                    var size = (int) instance.memory().readI32((int) args[0]);
                    return new long[] { size };
                });
    }

    /**
     * happend(list: i32, item: i32)
     *
     * Appends an item to the end of a list.
     * Uses geometric growth (doubling) for O(n) amortized performance.
     */
    private HostFunction createAppend() {
        return new HostFunction("host", "happend",
                FunctionType.of(List.of(ValType.I32, ValType.I32), List.of()),
                (instance, args) -> {
                    var alloc = instance.export("alloc");
                    var listInstance = (int) args[0];
                    var item = (int) args[1];

                    var oldSize = (int) instance.memory().readI32(listInstance + SIZE_OFFSET);
                    var newSize = oldSize + 1;
                    var capacity = (int) instance.memory().readI32(listInstance + CAPACITY_OFFSET);
                    var backingArray = (int) instance.memory().readI32(listInstance + BACKING_ARRAY_OFFSET);

                    // Geometric growth when capacity exceeded
                    if (newSize > capacity) {
                        var newCapacity = Math.max(newSize, capacity * 2);
                        var newBackingArray = (int) alloc.apply((long) WORD_SIZE * newCapacity)[0];
                        instance.memory().copy(newBackingArray, backingArray, oldSize * WORD_SIZE);
                        instance.memory().writeI32(listInstance + CAPACITY_OFFSET, newCapacity);
                        instance.memory().writeI32(listInstance + BACKING_ARRAY_OFFSET, newBackingArray);
                        backingArray = newBackingArray;
                    }

                    instance.memory().writeI32(listInstance + SIZE_OFFSET, newSize);
                    instance.memory().writeI32(backingArray + oldSize * WORD_SIZE, item);

                    return new long[] {};
                });
    }

    /**
     * hinsert(list: i32, index: i32, item: i32)
     *
     * Inserts an item at the specified index in a list.
     * Not implemented - only needed for list planning variables.
     */
    private HostFunction createInsert() {
        return new HostFunction("host", "hinsert",
                FunctionType.of(List.of(ValType.I32, ValType.I32, ValType.I32), List.of()),
                (instance, args) -> {
                    // Not needed for simple planning variables
                    throw new UnsupportedOperationException();
                });
    }

    /**
     * hremove(list: i32, index: i32)
     *
     * Removes an item at the specified index from a list.
     * Not implemented - only needed for list planning variables.
     */
    private HostFunction createRemove() {
        return new HostFunction("host", "hremove",
                FunctionType.of(List.of(ValType.I32, ValType.I32), List.of()),
                (instance, args) -> {
                    // Not needed for simple planning variables
                    throw new UnsupportedOperationException();
                });
    }

    /**
     * hround(value: f32) -> i32
     *
     * Rounds a float value multiplied by 10 to an integer.
     * Used for score calculations with decimal weights.
     */
    private HostFunction createRound() {
        return new HostFunction("host", "hround",
                FunctionType.of(List.of(ValType.F32), List.of(ValType.I32)),
                (instance, args) -> new long[] { (long) (Float.intBitsToFloat((int) args[0]) * 10) });
    }

    /**
     * hstringEquals(ptr1: i32, ptr2: i32) -> i32
     *
     * Compares two C strings in WASM memory for equality.
     * Returns 1 if the strings are equal (same content), 0 otherwise.
     *
     * This function is needed because strings allocated in WASM memory
     * may have different pointers even when they have identical content.
     * Pointer comparison alone is insufficient for string equality.
     */
    private HostFunction createStringEquals() {
        return new HostFunction("host", "hstringEquals",
                FunctionType.of(List.of(ValType.I32, ValType.I32), List.of(ValType.I32)),
                (instance, args) -> {
                    int ptr1 = (int) args[0];
                    int ptr2 = (int) args[1];

                    // If pointers are equal, strings are equal
                    if (ptr1 == ptr2) {
                        return new long[] { 1 };
                    }

                    // If either pointer is null, strings are not equal
                    if (ptr1 == 0 || ptr2 == 0) {
                        return new long[] { 0 };
                    }

                    // Read C strings from memory and compare
                    String str1 = instance.memory().readCString(ptr1);
                    String str2 = instance.memory().readCString(ptr2);

                    return new long[] { str1.equals(str2) ? 1 : 0 };
                });
    }
}
