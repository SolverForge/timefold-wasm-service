package ai.timefold.wasm.service.classgen;

import java.io.OutputStream;
import java.io.PrintStream;
import java.lang.classfile.Annotation;
import java.lang.classfile.AnnotationElement;
import java.lang.classfile.AnnotationValue;
import java.lang.classfile.ClassFile;
import java.lang.classfile.CodeBuilder;
import java.lang.classfile.MethodSignature;
import java.lang.classfile.Signature;
import java.lang.classfile.TypeKind;
import java.lang.classfile.attribute.RuntimeVisibleAnnotationsAttribute;
import java.lang.classfile.attribute.SignatureAttribute;
import java.lang.constant.ClassDesc;
import java.lang.constant.MethodTypeDesc;
import java.lang.reflect.Array;
import java.lang.reflect.InvocationTargetException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

import ai.timefold.solver.core.api.domain.entity.PlanningEntity;
import ai.timefold.solver.core.api.domain.solution.PlanningSolution;
import ai.timefold.solver.core.api.score.buildin.hardmediumsoft.HardMediumSoftScore;
import ai.timefold.solver.core.api.score.buildin.hardsoft.HardSoftScore;
import ai.timefold.solver.core.api.score.buildin.simple.SimpleScore;
import ai.timefold.wasm.service.SolverResource;
import ai.timefold.wasm.service.dto.DomainObject;
import ai.timefold.wasm.service.dto.FieldDescriptor;
import ai.timefold.wasm.service.dto.PlanningProblem;
import ai.timefold.wasm.service.dto.annotation.DomainPlanningScore;

import com.dylibso.chicory.runtime.ExportFunction;
import com.dylibso.chicory.runtime.Instance;
import com.dylibso.chicory.runtime.Memory;

public class DomainObjectClassGenerator {
    static final ClassDesc wasmObjectDesc = getDescriptor(WasmObject.class);
    static final ClassDesc allocatorDesc = getDescriptor(Allocator.class);
    static final ClassDesc instanceDesc = getDescriptor(Instance.class);
    static final ClassDesc mapDesc = getDescriptor(Map.class);

    static final ClassDesc booleanDesc = ClassDesc.ofDescriptor("Z");
    static final ClassDesc byteDesc = ClassDesc.ofDescriptor("B");
    public static final ClassDesc intDesc = ClassDesc.ofDescriptor("I");
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

    public static ClassDesc getDescriptor(Class<?> clazz) {
        return ClassDesc.ofInternalName(getInternalName(clazz));
    }

