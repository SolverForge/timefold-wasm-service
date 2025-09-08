package ai.timefold.wasm.service.classgen;

import java.lang.classfile.Annotation;
import java.lang.classfile.ClassFile;
import java.lang.classfile.CodeBuilder;
import java.lang.classfile.TypeKind;
import java.lang.classfile.attribute.RuntimeVisibleAnnotationsAttribute;
import java.lang.constant.ClassDesc;
import java.lang.constant.MethodTypeDesc;
import java.lang.reflect.Array;
import java.lang.reflect.InvocationTargetException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

import ai.timefold.solver.core.api.domain.entity.PlanningEntity;
import ai.timefold.solver.core.api.domain.solution.PlanningSolution;
import ai.timefold.solver.core.api.score.buildin.hardmediumsoft.HardMediumSoftScore;
import ai.timefold.solver.core.api.score.buildin.hardsoft.HardSoftScore;
import ai.timefold.solver.core.api.score.buildin.simple.SimpleScore;
import ai.timefold.solver.core.impl.domain.solution.cloner.PlanningCloneable;
import ai.timefold.wasm.service.dto.DomainObject;
import ai.timefold.wasm.service.dto.PlanningProblem;
import ai.timefold.wasm.service.dto.annotation.DomainPlanningScore;

import com.dylibso.chicory.runtime.Instance;

public class DomainObjectClassGenerator {
    private final Map<String, byte[]> classNameToBytecode = new HashMap<>();
    private final DomainObjectClassLoader classLoader = new DomainObjectClassLoader(classNameToBytecode);
    public static ThreadLocal<DomainObjectClassGenerator> domainObjectClassGenerator = new ThreadLocal<>();

    private static class DomainObjectClassLoader extends ClassLoader {
        Map<String, byte[]> classNameToBytecode;

        public DomainObjectClassLoader(Map<String,byte[]> classNameToBytecode) {
            this.classNameToBytecode = classNameToBytecode;
        }

        public Class<?> loadClass(String name) throws ClassNotFoundException {
            try {
                return super.loadClass(name);
            } catch (ClassNotFoundException e) {
                if (classNameToBytecode.containsKey(name)) {
                    var bytecode = classNameToBytecode.get(name);
                    return defineClass(name, bytecode, 0, bytecode.length);
                }
                throw e;
            }
        }
    }

    static final ClassDesc wasmObjectDesc = getDescriptor(WasmObject.class);
    static final ClassDesc instanceDesc = getDescriptor(Instance.class);
    static final ClassDesc mapDesc = getDescriptor(Map.class);

    static final ClassDesc intDesc = ClassDesc.ofDescriptor("I");
    static final ClassDesc longDesc = ClassDesc.ofDescriptor("J");
    static final ClassDesc floatDesc = ClassDesc.ofDescriptor("F");
    static final ClassDesc doubleDesc = ClassDesc.ofDescriptor("D");

    static final ClassDesc voidDesc = ClassDesc.ofDescriptor("V");
    static final ClassDesc objectDesc = getDescriptor(Object.class);
    static final ClassDesc stringDesc = getDescriptor(String.class);
    static final ClassDesc numberDesc = getDescriptor(Number.class);

    static String getInternalName(Class<?> clazz) {
        return clazz.getCanonicalName().replace('.', '/');
    }

    static ClassDesc getDescriptor(Class<?> clazz) {
        return ClassDesc.ofInternalName(getInternalName(clazz));
    }

    static ClassDesc getWasmTypeDesc(String wasmType) {
        if (wasmType.endsWith("[]")) {
            return getWasmTypeDesc(wasmType.substring(0, wasmType.length() - 2)).arrayType();
        }
        return switch (wasmType) {
            case "int" -> intDesc;
            case "long" -> longDesc;
            case "float" -> floatDesc;
            case "double" -> doubleDesc;
            case "String" -> stringDesc;
            case "SimpleScore" -> getDescriptor(SimpleScore.class);
            case "HardSoftScore" -> getDescriptor(HardSoftScore.class);
            case "HardMediumSoftScore" -> getDescriptor(HardMediumSoftScore.class);
            default -> ClassDesc.ofInternalName(wasmType);
        };
    }

