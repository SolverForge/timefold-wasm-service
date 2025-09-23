package ai.timefold.wasm.service.dto.annotation;

import java.lang.annotation.Annotation;
import java.lang.classfile.AnnotationElement;
import java.util.Collections;
import java.util.List;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.fasterxml.jackson.databind.annotation.JsonTypeIdResolver;

@JsonTypeInfo(use=JsonTypeInfo.Id.CUSTOM, property="annotation", visible=true)
@JsonTypeIdResolver(AnnotationTypeIdResolver.class)
public sealed interface PlanningAnnotation
        permits DomainPlanningEntityCollectionProperty, DomainPlanningId, DomainPlanningScore, DomainPlanningVariable,
        DomainProblemFactCollectionProperty, DomainValueRangeProvider {
    @JsonIgnore
    Class<? extends Annotation> annotationClass();
    default String annotation() {
        return annotationClass().getSimpleName();
    }

    @JsonIgnore
    default boolean definesPlanningEntity() {
        return false;
    }

    @JsonIgnore
    default boolean definesPlanningSolution() {
        return false;
    }

    @JsonIgnore
    default List<AnnotationElement> getAnnotationElements() {
        return Collections.emptyList();
    }
}
