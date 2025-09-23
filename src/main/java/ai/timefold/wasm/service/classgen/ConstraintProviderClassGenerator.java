package ai.timefold.wasm.service.classgen;

import java.lang.classfile.ClassBuilder;
import java.lang.classfile.ClassFile;
import java.lang.classfile.CodeBuilder;
import java.lang.classfile.TypeKind;
import java.lang.classfile.constantpool.ConstantPoolBuilder;
import java.lang.constant.ClassDesc;
import java.lang.constant.MethodTypeDesc;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Function;

import ai.timefold.solver.core.api.score.Score;
import ai.timefold.solver.core.api.score.buildin.hardmediumsoft.HardMediumSoftScore;
import ai.timefold.solver.core.api.score.buildin.hardsoft.HardSoftScore;
import ai.timefold.solver.core.api.score.buildin.simple.SimpleScore;
import ai.timefold.solver.core.api.score.stream.Constraint;
import ai.timefold.solver.core.api.score.stream.ConstraintFactory;
import ai.timefold.solver.core.api.score.stream.ConstraintProvider;
import ai.timefold.solver.core.api.score.stream.bi.BiJoiner;
import ai.timefold.solver.core.api.score.stream.uni.UniConstraintStream;
import ai.timefold.wasm.service.SolverResource;
import ai.timefold.wasm.service.dto.PlanningProblem;
import ai.timefold.wasm.service.dto.WasmConstraint;
import ai.timefold.wasm.service.dto.WasmFunction;
import ai.timefold.wasm.service.dto.annotation.DomainPlanningScore;
import ai.timefold.wasm.service.dto.constraint.ComplementComponent;
import ai.timefold.wasm.service.dto.constraint.DataStream;
import ai.timefold.wasm.service.dto.constraint.ExpandComponent;
import ai.timefold.wasm.service.dto.constraint.FilterComponent;
import ai.timefold.wasm.service.dto.constraint.FlattenLastComponent;
import ai.timefold.wasm.service.dto.constraint.ForEachComponent;
import ai.timefold.wasm.service.dto.constraint.ForEachUniquePairComponent;
import ai.timefold.wasm.service.dto.constraint.GroupByComponent;
import ai.timefold.wasm.service.dto.constraint.IfExistsComponent;
import ai.timefold.wasm.service.dto.constraint.IfNotExistsComponent;
import ai.timefold.wasm.service.dto.constraint.JoinComponent;
import ai.timefold.wasm.service.dto.constraint.MapComponent;
import ai.timefold.wasm.service.dto.constraint.PenalizeComponent;
import ai.timefold.wasm.service.dto.constraint.RewardComponent;
import ai.timefold.wasm.service.dto.constraint.joiner.DataJoiner;

import static ai.timefold.wasm.service.classgen.DomainObjectClassGenerator.*;

public class ConstraintProviderClassGenerator {
    private int functionCount = 0;
    private final List<Consumer<Class<?>>> classInitializerList = new ArrayList<>();
    private final ConstantPoolBuilder constantPool;

    static final ClassDesc constraintProviderDesc = getDescriptor(ConstraintProvider.class);
    static final ClassDesc constraintFactoryDesc = getDescriptor(ConstraintFactory.class);

    static final ClassDesc constraintDesc = getDescriptor(Constraint.class);

    public ConstraintProviderClassGenerator() {
        constantPool = ConstantPoolBuilder.of();
    }

    private static Class<?> getScoreType(PlanningProblem planningProblem) {
        var solutionDomainObject = planningProblem.getDomainObjectMap().get(planningProblem.getSolutionClass());
        for (var field : solutionDomainObject.getFieldDescriptorMap().entrySet()) {
            if (field.getValue().getAnnotations() != null) {
                for (var annotation : field.getValue().getAnnotations()) {
                    if (annotation instanceof DomainPlanningScore) {
                        return switch (field.getValue().getType()) {
                            case "SimpleScore" -> SimpleScore.class;
                            case "HardSoftScore" -> HardSoftScore.class;
                            case "HardMediumSoftScore" -> HardMediumSoftScore.class;
                            default -> {
                                throw new IllegalArgumentException("Unknown score type: " + field.getValue().getType());
                            }
                        };
                    }
                }
            }
        }
        throw new IllegalStateException("Impossible state: solution class does not have a PlanningScore annotation");
    }

