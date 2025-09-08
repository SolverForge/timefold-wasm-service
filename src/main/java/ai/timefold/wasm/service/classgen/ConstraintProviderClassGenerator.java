package ai.timefold.wasm.service.classgen;

import java.lang.classfile.ClassBuilder;
import java.lang.classfile.ClassFile;
import java.lang.classfile.CodeBuilder;
import java.lang.classfile.TypeKind;
import java.lang.constant.ClassDesc;
import java.lang.constant.MethodTypeDesc;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Predicate;

import ai.timefold.solver.core.api.score.Score;
import ai.timefold.solver.core.api.score.buildin.hardmediumsoft.HardMediumSoftScore;
import ai.timefold.solver.core.api.score.buildin.hardsoft.HardSoftScore;
import ai.timefold.solver.core.api.score.buildin.simple.SimpleScore;
import ai.timefold.solver.core.api.score.stream.Constraint;
import ai.timefold.solver.core.api.score.stream.ConstraintFactory;
import ai.timefold.solver.core.api.score.stream.ConstraintProvider;
import ai.timefold.solver.core.api.score.stream.uni.UniConstraintBuilder;
import ai.timefold.solver.core.api.score.stream.uni.UniConstraintStream;
import ai.timefold.wasm.service.dto.PlanningProblem;
import ai.timefold.wasm.service.dto.WasmConstraint;
import ai.timefold.wasm.service.dto.annotation.DomainPlanningScore;
import ai.timefold.wasm.service.dto.constraint.FilterComponent;
import ai.timefold.wasm.service.dto.constraint.ForEachComponent;

import static ai.timefold.wasm.service.classgen.DomainObjectClassGenerator.*;

import com.dylibso.chicory.runtime.Instance;

public class ConstraintProviderClassGenerator {
    private final DomainObjectClassGenerator domainObjectClassGenerator;
    private final Instance wasmInstance;
    private int functionCount = 0;
    private List<Consumer<Class<?>>> classInitializerList = new ArrayList<>();

    static final ClassDesc constraintProviderDesc = getDescriptor(ConstraintProvider.class);
    static final ClassDesc constraintFactoryDesc = getDescriptor(ConstraintFactory.class);

    // TODO: Support multiple cardinality
    static final ClassDesc streamDesc = getDescriptor(UniConstraintStream.class);
    static final ClassDesc constraintBuilderDesc = getDescriptor(UniConstraintBuilder.class);

    static final ClassDesc constraintDesc = getDescriptor(Constraint.class);

    public ConstraintProviderClassGenerator(DomainObjectClassGenerator domainObjectClassGenerator, Instance wasmInstance) {
        this.domainObjectClassGenerator = domainObjectClassGenerator;
        this.wasmInstance = wasmInstance;
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
        var classBytes = classFile.build(generatedClassDesc, classBuilder -> {
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
                        codeBuilder.loadConstant(planningProblem.getPenaltyConstraintList().size() + planningProblem.getRewardConstraintList().size());
                        codeBuilder.anewarray(getDescriptor(Constraint.class));
                        var index = 0;
                        for (var penaltyConstraint : planningProblem.getPenaltyConstraintList()) {
                            codeBuilder.dup();
                            codeBuilder.dup();
                            codeBuilder.loadConstant(index);

                            generateConstraintBody(generatedClassDesc, classBuilder, codeBuilder, penaltyConstraint);
                            codeBuilder.loadConstant(penaltyConstraint.getWeight());
                            codeBuilder.invokestatic(scoreDesc, "parseScore", MethodTypeDesc.of(scoreDesc, stringDesc));
                            codeBuilder.invokeinterface(streamDesc, "penalize", MethodTypeDesc.of(constraintBuilderDesc, getDescriptor(
                                    Score.class)));
                            codeBuilder.loadConstant(penaltyConstraint.getName());
                            codeBuilder.invokeinterface(constraintBuilderDesc, "asConstraint", MethodTypeDesc.of(constraintDesc, stringDesc));

                            codeBuilder.aastore();
                            index++;
                        }
                        for (var rewardConstraint : planningProblem.getRewardConstraintList()) {
                            codeBuilder.dup();
                            codeBuilder.dup();
                            codeBuilder.loadConstant(index);

                            generateConstraintBody(generatedClassDesc, classBuilder, codeBuilder, rewardConstraint);
                            codeBuilder.loadConstant(rewardConstraint.getWeight());
                            codeBuilder.invokestatic(scoreDesc, "parseScore", MethodTypeDesc.of(scoreDesc, stringDesc));
                            codeBuilder.invokeinterface(streamDesc, "reward", MethodTypeDesc.of(constraintBuilderDesc, getDescriptor(
                                    Score.class)));
                            codeBuilder.loadConstant(rewardConstraint.getName());
                            codeBuilder.invokeinterface(constraintBuilderDesc, "asConstraint", MethodTypeDesc.of(constraintDesc, stringDesc));

                            codeBuilder.aastore();
                            index++;
                        }
                        codeBuilder.return_(TypeKind.REFERENCE);
                    });
        });
        var out = domainObjectClassGenerator.defineConstraintProviderClass(constraintProviderClassName, classBytes);
        for (var initializer : classInitializerList) {
            initializer.accept(out);
        }
        classInitializerList.clear();
        return (Class<? extends ConstraintProvider>) out;
    }

    private void generateConstraintBody(ClassDesc generatedClass, ClassBuilder classBuilder, CodeBuilder codeBuilder, WasmConstraint wasmConstraint) {
        for (var steamComponent : wasmConstraint.getStreamComponentList()) {
            switch (steamComponent) {
                case ForEachComponent forEachComponent -> {
                    codeBuilder.aload(1);
                    codeBuilder.loadConstant(getDescriptor(domainObjectClassGenerator.getClassForDomainClassName(
                            forEachComponent.getClassName())));
                    codeBuilder.invokeinterface(constraintFactoryDesc, "forEach", MethodTypeDesc.of(streamDesc, getDescriptor(Class.class)));
                }
                case FilterComponent filterComponent -> {
                    var predicate = filterComponent.getFilter().asPredicate(wasmInstance);
                    var predicateFieldName = "predicate" + functionCount;
                    functionCount++;
                    classBuilder.withField(predicateFieldName, getDescriptor(Predicate.class), ClassFile.ACC_PUBLIC | ClassFile.ACC_STATIC);
                    classInitializerList.add(clazz -> {
                        try {
                            clazz.getField(predicateFieldName).set(null, predicate);
                        } catch (IllegalAccessException | NoSuchFieldException e) {
                            throw new RuntimeException(e);
                        }
                    });
                    codeBuilder.getstatic(generatedClass, predicateFieldName, getDescriptor(Predicate.class));
                    codeBuilder.invokeinterface(streamDesc, "filter", MethodTypeDesc.of(streamDesc, getDescriptor(Predicate.class)));
                }
            }
        }
    }
}
