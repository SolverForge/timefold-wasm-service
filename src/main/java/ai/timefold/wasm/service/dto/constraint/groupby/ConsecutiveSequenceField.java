package ai.timefold.wasm.service.dto.constraint.groupby;

import java.util.function.ToIntFunction;

import ai.timefold.solver.core.api.score.stream.common.Sequence;

public enum ConsecutiveSequenceField {
    COUNT(Sequence::getCount),
    LENGTH(Sequence::getLength),
    START(Sequence::getFirstItem),
    END(Sequence::getLastItem),;

    private final ToIntFunction<Sequence<Integer, Integer>> fieldExtractor;

    ConsecutiveSequenceField(ToIntFunction<Sequence<Integer, Integer>> fieldExtractor) {
        this.fieldExtractor = fieldExtractor;
    }

    public int extractField(Sequence<Integer, Integer> sequence) {
        return fieldExtractor.applyAsInt(sequence);
    }
}