    public Class<? extends ConstraintProvider> defineConstraintProviderClass(PlanningProblem planningProblem) {
        var constraintProviderClassName = "MyConstraintProvider";
        var classFile = ClassFile.of();
        var scoreType = getScoreType(planningProblem);
        var scoreDesc = getDescriptor(scoreType);
        var generatedClassDesc = ClassDesc.of(constraintProviderClassName);
        var classBytes = classFile.build(constantPool.classEntry(generatedClassDesc), constantPool, classBuilder -> {
            classBuilder.withInterfaceSymbols(constraintProviderDesc);

            // Constructor
            classBuilder.withMethodBody("<init>", MethodTypeDesc.of(voidDesc), ClassFile.ACC_PUBLIC, codeBuilder -> {
                codeBuilder.aload(0);
                codeBuilder.invokespecial(objectDesc, "<init>", MethodTypeDesc.of(voidDesc));
                codeBuilder.return_();
            });

            // Constraints
            classBuilder.withMethodBody("defineConstraints",
                    MethodTypeDesc.of(getDescriptor(Constraint.class).arrayType(), getDescriptor(ConstraintFactory.class)), ClassFile.ACC_PUBLIC,
                    codeBuilder -> {
                        codeBuilder.loadConstant(planningProblem.getConstraintList().size());
                        codeBuilder.anewarray(getDescriptor(Constraint.class));
                        var index = 0;
                        codeBuilder.dup();
                        for (var constraint : planningProblem.getConstraintList()) {
                            codeBuilder.dup();
                            codeBuilder.loadConstant(index);

                            var dataStream = generateConstraintBody(generatedClassDesc, scoreDesc, classBuilder, codeBuilder, constraint);
                            var constraintBuilderDesc = getDescriptor(dataStream.getConstraintBuilderClass());
                            codeBuilder.loadConstant(constraint.getName());
                            codeBuilder.invokeinterface(constraintBuilderDesc, "asConstraint", MethodTypeDesc.of(constraintDesc, stringDesc));

                            codeBuilder.aastore();
                            index++;
                        }
                        codeBuilder.return_(TypeKind.REFERENCE);
                    });
        });
        SolverResource.GENERATED_CLASS_LOADER.get().addClass(constraintProviderClassName, classBytes);
        var out = SolverResource.GENERATED_CLASS_LOADER.get().getClassForDomainClassName(constraintProviderClassName);
        for (var initializer : classInitializerList) {
            initializer.accept(out);
        }
        classInitializerList.clear();
        return (Class<? extends ConstraintProvider>) out;
    }

    public ConstantPoolBuilder getConstantPool() {
        return constantPool;
    }

    public ClassDesc loadFunction(DataStreamInfo dataStreamInfo, FunctionType functionType,
            WasmFunction function) {
        return loadFunctionWithExtras(dataStreamInfo, 0, functionType, function);
    }

    public ClassDesc loadFunctionWithExtras(DataStreamInfo dataStreamInfo, int extras, FunctionType functionType,
            WasmFunction function) {
        return loadFunctionOfSize(dataStreamInfo,
                dataStreamInfo.dataStream().getTupleSize() + extras,
                functionType, function);
    }


    public ClassDesc loadFunctionOfSize(DataStreamInfo dataStreamInfo, int argCount, FunctionType functionType,
            WasmFunction function) {
        var functionInstance = functionType.getFunction(argCount, function);
        var functionFieldName = "$function" + functionCount;
        var functionClassDesc = functionType.getClassDescriptor(dataStreamInfo.dataStream(), argCount);
        functionCount++;
        dataStreamInfo.classBuilder().withField(functionFieldName, functionClassDesc, ClassFile.ACC_PUBLIC | ClassFile.ACC_STATIC);
        classInitializerList.add(clazz -> {
            try {
                clazz.getField(functionFieldName).set(null, functionInstance);
            } catch (IllegalAccessException | NoSuchFieldException e) {
                throw new RuntimeException(e);
            } catch (IllegalArgumentException e) {
                throw new RuntimeException("Type mismatch: expected %s but got %s".formatted(functionClassDesc, functionInstance.getClass().getInterfaces()[0]), e);
            }
        });
        dataStreamInfo.codeBuilder().getstatic(dataStreamInfo.generatedClassDesc(), functionFieldName, functionClassDesc);
        return functionClassDesc;
    }

