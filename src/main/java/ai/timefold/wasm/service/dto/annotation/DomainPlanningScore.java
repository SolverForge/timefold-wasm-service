package ai.timefold.wasm.service.dto.annotation;

import java.lang.annotation.Annotation;

import ai.timefold.solver.core.api.domain.solution.PlanningScore;

public final class DomainPlanningScore implements PlanningAnnotation {
    @Override
    public Class<? extends Annotation> annotationClass() {
        return PlanningScore.class;
    }

    @Override
    public boolean definesPlanningSolution() {
        return true;
    }
}
