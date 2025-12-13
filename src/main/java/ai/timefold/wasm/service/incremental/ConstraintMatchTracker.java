package ai.timefold.wasm.service.incremental;

import ai.timefold.solver.core.api.score.Score;
import ai.timefold.solver.core.api.score.buildin.hardsoft.HardSoftScore;

import java.util.*;

/**
 * Tracks individual constraint matches for incremental score calculation.
 * <p>
 * Instead of re-evaluating all constraints on every move, this tracker maintains
 * the set of active constraint matches and computes score deltas when matches
 * are added or removed.
 *
 * @param <Score_> The score type (e.g., HardSoftScore)
 */
public class ConstraintMatchTracker<Score_ extends Score<Score_>> {
    private final String constraintName;
    private final Map<MatchKey, ConstraintMatch<Score_>> activeMatches = new LinkedHashMap<>();
    private Score_ totalScore;

    /**
     * Creates a new constraint match tracker.
     *
     * @param constraintName The name of the constraint being tracked
     * @param zeroScore The zero score (e.g., HardSoftScore.ZERO)
     */
    public ConstraintMatchTracker(String constraintName, Score_ zeroScore) {
        this.constraintName = constraintName;
        this.totalScore = zeroScore;
    }

    /**
     * Adds a constraint match and returns the score delta.
     * <p>
     * If a match with the same key already exists, it is replaced and the
     * delta reflects the difference between old and new scores.
     *
     * @param matchScore The score impact of this match (typically negative for penalties)
     * @param entities The entities involved in this match (used as match key)
     * @return The score delta (difference between new and old total score)
     */
    public Score_ addMatch(Score_ matchScore, Object... entities) {
        MatchKey key = new MatchKey(entities);
        ConstraintMatch<Score_> oldMatch = activeMatches.get(key);

        Score_ oldTotalScore = totalScore;

        if (oldMatch != null) {
            // Replace existing match - compute delta as (new - old)
            totalScore = totalScore.subtract(oldMatch.score).add(matchScore);
        } else {
            // New match - delta is just the match score
            totalScore = totalScore.add(matchScore);
        }

        activeMatches.put(key, new ConstraintMatch<>(constraintName, matchScore, entities));

        return totalScore.subtract(oldTotalScore);
    }

    /**
     * Removes a constraint match and returns the score delta.
     *
     * @param entities The entities involved in the match to remove
     * @return The score delta (removing a penalty is positive)
     */
    public Score_ removeMatch(Object... entities) {
        MatchKey key = new MatchKey(entities);
        ConstraintMatch<Score_> match = activeMatches.remove(key);

        if (match == null) {
            // Match doesn't exist - no delta
            return (Score_) totalScore.zero();
        }

        Score_ oldTotalScore = totalScore;
        totalScore = totalScore.subtract(match.score);

        return totalScore.subtract(oldTotalScore);
    }

    /**
     * Updates a constraint match when one of the entities changes.
     * <p>
     * This is a convenience method that removes the old match and adds a new one.
     *
     * @param oldScore The score of the match being removed
     * @param newScore The score of the match being added
     * @param oldEntities The entities in the old match
     * @param newEntities The entities in the new match
     * @return The total score delta
     */
    public Score_ updateMatch(Score_ oldScore, Score_ newScore,
                               Object[] oldEntities, Object[] newEntities) {
        Score_ removeDelta = removeMatch(oldEntities);
        Score_ addDelta = addMatch(newScore, newEntities);
        return removeDelta.add(addDelta);
    }

    /**
     * Returns the current total score for this constraint.
     */
    public Score_ getTotalScore() {
        return totalScore;
    }

    /**
     * Returns the number of active constraint matches.
     */
    public int getMatchCount() {
        return activeMatches.size();
    }

    /**
     * Returns all active constraint matches.
     */
    public Collection<ConstraintMatch<Score_>> getMatches() {
        return Collections.unmodifiableCollection(activeMatches.values());
    }

    /**
     * Clears all constraint matches and resets score to zero.
     */
    public void clear() {
        activeMatches.clear();
        totalScore = (Score_) totalScore.zero();
    }

    /**
     * A single constraint match with its score impact.
     */
    public static class ConstraintMatch<Score_ extends Score<Score_>> {
        private final String constraintName;
        private final Score_ score;
        private final Object[] entities;

        public ConstraintMatch(String constraintName, Score_ score, Object... entities) {
            this.constraintName = constraintName;
            this.score = score;
            this.entities = entities;
        }

        public String getConstraintName() {
            return constraintName;
        }

        public Score_ getScore() {
            return score;
        }

        public Object[] getEntities() {
            return entities;
        }

        @Override
        public String toString() {
            return String.format("%s: %s %s", constraintName, score, Arrays.toString(entities));
        }
    }

    /**
     * Key for uniquely identifying a constraint match based on involved entities.
     */
    private static class MatchKey {
        private final Object[] entities;
        private final int hashCode;

        public MatchKey(Object... entities) {
            this.entities = entities;
            this.hashCode = Arrays.hashCode(entities);
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            MatchKey matchKey = (MatchKey) o;
            return Arrays.equals(entities, matchKey.entities);
        }

        @Override
        public int hashCode() {
            return hashCode;
        }
    }
}
