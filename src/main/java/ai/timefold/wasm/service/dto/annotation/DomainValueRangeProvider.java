package ai.timefold.wasm.service.dto.annotation;

import java.lang.annotation.Annotation;

import ai.timefold.solver.core.api.domain.valuerange.ValueRangeProvider;

public final class DomainValueRangeProvider implements PlanningAnnotation {
    @Override
    public Class<? extends Annotation> annotationClass() {
        return ValueRangeProvider.class;
    }
}