    private DataStream generateConstraintBody(ClassDesc generatedClass,
            ClassDesc scoreDesc,
            ClassBuilder classBuilder,
            CodeBuilder codeBuilder,
            WasmConstraint wasmConstraint) {
        DataStream dataStream = new DataStream();
        var dataStreamInfo = new DataStreamInfo(this, classBuilder, codeBuilder, dataStream, generatedClass);
        var classLoader = SolverResource.GENERATED_CLASS_LOADER.get();
        for (var streamComponent : wasmConstraint.getStreamComponentList()) {
            var streamDesc = getDescriptor(dataStream.getConstraintStreamClass());

            switch (streamComponent) {
                case ForEachComponent forEachComponent -> {
                    codeBuilder.aload(1);
                    codeBuilder.loadConstant(getDescriptor(classLoader.getClassForDomainClassName(
                            forEachComponent.className())));
                    codeBuilder.invokeinterface(constraintFactoryDesc, "forEach", MethodTypeDesc.of(streamDesc, getDescriptor(Class.class)));
                }
                case ForEachUniquePairComponent forEachUniquePairComponent -> {
                    var className = getDescriptor(classLoader.getClassForDomainClassName(forEachUniquePairComponent.className()));
                    var joiners = forEachUniquePairComponent.getJoiners();

                    codeBuilder.aload(1);
                    codeBuilder.loadConstant(className);
                    codeBuilder.loadConstant(joiners.size());
                    codeBuilder.anewarray(getDescriptor(dataStream.getJoinerClass()));
                    for (var i = 0; i < joiners.size(); i++) {
                        codeBuilder.dup();
                        codeBuilder.loadConstant(i);
                        joiners.get(i).loadJoinerInstance(dataStreamInfo);
                        codeBuilder.aastore();
                    }
                    codeBuilder.invokeinterface(constraintFactoryDesc, "forEachUniquePair", MethodTypeDesc.of(
                            getDescriptor(dataStream.getConstraintStreamClassWithExtras(1)), getDescriptor(Class.class),
                            getDescriptor(BiJoiner.class).arrayType()));
                }
                case FilterComponent filterComponent -> {
                    var predicateDesc = loadFunction(dataStreamInfo, FunctionType.PREDICATE, filterComponent.filter());
                    codeBuilder.invokeinterface(streamDesc, "filter", MethodTypeDesc.of(streamDesc, predicateDesc));
                }
                case JoinComponent _, IfExistsComponent _, IfNotExistsComponent _ -> {
                    var className = switch (streamComponent) {
                        case JoinComponent joinComponent -> joinComponent.className();
                        case IfExistsComponent ifExistsComponent -> ifExistsComponent.className();
                        case IfNotExistsComponent ifNotExistsComponent -> ifNotExistsComponent.className();
                        default -> throw new IllegalStateException("Impossible state");
                    };
                    var joiners = switch (streamComponent) {
                        case JoinComponent joinComponent -> joinComponent.getJoiners();
                        case IfExistsComponent ifExistsComponent -> ifExistsComponent.getJoiners();
                        case IfNotExistsComponent ifNotExistsComponent -> ifNotExistsComponent.getJoiners();
                        default -> throw new IllegalStateException("Impossible state");
                    };
                    codeBuilder.loadConstant(getDescriptor(classLoader.getClassForDomainClassName(className)));
                    codeBuilder.loadConstant(joiners.size());
                    codeBuilder.anewarray(getDescriptor(dataStream.getJoinerClass()));
                    for (var i = 0; i < joiners.size(); i++) {
                        codeBuilder.dup();
                        codeBuilder.loadConstant(i);
                        joiners.get(i).loadJoinerInstance(dataStreamInfo);
                        codeBuilder.aastore();
                    }
                    codeBuilder.invokeinterface(streamDesc, streamComponent.kind(), MethodTypeDesc.of(
                            getDescriptor(switch (streamComponent) {
                                case JoinComponent _ -> dataStream.getConstraintStreamClassWithExtras(1);
                                default -> dataStream.getConstraintStreamClass();
                            }),
                            getDescriptor(Class.class),
                            getDescriptor(dataStream.getJoinerClass()).arrayType()));
                }
                case GroupByComponent groupByComponent -> {
                    var keyFunctionDesc = getDescriptor(dataStream.getFunctionClass());
                    var constraintCollectorDesc = getDescriptor(dataStream.getConstraintCollectorClass());
                    var methodParameterDescriptors = new ClassDesc[groupByComponent.getKeys().size() + groupByComponent.getAggregators().size()];
                    for (int i = 0; i < groupByComponent.getKeys().size(); i++) {
                        methodParameterDescriptors[i] = keyFunctionDesc;
                        loadFunction(dataStreamInfo, FunctionType.MAPPER, groupByComponent.getKeys().get(i));
                    }
                    for (int i = 0; i < groupByComponent.getAggregators().size(); i++) {
                        methodParameterDescriptors[i + groupByComponent.getKeys().size()] = constraintCollectorDesc;
                        groupByComponent.getAggregators().get(i).loadAggregatorInstance(dataStreamInfo);
                    }
                    var methodReturnDesc = getDescriptor(dataStream.getConstraintStreamClassWithExtras(methodParameterDescriptors.length - dataStream.getTupleSize()));

                    codeBuilder.invokeinterface(streamDesc, "groupBy", MethodTypeDesc.of(methodReturnDesc, methodParameterDescriptors));
                }
                case FlattenLastComponent flattenLastComponent -> {
                    if (flattenLastComponent.map() == null) {
                        codeBuilder.getstatic(wasmObjectDesc, "TO_LIST", getDescriptor(Function.class));
                    } else {
                        loadFunctionOfSize(dataStreamInfo, 1, FunctionType.LIST_MAPPER, flattenLastComponent.map());
                    }
                    codeBuilder.invokeinterface(streamDesc, "flattenLast", MethodTypeDesc.of(streamDesc, getDescriptor(Function.class)));
                }
                case MapComponent _, ExpandComponent _ -> {
                    var funcDesc = getDescriptor(dataStream.getFunctionClass());
                    var mappers = switch (streamComponent) {
                        case MapComponent mapComponent -> mapComponent.mappers();
                        case ExpandComponent expandComponent -> expandComponent.mappers();
                        default -> throw new IllegalStateException("Impossible state");
                    };
                    var streamReturnTypeDesc = getDescriptor(switch (streamComponent) {
                        case  MapComponent _ -> dataStream.getConstraintStreamClassOfSize(mappers.size());
                        case  ExpandComponent _ -> dataStream.getConstraintStreamClassWithExtras(mappers.size());
                        default -> throw new IllegalStateException("Impossible state");
                    });
                    var methodParamDescs = new ClassDesc[mappers.size()];
                    Arrays.fill(methodParamDescs, funcDesc);
                    for (var mapper : mappers) {
                        loadFunction(dataStreamInfo, FunctionType.MAPPER, mapper);
                    }
                    codeBuilder.invokeinterface(streamDesc, streamComponent.kind(),
                            MethodTypeDesc.of(streamReturnTypeDesc, methodParamDescs));
                }
                case ComplementComponent complementComponent -> {
                    codeBuilder.loadConstant(getDescriptor(classLoader.getClassForDomainClassName(
                            complementComponent.className())));
                    complementComponent.loadPadding(dataStreamInfo);
                    var methodParamDescs = new ClassDesc[dataStream.getTupleSize()];
                    Arrays.fill(methodParamDescs, getDescriptor(Function.class));
                    methodParamDescs[0] = getDescriptor(Class.class);
                    codeBuilder.invokeinterface(streamDesc, "complement",
                            MethodTypeDesc.of(streamDesc, methodParamDescs));
                }
                case PenalizeComponent penalizeComponent -> {
                    codeBuilder.loadConstant(penalizeComponent.weight());
                    codeBuilder.invokestatic(scoreDesc, "parseScore", MethodTypeDesc.of(scoreDesc, stringDesc));
                    if (penalizeComponent.scaleBy() != null) {
                        var functionDesc = loadFunction(dataStreamInfo, FunctionType.TO_INT, penalizeComponent.scaleBy());
                        codeBuilder.invokeinterface(streamDesc, "penalize", MethodTypeDesc.of(
                                getDescriptor(dataStream.getConstraintBuilderClass()), getDescriptor(Score.class), functionDesc));
                    } else {
                        codeBuilder.invokeinterface(streamDesc, "penalize", MethodTypeDesc.of(
                                getDescriptor(dataStream.getConstraintBuilderClass()), getDescriptor(Score.class)));
                    }
                }
                case RewardComponent rewardComponent -> {
                    codeBuilder.loadConstant(rewardComponent.weight());
                    codeBuilder.invokestatic(scoreDesc, "parseScore", MethodTypeDesc.of(scoreDesc, stringDesc));
                    if (rewardComponent.scaleBy() != null) {
                        var functionDesc = loadFunction(dataStreamInfo, FunctionType.TO_INT, rewardComponent.scaleBy());
                        codeBuilder.invokeinterface(streamDesc, "reward", MethodTypeDesc.of(
                                getDescriptor(dataStream.getConstraintBuilderClass()), getDescriptor(Score.class), functionDesc));
                    } else {
                        codeBuilder.invokeinterface(streamDesc, "reward", MethodTypeDesc.of(
                                getDescriptor(dataStream.getConstraintBuilderClass()), getDescriptor(Score.class)));
                    }
                }
            }

            streamComponent.applyToDataStream(dataStream);
        }
        return dataStream;
    }
}