    static TypeKind getTypeKind(String wasmType) {
        return switch (wasmType) {
            case "int" -> TypeKind.INT;
            case "long" -> TypeKind.LONG;
            case "float" -> TypeKind.FLOAT;
            case "double" -> TypeKind.DOUBLE;
            default -> TypeKind.REFERENCE;
        };
    }

    static String getGetterName(String fieldName) {
        return "get" + fieldName.substring(0, 1).toUpperCase() + fieldName.substring(1);
    }

    static String getSetterName(String fieldName) {
        return "set" + fieldName.substring(0, 1).toUpperCase() + fieldName.substring(1);
    }

    private record WasmOffsets(int totalSize, Map<String, Integer> nameToMemoryOffset) {
    }

    private static WasmOffsets calculateWasmOffsets(DomainObject domainObject) {
        int totalSize = 0;
        Map<String, Integer> nameToMemoryOffset = new HashMap<>();
        for (var field : domainObject.getFieldDescriptorMap().entrySet()) {
            var fieldType = field.getValue().getType();
            nameToMemoryOffset.put(field.getKey(), totalSize);
            totalSize += switch (fieldType) {
                case "int" -> Integer.SIZE;
                case "long" -> Long.SIZE;
                case "float" -> Float.SIZE;
                case "double" -> Double.SIZE;
                default -> Integer.SIZE;  // Pointers are integers
            };
        }
        return new WasmOffsets(totalSize, Collections.unmodifiableMap(nameToMemoryOffset));
    }

    private static void writeWasmField(CodeBuilder codeBuilder, int offset, String type,
            Consumer<CodeBuilder> valueSupplier) {
        codeBuilder.aload(0);
        codeBuilder.loadConstant(offset);
        valueSupplier.accept(codeBuilder);

        switch (type) {
            case "int" -> {
                codeBuilder.invokevirtual(wasmObjectDesc, "writeIntField",
                        MethodTypeDesc.of(voidDesc, intDesc, intDesc));
            }
            case "long" -> {
                codeBuilder.invokevirtual(wasmObjectDesc, "writeLongField",
                        MethodTypeDesc.of(voidDesc, intDesc, longDesc));
            }
            case "float" -> {
                codeBuilder.invokevirtual(wasmObjectDesc, "writeFloatField",
                        MethodTypeDesc.of(voidDesc, intDesc, floatDesc));
            }
            case "double" -> {
                codeBuilder.invokevirtual(wasmObjectDesc, "writeDoubleField",
                        MethodTypeDesc.of(voidDesc, intDesc, intDesc));
            }
            default -> {
                codeBuilder.invokevirtual(wasmObjectDesc, "writeReferenceField",
                        MethodTypeDesc.of(voidDesc, intDesc, intDesc));
            }
        };
    }

    private static void readWasmField(CodeBuilder codeBuilder, int offset, String type) {
        codeBuilder.aload(0);
        codeBuilder.loadConstant(offset);

        switch (type) {
            case "int" -> {
                codeBuilder.invokevirtual(wasmObjectDesc, "readIntField",
                        MethodTypeDesc.of(intDesc, intDesc));
            }
            case "long" -> {
                codeBuilder.invokevirtual(wasmObjectDesc, "readLongField",
                        MethodTypeDesc.of(longDesc, intDesc));
            }
            case "float" -> {
                codeBuilder.invokevirtual(wasmObjectDesc, "readFloatField",
                        MethodTypeDesc.of(floatDesc, intDesc));
            }
            case "double" -> {
                codeBuilder.invokevirtual(wasmObjectDesc, "readDoubleField",
                        MethodTypeDesc.of(doubleDesc, intDesc));
            }
            default -> {
                codeBuilder.invokevirtual(wasmObjectDesc, "readReferenceField",
                        MethodTypeDesc.of(wasmObjectDesc, intDesc));
                if (type.endsWith("[]")) {
                    codeBuilder.checkcast(wasmObjectDesc);

                    String elementType = type.substring(0, type.length() - 2);
                    codeBuilder.loadConstant(switch (elementType) {
                        case "long", "double" -> Long.SIZE;
                        default -> Integer.SIZE;
                    });
                    codeBuilder.loadConstant(getWasmTypeDesc(elementType));
                    codeBuilder.invokevirtual(wasmObjectDesc, "toJavaArray", MethodTypeDesc.of(objectDesc, intDesc, getDescriptor(Class.class)));
                    codeBuilder.checkcast(getWasmTypeDesc(type));
                } else {
                    codeBuilder.checkcast(getWasmTypeDesc(type));
                }
            }
        }
    }

