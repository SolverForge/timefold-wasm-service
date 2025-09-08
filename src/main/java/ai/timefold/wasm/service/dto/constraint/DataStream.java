package ai.timefold.wasm.service.dto.constraint;

import java.util.function.BiFunction;
import java.util.function.BiPredicate;
import java.util.function.Function;
import java.util.function.Predicate;

import ai.timefold.solver.core.api.function.PentaFunction;
import ai.timefold.solver.core.api.function.PentaPredicate;
import ai.timefold.solver.core.api.function.QuadFunction;
import ai.timefold.solver.core.api.function.QuadPredicate;
import ai.timefold.solver.core.api.function.TriFunction;
import ai.timefold.solver.core.api.function.TriPredicate;
import ai.timefold.solver.core.api.score.stream.ConstraintBuilder;
import ai.timefold.solver.core.api.score.stream.ConstraintStream;
import ai.timefold.solver.core.api.score.stream.bi.BiConstraintBuilder;
import ai.timefold.solver.core.api.score.stream.bi.BiConstraintStream;
import ai.timefold.solver.core.api.score.stream.quad.QuadConstraintBuilder;
import ai.timefold.solver.core.api.score.stream.quad.QuadConstraintStream;
import ai.timefold.solver.core.api.score.stream.tri.TriConstraintBuilder;
import ai.timefold.solver.core.api.score.stream.tri.TriConstraintStream;
import ai.timefold.solver.core.api.score.stream.uni.UniConstraintBuilder;
import ai.timefold.solver.core.api.score.stream.uni.UniConstraintStream;

public final class DataStream {
    int tupleSize;

    public DataStream() {
        tupleSize = 1;
    }

    public int getTupleSize() {
        return tupleSize;
    }

    public void incrementSize() {
        if (tupleSize >= 4) {
            throw new IllegalArgumentException("Stream exceeds bounds of ConstraintStream");
        }
        tupleSize++;
    }

    public void setSize(int tupleSize) {
        if (tupleSize > 4) {
            throw new IllegalArgumentException("Stream exceeds bounds of ConstraintStream");
        }
        this.tupleSize = tupleSize;
    }

    public Class<? extends ConstraintStream> getConstraintStreamClass() {
        return getConstraintStreamClassWithExtras(0);
    }

    public Class<? extends ConstraintStream> getConstraintStreamClassWithExtras(int count) {
        return switch (tupleSize + count) {
            case 1 -> UniConstraintStream.class;
            case 2 -> BiConstraintStream.class;
            case 3 -> TriConstraintStream.class;
            case 4 -> QuadConstraintStream.class;
            default -> throw new IllegalStateException("Impossible state: tupleSize (%d) must be between 1 and 4".formatted(tupleSize));
        };
    }

    public Class<? extends ConstraintBuilder> getConstraintBuilderClass() {
        return switch (tupleSize) {
            case 1 -> UniConstraintBuilder.class;
            case 2 -> BiConstraintBuilder.class;
            case 3 -> TriConstraintBuilder.class;
            case 4 -> QuadConstraintBuilder.class;
            default -> throw new IllegalStateException("Impossible state: tupleSize (%d) must be between 1 and 4".formatted(tupleSize));
        };
    }

    public Class<?> getPredicateClass() {
        return getPredicateClassWithExtras(0);
    }

    public Class<?> getPredicateClassWithExtras(int count) {
        return switch (tupleSize + count) {
            case 1 -> Predicate.class;
            case 2 -> BiPredicate.class;
            case 3 -> TriPredicate.class;
            case 4 -> QuadPredicate.class;
            case 5 -> PentaPredicate.class;
            default -> throw new IllegalStateException("Impossible state: tupleSize (%d) must be between 1 and 5".formatted(tupleSize + count));
        };
    }

    public Class<?> getFunctionClass() {
        return getFunctionClassWithExtras(0);
    }

    public Class<?> getFunctionClassWithExtras(int count) {
        return switch (tupleSize) {
            case 1 -> Function.class;
            case 2 -> BiFunction.class;
            case 3 -> TriFunction.class;
            case 4 -> QuadFunction.class;
            case 5 -> PentaFunction.class;
            default -> throw new IllegalStateException("Impossible state: tupleSize (%d) must be between 1 and 5".formatted(tupleSize + count));
        };
    }
}
