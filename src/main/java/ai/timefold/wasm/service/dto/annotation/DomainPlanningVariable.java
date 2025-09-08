package ai.timefold.wasm.service.dto.annotation;

import java.lang.annotation.Annotation;
import java.lang.classfile.AnnotationElement;
import java.lang.classfile.AnnotationValue;
import java.util.List;

import ai.timefold.solver.core.api.domain.variable.PlanningVariable;

import com.fasterxml.jackson.annotation.JsonCreator;

public final class DomainPlanningVariable implements PlanningAnnotation {
    boolean allowsUnassigned;

    @JsonCreator
    public DomainPlanningVariable() {
        this.allowsUnassigned = true;
    }

    @JsonCreator
    public DomainPlanningVariable(boolean allowsUnassigned) {
        this.allowsUnassigned = allowsUnassigned;
    }

    @Override
    public Class<? extends Annotation> annotationClass() {
        return PlanningVariable.class;
    }

    @Override
    public boolean definesPlanningEntity() {
        return true;
    }

    @Override
    public List<AnnotationElement> getAnnotationElements() {
        return List.of(
                AnnotationElement.of("allowsUnassigned", AnnotationValue.of(allowsUnassigned))
        );
    }
}