    private static void convertObjectToWasmValue(CodeBuilder codeBuilder, String type) {
        codeBuilder.dup();
        codeBuilder.aconst_null();
        var doneLabel = codeBuilder.newLabel();
        var isNullLabel = codeBuilder.newLabel();
        codeBuilder.if_acmpeq(isNullLabel);


        if (type.endsWith("[]")) {
            String elementType = type.substring(0, type.length() - 2);
            var arraySize = switch (elementType) {
                case "long", "double" -> Long.SIZE;
                default -> Integer.SIZE;
            };
            codeBuilder.dup();
            codeBuilder.instanceOf(getDescriptor(Collection.class));
            var isArrayLabel = codeBuilder.newLabel();
            codeBuilder.loadConstant(0);
            codeBuilder.if_icmpeq(isArrayLabel);

            codeBuilder.checkcast(getDescriptor(Collection.class));
            codeBuilder.invokeinterface(getDescriptor(Collection.class), "toArray", MethodTypeDesc.of(objectDesc.arrayType()));
            // Needed since type resolver would otherwise try to resolve the class we are defining,
            // causing an illegal argument exception
            codeBuilder.checkcast(getDescriptor(Object.class));

            codeBuilder.labelBinding(isArrayLabel);
            codeBuilder.dup();
            codeBuilder.aload(0);
            codeBuilder.swap();
            codeBuilder.invokestatic(getDescriptor(Array.class), "getLength", MethodTypeDesc.of(intDesc, objectDesc));
            codeBuilder.loadConstant(arraySize);
            codeBuilder.invokevirtual(wasmObjectDesc, "allocateArray", MethodTypeDesc.of(wasmObjectDesc, intDesc, intDesc));
            codeBuilder.loadConstant(arraySize);
            codeBuilder.loadConstant(elementType);
            codeBuilder.invokestatic(getDescriptor(DomainObjectClassGenerator.class), "copyArrayToWasmObject", MethodTypeDesc.of(intDesc, objectDesc, wasmObjectDesc, intDesc, stringDesc));
        } else {
            switch (type) {
                case "int" -> {
                    codeBuilder.checkcast(numberDesc);
                    codeBuilder.invokevirtual(numberDesc, "intValue",
                            MethodTypeDesc.of(intDesc));
                }
                case "long" -> {
                    codeBuilder.checkcast(numberDesc);
                    codeBuilder.invokevirtual(numberDesc, "longValue",
                            MethodTypeDesc.of(longDesc));
                }
                case "float" -> {
                    codeBuilder.checkcast(numberDesc);
                    codeBuilder.invokevirtual(numberDesc, "floatValue",
                            MethodTypeDesc.of(floatDesc));
                }
                case "double" -> {
                    codeBuilder.checkcast(numberDesc);
                    codeBuilder.invokevirtual(numberDesc, "doubleValue",
                            MethodTypeDesc.of(doubleDesc));
                }
                case "String" -> {
                    codeBuilder.checkcast(stringDesc);
                    codeBuilder.aload(0);
                    codeBuilder.swap();
                    codeBuilder.invokevirtual(wasmObjectDesc, "allocateString",
                            MethodTypeDesc.of(intDesc, stringDesc));
                }
                default -> {
                    codeBuilder.dup();
                    codeBuilder.instanceOf(mapDesc);
                    codeBuilder.loadConstant(0);
                    var isWasmObjectLabel = codeBuilder.newLabel();
                    codeBuilder.if_icmpeq(isWasmObjectLabel);

                    codeBuilder.checkcast(mapDesc);
                    codeBuilder.new_(getWasmTypeDesc(type));
                    codeBuilder.dup_x1();
                    codeBuilder.swap();
                    codeBuilder.aload(0);
                    codeBuilder.getfield(wasmObjectDesc, "wasmInstance", instanceDesc);
                    codeBuilder.swap();
                    codeBuilder.invokespecial(getWasmTypeDesc(type), "<init>", MethodTypeDesc.of(voidDesc, instanceDesc, mapDesc));

                    codeBuilder.labelBinding(isWasmObjectLabel);
                    codeBuilder.checkcast(wasmObjectDesc);
                    codeBuilder.getfield(wasmObjectDesc, "memoryPointer", intDesc);
                }
            }
        }
        codeBuilder.goto_(doneLabel);
        codeBuilder.labelBinding(isNullLabel);
        codeBuilder.pop();
        switch (type) {
            case "int" -> {
                codeBuilder.loadConstant(0);
            }
            case "long" -> {
                codeBuilder.loadConstant(0L);
            }
            case "float" -> {
                codeBuilder.loadConstant(0f);
            }
            case "double" -> {
                codeBuilder.loadConstant(0d);
            }
            default -> {
                codeBuilder.loadConstant(0);
            }
        }

        codeBuilder.labelBinding(doneLabel);
    }