    static ClassDesc getWasmTypeDesc(String wasmType) {
        if (wasmType.endsWith("[]")) {
            return getDescriptor(WasmList.class);
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

    private static void debug(CodeBuilder codeBuilder, ClassDesc typeDesc) {
        codeBuilder.getstatic(getDescriptor(System.class), "out", getDescriptor(PrintStream.class));
        codeBuilder.swap();
        codeBuilder.invokevirtual(getDescriptor(PrintStream.class), "println", MethodTypeDesc.of(voidDesc, typeDesc));
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
                if (type.endsWith("[]")) {
                    codeBuilder.loadConstant(getWasmTypeDesc(type.substring(0, type.length() - 2)));
                    codeBuilder.invokestatic(getDescriptor(WasmList.class), "ofExisting", MethodTypeDesc.of(getDescriptor(WasmList.class), intDesc, getDescriptor(Class.class)));
                    return;
                }
                codeBuilder.invokevirtual(wasmObjectDesc, "readReferenceField",
                        MethodTypeDesc.of(wasmObjectDesc, intDesc));
                codeBuilder.checkcast(getWasmTypeDesc(type));
            }
        }
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

            // No-args constructor
            classBuilder.withMethodBody("<init>", MethodTypeDesc.of(voidDesc), ClassFile.ACC_PUBLIC, codeBuilder -> {
                codeBuilder.aload(0);
                codeBuilder.invokespecial(wasmObjectDesc, "<init>", MethodTypeDesc.of(voidDesc));
                codeBuilder.return_();
            });

            // allocator + instance constructor
            classBuilder.withMethodBody("<init>", MethodTypeDesc.of(voidDesc, allocatorDesc, instanceDesc), ClassFile.ACC_PUBLIC, codeBuilder -> {
                codeBuilder.aload(0);
                codeBuilder.aload(1);
                codeBuilder.aload(2);
                codeBuilder.loadConstant(wasmOffsets.totalSize);
                codeBuilder.invokespecial(wasmObjectDesc, "<init>", MethodTypeDesc.of(voidDesc, allocatorDesc, instanceDesc, intDesc));
                codeBuilder.return_();
            });

            // instance + pointer constructor
            classBuilder.withMethodBody("<init>", MethodTypeDesc.of(voidDesc, instanceDesc, intDesc), ClassFile.ACC_PUBLIC, codeBuilder -> {
                codeBuilder.aload(0);
                codeBuilder.aload(1);
                codeBuilder.iload(2);
                codeBuilder.invokespecial(wasmObjectDesc, "<init>", MethodTypeDesc.of(voidDesc, instanceDesc, intDesc));
                codeBuilder.return_();
            });

            // allocator + instance + string constructor
            if (domainObject.getDomainObjectMapper() != null) {
                var domainObjectMapper = domainObject.getDomainObjectMapper();
                classBuilder.withMethodBody("<init>", MethodTypeDesc.of(voidDesc, allocatorDesc, instanceDesc, stringDesc), ClassFile.ACC_PUBLIC, codeBuilder -> {
                    codeBuilder.aload(0);
                    codeBuilder.aload(2);

                    codeBuilder.aload(2);
                    codeBuilder.loadConstant(domainObjectMapper.stringToInstanceFunction());
                    codeBuilder.invokevirtual(instanceDesc, "export", MethodTypeDesc.of(getDescriptor(ExportFunction.class), stringDesc));

                    // pointer = allocator.allocate(str.getBytes().length + 1);
                    var STRING_LENGTH_LOCAL = 4;
                    var POINTER_LOCAL = 5;
                    codeBuilder.aload(1);
                    codeBuilder.aload(3);
                    codeBuilder.invokevirtual(stringDesc, "getBytes", MethodTypeDesc.of(byteDesc.arrayType()));
                    codeBuilder.arraylength();
                    codeBuilder.storeLocal(TypeKind.INT, STRING_LENGTH_LOCAL);
                    codeBuilder.loadLocal(TypeKind.INT, STRING_LENGTH_LOCAL);
                    codeBuilder.iconst_1();
                    codeBuilder.iadd();
                    codeBuilder.invokevirtual(allocatorDesc, "allocate", MethodTypeDesc.of(intDesc, intDesc));
                    codeBuilder.storeLocal(TypeKind.INT, POINTER_LOCAL);

                    // instance.memory().writeCString(text, pointer);
                    codeBuilder.aload(2);
                    codeBuilder.invokevirtual(instanceDesc, "memory",  MethodTypeDesc.of(getDescriptor(Memory.class)));
                    codeBuilder.loadLocal(TypeKind.INT, POINTER_LOCAL);
                    codeBuilder.aload(3);
                    codeBuilder.invokeinterface(getDescriptor(Memory.class), "writeCString", MethodTypeDesc.of(voidDesc, intDesc, stringDesc));

                    // long[] temp = new long[] {size, pointer};
                    codeBuilder.loadConstant(2);
                    codeBuilder.newarray(TypeKind.LONG);
                    codeBuilder.dup();
                    codeBuilder.dup();

                    codeBuilder.loadConstant(0);
                    codeBuilder.loadLocal(TypeKind.INT, STRING_LENGTH_LOCAL);
                    codeBuilder.i2l();
                    codeBuilder.lastore();

                    codeBuilder.loadConstant(1);
                    codeBuilder.loadLocal(TypeKind.INT, POINTER_LOCAL);
                    codeBuilder.i2l();
                    codeBuilder.lastore();

                    codeBuilder.invokeinterface(getDescriptor(ExportFunction.class), "apply", MethodTypeDesc.of(longDesc.arrayType(), longDesc.arrayType()));
                    codeBuilder.loadConstant(0);
                    codeBuilder.laload();
                    codeBuilder.l2i();

                    codeBuilder.aload(1);
                    codeBuilder.iload(POINTER_LOCAL);
                    codeBuilder.invokevirtual(allocatorDesc, "free", MethodTypeDesc.of(voidDesc, intDesc));

                    codeBuilder.invokespecial(wasmObjectDesc, "<init>", MethodTypeDesc.of(voidDesc, instanceDesc, intDesc));
                    codeBuilder.return_();
                });
            }

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
                            if (field.getValue().getType().endsWith("[]")) {
                                var innerType = getWasmTypeDesc(field.getValue().getType().substring(0, field.getValue().getType().length() - 2));
                                methodBuilder.with(SignatureAttribute.of(MethodSignature.of(
                                        Signature.ClassTypeSig.of(getDescriptor(WasmList.class), Signature.TypeArg.of(
                                                Signature.ClassTypeSig.of(innerType)))
                                )));
                            }
                            methodBuilder.withCode(codeBuilder -> {
                                if (finalIsPlanningScore) {
                                    codeBuilder.aload(0);
                                    codeBuilder.getfield(ClassDesc.of(domainObject.getName()), field.getKey(), typeDesc);
                                    codeBuilder.return_(getTypeKind(field.getValue().getType()));
                                } else {
                                    if (field.getValue().getAccessor() != null) {
                                        readWasmFieldUsingAccessor(field.getValue(), codeBuilder);
                                        codeBuilder.return_(getTypeKind(field.getValue().getType()));
                                    } else {
                                        codeBuilder.new_(getDescriptor(UnsupportedOperationException.class));
                                        codeBuilder.dup();
                                        codeBuilder.invokespecial(getDescriptor(UnsupportedOperationException.class), "<init>", MethodTypeDesc.of(voidDesc));
                                        codeBuilder.athrow();
                                    }
                                }
                            });
                        });

                // Setter
                classBuilder.withMethodBody(getSetterName(field.getKey()),
                        MethodTypeDesc.of(voidDesc, typeDesc), ClassFile.ACC_PUBLIC, codeBuilder -> {
                                if (finalIsPlanningScore) {
                                    codeBuilder.aload(0);
                                    codeBuilder.aload(1);
                                    codeBuilder.putfield(ClassDesc.of(domainObject.getName()), field.getKey(), typeDesc);
                                    codeBuilder.return_();
                                } else {
                                    if (field.getValue().getAccessor() != null) {
                                        writeWasmFieldUsingAccessor(field.getValue(), codeBuilder, valueBuilder -> {
                                            valueBuilder.aload(1);
                                        });
                                        codeBuilder.return_();
                                    }else {
                                        codeBuilder.new_(getDescriptor(UnsupportedOperationException.class));
                                        codeBuilder.dup();
                                        codeBuilder.invokespecial(getDescriptor(UnsupportedOperationException.class), "<init>", MethodTypeDesc.of(voidDesc));
                                        codeBuilder.athrow();
                                    }
                                }
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
                classBuilder.with(RuntimeVisibleAnnotationsAttribute.of(Annotation.of(
                        getDescriptor(PlanningSolution.class),
                        List.of(AnnotationElement.of("solutionCloner", AnnotationValue.of(getDescriptor(WasmSolutionCloner.class)))))));
            }

            // toString
            if (domainObject.getDomainObjectMapper() != null) {
                var domainObjectMapper = domainObject.getDomainObjectMapper();
                classBuilder.withMethodBody("toString", MethodTypeDesc.of(stringDesc), ClassFile.ACC_PUBLIC, codeBuilder -> {
                    codeBuilder.aload(0);
                    codeBuilder.getfield(wasmObjectDesc, "wasmInstance", instanceDesc);
                    codeBuilder.loadConstant(domainObjectMapper.instanceToStringFunction());
                    codeBuilder.invokevirtual(instanceDesc, "export", MethodTypeDesc.of(getDescriptor(ExportFunction.class), stringDesc));

                    // long[] temp = new long[] {pointer};
                    codeBuilder.loadConstant(1);
                    codeBuilder.newarray(TypeKind.LONG);
                    codeBuilder.dup();
                    codeBuilder.loadConstant(0);
                    codeBuilder.aload(0);
                    codeBuilder.getfield(wasmObjectDesc, "memoryPointer", intDesc);
                    codeBuilder.i2l();
                    codeBuilder.lastore();

                    codeBuilder.invokeinterface(getDescriptor(ExportFunction.class), "apply", MethodTypeDesc.of(longDesc.arrayType(), longDesc.arrayType()));

                    codeBuilder.loadConstant(0);
                    codeBuilder.laload();
                    codeBuilder.l2i();
                    codeBuilder.dup();

                    codeBuilder.aload(0);
                    codeBuilder.getfield(wasmObjectDesc, "wasmInstance", instanceDesc);
                    codeBuilder.invokevirtual(instanceDesc, "memory", MethodTypeDesc.of(getDescriptor(Memory.class)));
                    codeBuilder.swap();
                    codeBuilder.invokeinterface(getDescriptor(Memory.class), "readCString", MethodTypeDesc.of(stringDesc, intDesc));

                    codeBuilder.swap();
                    codeBuilder.getstatic(getDescriptor(SolverResource.class), "ALLOCATOR", getDescriptor(ThreadLocal.class));
                    codeBuilder.invokevirtual(getDescriptor(ThreadLocal.class), "get", MethodTypeDesc.of(objectDesc));
                    codeBuilder.checkcast(allocatorDesc);
                    codeBuilder.swap();
                    codeBuilder.invokevirtual(allocatorDesc, "free", MethodTypeDesc.of(voidDesc, intDesc));

                    codeBuilder.return_(TypeKind.REFERENCE);
                });
            }
        });

        SolverResource.GENERATED_CLASS_LOADER.get().addClass(domainObject.getName(), classBytes);
    }

    private static void readWasmFieldUsingAccessor(FieldDescriptor fieldDescriptor,
            CodeBuilder codeBuilder) {
        var getterFunctionName = fieldDescriptor.getAccessor().getterFunctionName();
        codeBuilder.aload(0);
        codeBuilder.getfield(wasmObjectDesc, "wasmInstance", instanceDesc);
        codeBuilder.loadConstant(getterFunctionName);
        codeBuilder.invokevirtual(instanceDesc, "export", MethodTypeDesc.of(getDescriptor(ExportFunction.class), stringDesc));
        codeBuilder.loadConstant(1);
        codeBuilder.newarray(TypeKind.LONG);
        codeBuilder.dup();
        codeBuilder.loadConstant(0);
        codeBuilder.aload(0);
        codeBuilder.getfield(wasmObjectDesc, "memoryPointer", intDesc);
        codeBuilder.i2l();
        codeBuilder.lastore();

        codeBuilder.invokeinterface(getDescriptor(ExportFunction.class), "apply", MethodTypeDesc.of(longDesc.arrayType(), longDesc.arrayType()));
        codeBuilder.loadConstant(0);
        codeBuilder.laload();

        switch (fieldDescriptor.getType()) {
            case "int" -> {
                codeBuilder.l2i();
            }
            case "long" -> {}
            case "float" -> {
                codeBuilder.l2i();
                codeBuilder.invokestatic(getDescriptor(Float.class),
                        "intBitsToFloat",
                        MethodTypeDesc.of(floatDesc, intDesc));
            }
            case "double" -> {
                codeBuilder.invokestatic(getDescriptor(Double.class),
                        "longBitsToDouble",
                        MethodTypeDesc.of(doubleDesc, longDesc));
            }
            default -> {
                codeBuilder.l2i();
                var notNullLabel = codeBuilder.newLabel();
                codeBuilder.dup();
                codeBuilder.loadConstant(0);
                codeBuilder.if_icmpne(notNullLabel);
                codeBuilder.pop();
                codeBuilder.aconst_null();
                codeBuilder.areturn();

                codeBuilder.labelBinding(notNullLabel);
                var domainClassDesc = getWasmTypeDesc(fieldDescriptor.getType());

                if (fieldDescriptor.getType().endsWith("[]")) {
                    codeBuilder.loadConstant(getWasmTypeDesc(fieldDescriptor.getType().substring(0, fieldDescriptor.getType().length() - 2)));
                    codeBuilder.invokestatic(getDescriptor(WasmList.class), "ofExisting", MethodTypeDesc.of(getDescriptor(WasmList.class), intDesc, getDescriptor(Class.class)));
                } else {
                    codeBuilder.aload(0);
                    codeBuilder.getfield(wasmObjectDesc, "wasmInstance", instanceDesc);
                    codeBuilder.swap();

                    codeBuilder.new_(domainClassDesc);
                    codeBuilder.dup_x2();
                    codeBuilder.dup_x2();
                    codeBuilder.pop();
                    codeBuilder.invokespecial(domainClassDesc, "<init>", MethodTypeDesc.of(voidDesc, instanceDesc, intDesc));

                    codeBuilder.aload(0);
                    codeBuilder.getfield(wasmObjectDesc, "wasmInstance", instanceDesc);
                    codeBuilder.swap();

                    codeBuilder.dup();
                    codeBuilder.getfield(wasmObjectDesc, "memoryPointer", intDesc);
                    codeBuilder.swap();

                    codeBuilder.invokestatic(wasmObjectDesc, "ofExistingOrDefault", MethodTypeDesc.of(wasmObjectDesc,
                            instanceDesc, intDesc, wasmObjectDesc));
                    codeBuilder.checkcast(domainClassDesc);
                }
            }
        }
    }

    private void writeWasmFieldUsingAccessor(FieldDescriptor fieldDescriptor, CodeBuilder codeBuilder, Consumer<CodeBuilder> valueBuilder) {
        var setterFunctionName = fieldDescriptor.getAccessor().setterFunctionName();
        codeBuilder.aload(0);
        codeBuilder.getfield(wasmObjectDesc, "wasmInstance", instanceDesc);
        codeBuilder.loadConstant(setterFunctionName);
        codeBuilder.invokevirtual(instanceDesc, "export", MethodTypeDesc.of(getDescriptor(ExportFunction.class), stringDesc));

        codeBuilder.loadConstant(2);
        codeBuilder.newarray(TypeKind.LONG);
        codeBuilder.dup();
        codeBuilder.dup();
        codeBuilder.loadConstant(0);
        codeBuilder.aload(0);
        codeBuilder.getfield(wasmObjectDesc, "memoryPointer", intDesc);
        codeBuilder.i2l();
        codeBuilder.lastore();

        codeBuilder.loadConstant(1);
        valueBuilder.accept(codeBuilder);
        switch (fieldDescriptor.getType()) {
            case "int" -> {
                codeBuilder.i2l();
            }
            case "long" -> {}
            case "float" -> {
                codeBuilder.invokestatic(getDescriptor(Float.class), "floatToRawIntBits",
                        MethodTypeDesc.of(intDesc, floatDesc));
                codeBuilder.i2l();
            }
            case "double" -> {
                codeBuilder.invokestatic(getDescriptor(Double.class), "doubleToLongBits",
                        MethodTypeDesc.of(longDesc, doubleDesc));
            }
            default -> {
                var isNotNullLabel = codeBuilder.newLabel();
                var doneLabel = codeBuilder.newLabel();
                codeBuilder.dup();
                codeBuilder.aconst_null();
                codeBuilder.if_acmpne(isNotNullLabel);

                codeBuilder.pop();
                codeBuilder.loadConstant(0L);
                codeBuilder.goto_(doneLabel);

                codeBuilder.labelBinding(isNotNullLabel);

                if (fieldDescriptor.getType().endsWith("[]")) {
                    codeBuilder.invokevirtual(getDescriptor(WasmList.class), "getMemoryAddress", MethodTypeDesc.of(intDesc));
                } else {
                    codeBuilder.getfield(wasmObjectDesc, "memoryPointer", intDesc);
                }
                codeBuilder.i2l();

                codeBuilder.labelBinding(doneLabel);
            }
        }
        codeBuilder.lastore();

        codeBuilder.invokeinterface(getDescriptor(ExportFunction.class), "apply", MethodTypeDesc.of(longDesc.arrayType(), longDesc.arrayType()));
    }
}
