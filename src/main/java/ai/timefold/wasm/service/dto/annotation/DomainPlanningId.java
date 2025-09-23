package ai.timefold.wasm.service.dto.annotation;

import java.lang.annotation.Annotation;

import ai.timefold.solver.core.api.domain.lookup.PlanningId;
import ai.timefold.solver.core.api.domain.solution.PlanningScore;

public final class DomainPlanningId implements PlanningAnnotation {
    @Override
    public Class<? extends Annotation> annotationClass() {
        return PlanningId.class;
    }
}