    public static int copyArrayToWasmObject(Object array, WasmObject wasmArray, int arrayElementSize, String elementType) {
        var arrayLength = Array.getLength(array);
        var wasmMemory = wasmArray.getMemory();
        var pointer = wasmArray.memoryPointer;

        for (int i = 0; i < arrayLength; i++) {
            var element = Array.get(array, i);
            var elementLocation = pointer + arrayElementSize * (i + 1);

            switch (elementType) {
                case "int" -> {
                   var number = (Number) element;
                   wasmMemory.writeI32(elementLocation, number.intValue());
                }
                case "long" -> {
                    var number = (Number) element;
                    wasmMemory.writeLong(elementLocation, number.longValue());
                }
                case "float" -> {
                    var number = (Number) element;
                    wasmMemory.writeF32(elementLocation, number.floatValue());
                }
                case "double" -> {
                    var number = (Number) element;
                    wasmMemory.writeF64(elementLocation, number.doubleValue());
                }
                default -> {
                    if (element instanceof Map map) {
                        var elementClass = domainObjectClassGenerator.get().getClassForDomainClassName(elementType);
                        var wasmInstance = wasmArray.wasmInstance;
                        try {
                            var newInstance = (WasmObject) elementClass.getConstructor(Instance.class, Map.class).newInstance(wasmInstance, map);
                            wasmMemory.writeI32(elementLocation, newInstance.memoryPointer);
                        } catch (InstantiationException | NoSuchMethodException | InvocationTargetException |
                                IllegalAccessException e) {
                            throw new RuntimeException(e);
                        }
                    } else if (element instanceof WasmObject object) {
                        wasmMemory.writeI32(elementLocation, object.memoryPointer);
                    } else {
                        throw new RuntimeException();
                    }
                }
            }
        }

        return pointer;
    }

    public void prepareClassesForPlanningProblem(PlanningProblem planningProblem) {
        for (var domainEntry : planningProblem.getDomainObjectMap().entrySet()) {
            prepareClassForDomainObject(domainEntry.getValue());
        }
    }

