package ai.timefold.wasm.service.dto.constraint.groupby;

import java.util.function.ToIntFunction;

import ai.timefold.solver.core.api.score.stream.common.ConnectedRange;
import ai.timefold.solver.core.api.score.stream.common.Sequence;

public enum ConnectedRangeField {
    COUNT(ConnectedRange::getContainedRangeCount),
    LENGTH(ConnectedRange::getLength),
    START(ConnectedRange::getStart),
    END(ConnectedRange::getEnd),
    MIN_OVERLAP(ConnectedRange::getMaximumOverlap),
    MAX_OVERLAP(ConnectedRange::getMaximumOverlap);

    private final ToIntFunction<ConnectedRange<?, Integer, Integer>> fieldExtractor;

    ConnectedRangeField(ToIntFunction<ConnectedRange<?, Integer, Integer>> fieldExtractor) {
        this.fieldExtractor = fieldExtractor;
    }

    public int extractField(ConnectedRange<?, Integer, Integer> sequence) {
        return fieldExtractor.applyAsInt(sequence);
    }
}
