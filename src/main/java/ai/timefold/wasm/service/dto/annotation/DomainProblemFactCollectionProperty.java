package ai.timefold.wasm.service.dto.annotation;

import java.lang.annotation.Annotation;

import ai.timefold.solver.core.api.domain.solution.ProblemFactCollectionProperty;

public final class DomainProblemFactCollectionProperty implements PlanningAnnotation {
    @Override
    public Class<? extends Annotation> annotationClass() {
        return ProblemFactCollectionProperty.class;
    }

    @Override
    public boolean definesPlanningSolution() {
        return true;
    }
}