    public void prepareClassForDomainObject(DomainObject domainObject) {
        var wasmOffsets = calculateWasmOffsets(domainObject);
        var classFile = ClassFile.of();


        var classBytes = classFile.build(ClassDesc.of(domainObject.getName()), classBuilder -> {
            var isPlanningEntity = false;
            var isPlanningSolution = false;
            classBuilder.withSuperclass(wasmObjectDesc);
            //classBuilder.withInterfaceSymbols(getDescriptor(PlanningCloneable.class));

            // No-args constructor
            classBuilder.withMethodBody("<init>", MethodTypeDesc.of(voidDesc), ClassFile.ACC_PUBLIC, codeBuilder -> {
                codeBuilder.aload(0);
                codeBuilder.invokespecial(wasmObjectDesc, "<init>", MethodTypeDesc.of(voidDesc));
                codeBuilder.return_();
            });

            // instance constructor
            classBuilder.withMethodBody("<init>", MethodTypeDesc.of(voidDesc, instanceDesc), ClassFile.ACC_PUBLIC, codeBuilder -> {
                codeBuilder.aload(0);
                codeBuilder.aload(1);
                codeBuilder.loadConstant(wasmOffsets.totalSize);
                codeBuilder.invokespecial(wasmObjectDesc, "<init>", MethodTypeDesc.of(voidDesc, instanceDesc, intDesc));
                codeBuilder.return_();
            });

            // instance + map constructor
            classBuilder.withMethodBody("<init>", MethodTypeDesc.of(voidDesc, instanceDesc, mapDesc), ClassFile.ACC_PUBLIC, codeBuilder -> {
                codeBuilder.aload(0);
                codeBuilder.aload(1);
                codeBuilder.loadConstant(wasmOffsets.totalSize);
                codeBuilder.invokespecial(wasmObjectDesc, "<init>", MethodTypeDesc.of(voidDesc, instanceDesc, intDesc));

                for (var field : domainObject.getFieldDescriptorMap().entrySet()) {
                    var key = field.getKey();
                    var offset = wasmOffsets.nameToMemoryOffset.get(key);
                    writeWasmField(codeBuilder, offset, field.getValue().getType(), valueBuilder -> {
                        valueBuilder.aload(2);
                        valueBuilder.loadConstant(key);
                        valueBuilder.invokeinterface(mapDesc, "get", MethodTypeDesc.of(objectDesc, objectDesc));
                        convertObjectToWasmValue(valueBuilder, field.getValue().getType());
                    });
                }

                codeBuilder.return_();
            });

            for (var field : domainObject.getFieldDescriptorMap().entrySet()) {
                var annotations = field.getValue().getAnnotations();
                if (annotations == null || annotations.isEmpty()) {
                    continue;
                }
                var typeDesc = getWasmTypeDesc(field.getValue().getType());

                var isPlanningScore = false;
                for (var annotation : annotations) {
                    isPlanningEntity |= annotation.definesPlanningEntity();
                    isPlanningSolution |= annotation.definesPlanningSolution();
                    isPlanningScore |= annotation instanceof DomainPlanningScore;
                }
                var finalIsPlanningScore = isPlanningScore;

                if (isPlanningScore) {
                    classBuilder.withField(field.getKey(), typeDesc, ClassFile.ACC_PRIVATE);
                }

                // Getter
                classBuilder.withMethod(getGetterName(field.getKey()),
                        MethodTypeDesc.of(typeDesc), ClassFile.ACC_PUBLIC, methodBuilder -> {
                            List<Annotation> annotationsList = new ArrayList<>();
                            for (var annotation : annotations) {
                                annotationsList.add(Annotation.of(getDescriptor(annotation.annotationClass()),
                                                annotation.getAnnotationElements()));
                            }
                            methodBuilder.with(RuntimeVisibleAnnotationsAttribute.of(annotationsList));
                            methodBuilder.withCode(codeBuilder -> {
                                if (finalIsPlanningScore) {
                                    codeBuilder.aload(0);
                                    codeBuilder.getfield(ClassDesc.of(domainObject.getName()), field.getKey(), typeDesc);
                                } else {
                                    var offset = wasmOffsets.nameToMemoryOffset.get(field.getKey());
                                    readWasmField(codeBuilder, offset, field.getValue().getType());
                                }
                                codeBuilder.return_(getTypeKind(field.getValue().getType()));
                            });
                        });

                // Setter
                classBuilder.withMethodBody(getSetterName(field.getKey()),
                        MethodTypeDesc.of(voidDesc, typeDesc), ClassFile.ACC_PUBLIC, codeBuilder -> {
                                if (finalIsPlanningScore) {
                                    codeBuilder.aload(0);
                                    codeBuilder.aload(1);
                                    codeBuilder.putfield(ClassDesc.of(domainObject.getName()), field.getKey(), typeDesc);
                                } else {
                                    var offset = wasmOffsets.nameToMemoryOffset.get(field.getKey());
                                    writeWasmField(codeBuilder, offset, field.getValue().getType(), valueBuilder -> {
                                        valueBuilder.aload(1);
                                        convertObjectToWasmValue(codeBuilder, field.getValue().getType());
                                    });
                                }
                                codeBuilder.return_();
                            });
            }
            if (isPlanningEntity && isPlanningSolution) {
                throw new IllegalArgumentException("Class %s is both a planning entity and planning solution."
                        .formatted(domainObject.getName()));
            }
            if (isPlanningEntity) {
                classBuilder.with(RuntimeVisibleAnnotationsAttribute.of(Annotation.of(getDescriptor(PlanningEntity.class))));
            }
            if (isPlanningSolution) {
                classBuilder.with(RuntimeVisibleAnnotationsAttribute.of(Annotation.of(getDescriptor(PlanningSolution.class))));
            }

            // toString
            classBuilder.withMethodBody("toString", MethodTypeDesc.of(stringDesc), ClassFile.ACC_PUBLIC, codeBuilder -> {
                var stringBuilderDesc = getDescriptor(StringBuilder.class);
                codeBuilder.new_(stringBuilderDesc);
                codeBuilder.dup();
                codeBuilder.invokespecial(stringBuilderDesc, "<init>", MethodTypeDesc.of(voidDesc));

                codeBuilder.loadConstant(domainObject.getName() + "(");
                codeBuilder.invokevirtual(stringBuilderDesc, "append", MethodTypeDesc.of(stringBuilderDesc, stringDesc));

                var fieldIndex = 0;
                for (var field : domainObject.getFieldDescriptorMap().entrySet()) {
                    fieldIndex++;
                    codeBuilder.loadConstant(field.getKey());
                    codeBuilder.invokevirtual(stringBuilderDesc, "append", MethodTypeDesc.of(stringBuilderDesc, stringDesc));

                    codeBuilder.loadConstant(": ");
                    codeBuilder.invokevirtual(stringBuilderDesc, "append", MethodTypeDesc.of(stringBuilderDesc, stringDesc));

                    readWasmField(codeBuilder, wasmOffsets.nameToMemoryOffset.get(field.getKey()), field.getValue().getType());
                    switch (field.getValue().getType()) {
                        case "int" -> {
                            codeBuilder.invokevirtual(stringBuilderDesc, "append", MethodTypeDesc.of(stringBuilderDesc, intDesc));
                        }
                        case "long" -> {
                            codeBuilder.invokevirtual(stringBuilderDesc, "append", MethodTypeDesc.of(stringBuilderDesc, longDesc));
                        }
                        case "float" -> {
                            codeBuilder.invokevirtual(stringBuilderDesc, "append", MethodTypeDesc.of(stringBuilderDesc, floatDesc));
                        }
                        case "double" -> {
                            codeBuilder.invokevirtual(stringBuilderDesc, "append", MethodTypeDesc.of(stringBuilderDesc, doubleDesc));
                        }
                        default -> {
                            if (field.getValue().getAnnotations() != null && field.getValue().getAnnotations().stream().anyMatch(annotation -> annotation instanceof DomainPlanningScore)) {
                                // the score on the stack if the null score we reserved on the object; we want the one from the field
                                codeBuilder.pop();
                                codeBuilder.aload(0);
                                codeBuilder.getfield(ClassDesc.ofInternalName(domainObject.getName()), field.getKey(), getWasmTypeDesc(field.getValue().getType()));
                                codeBuilder.invokevirtual(stringBuilderDesc, "append", MethodTypeDesc.of(stringBuilderDesc, objectDesc));
                            } else {
                                if (field.getValue().getType().endsWith("[]")) {
                                    codeBuilder.invokestatic(getDescriptor(Arrays.class), "toString", MethodTypeDesc.of(stringDesc, objectDesc.arrayType()));
                                    codeBuilder.invokevirtual(stringBuilderDesc, "append", MethodTypeDesc.of(stringBuilderDesc, stringDesc));
                                } else {
                                    codeBuilder.invokevirtual(stringBuilderDesc, "append", MethodTypeDesc.of(stringBuilderDesc, objectDesc));
                                }
                            }
                        }
                    }

                    if (fieldIndex != domainObject.getFieldDescriptorMap().size()) {
                        codeBuilder.loadConstant(", ");
                        codeBuilder.invokevirtual(stringBuilderDesc, "append", MethodTypeDesc.of(stringBuilderDesc, stringDesc));
                    }
                }

                codeBuilder.loadConstant(")");
                codeBuilder.invokevirtual(stringBuilderDesc, "append", MethodTypeDesc.of(stringBuilderDesc, stringDesc));

                codeBuilder.invokevirtual(stringBuilderDesc, "toString", MethodTypeDesc.of(stringDesc));
                codeBuilder.return_(TypeKind.REFERENCE);
            });

            // toMap
            classBuilder.withMethodBody("toMap", MethodTypeDesc.of(mapDesc), ClassFile.ACC_PUBLIC, codeBuilder -> {
                codeBuilder.new_(getDescriptor(LinkedHashMap.class));
                codeBuilder.dup();
                codeBuilder.invokespecial(getDescriptor(LinkedHashMap.class), "<init>", MethodTypeDesc.of(voidDesc));

                for (var field : domainObject.getFieldDescriptorMap().entrySet()) {
                    codeBuilder.dup();
                    codeBuilder.loadConstant(field.getKey());
                    readWasmField(codeBuilder, wasmOffsets.nameToMemoryOffset.get(field.getKey()), field.getValue().getType());
                    switch (field.getValue().getType()) {
                        case "int" -> {
                            codeBuilder.invokestatic(getDescriptor(Integer.class), "valueOf", MethodTypeDesc.of(getDescriptor(Integer.class), intDesc));
                        }
                        case "long" -> {
                            codeBuilder.invokestatic(getDescriptor(Long.class), "valueOf", MethodTypeDesc.of(getDescriptor(Long.class), longDesc));
                        }
                        case "float" -> {
                            codeBuilder.invokestatic(getDescriptor(Float.class), "valueOf", MethodTypeDesc.of(getDescriptor(Float.class), floatDesc));
                        }
                        case "double" -> {
                            codeBuilder.invokestatic(getDescriptor(Double.class), "valueOf", MethodTypeDesc.of(getDescriptor(Double.class), doubleDesc));
                        }
                        default -> {
                            if (field.getValue().getAnnotations() != null && field.getValue().getAnnotations().stream().anyMatch(annotation -> annotation instanceof DomainPlanningScore)) {
                                // the score on the stack if the null score we reserved on the object; we want the one from the field
                                codeBuilder.pop();
                                codeBuilder.aload(0);
                                codeBuilder.getfield(ClassDesc.ofInternalName(domainObject.getName()), field.getKey(), getWasmTypeDesc(field.getValue().getType()));
                            } else if (field.getValue().getType().endsWith("[]")) {
                                codeBuilder.invokestatic(getDescriptor(Arrays.class), "asList", MethodTypeDesc.of(getDescriptor(List.class), objectDesc.arrayType()));
                            } else {
                                codeBuilder.invokevirtual(ClassDesc.ofInternalName(field.getValue().getType()), "toMap", MethodTypeDesc.of(mapDesc));
                            }
                        }
                    }
                    codeBuilder.invokeinterface(mapDesc, "put", MethodTypeDesc.of(objectDesc, objectDesc, objectDesc));
                    codeBuilder.pop();
                }
                codeBuilder.return_(TypeKind.REFERENCE);
            });

            // createNewInstance
//            classBuilder.withMethodBody("createNewInstance",
//                    MethodTypeDesc.of(objectDesc), ClassFile.ACC_PUBLIC,
//                    codeBuilder -> {
//                        codeBuilder.new_(ClassDesc.ofInternalName(domainObject.getName()));
//                        codeBuilder.dup();
//
//                        codeBuilder.aload(0);
//                        codeBuilder.getfield(wasmObjectDesc, "wasmInstance", instanceDesc);
//                        codeBuilder.invokespecial(ClassDesc.ofInternalName(domainObject.getName()),
//                                "<init>", MethodTypeDesc.of(voidDesc, instanceDesc));
//                        codeBuilder.return_(TypeKind.REFERENCE);
//                    });
        });

        classNameToBytecode.put(domainObject.getName(), classBytes);
    }

    public Class<?> getClassForDomainObject(DomainObject domainObject) {
        return getClassForDomainClassName(domainObject.getName());
    }

    public Class<?> getClassForDomainClassName(String className) {
        try {
            return classLoader.loadClass(className);
        } catch (ClassNotFoundException e) {
            throw new RuntimeException(e);
        }
    }

    public Class<?> defineConstraintProviderClass(String className, byte[] classBytes) {
        classNameToBytecode.put(className, classBytes);
        return getClassForDomainClassName(className);
    }
}
