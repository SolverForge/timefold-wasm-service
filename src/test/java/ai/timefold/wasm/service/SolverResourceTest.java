package ai.timefold.wasm.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;

import jakarta.inject.Inject;

import ai.timefold.solver.core.api.score.buildin.simple.SimpleScore;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import io.quarkus.test.junit.QuarkusTest;

@QuarkusTest
public class SolverResourceTest {
    @Inject
    ObjectMapper objectMapper;

    @Inject
    SolverResource solverResource;

    @BeforeEach
    public void setup() {
        TestUtils.setup(solverResource, objectMapper);
    }

    @Test
    public void solveTest() throws JsonProcessingException {
        var planningProblem = TestUtils.getPlanningProblem();
        var out = solverResource.solve(planningProblem);
        var solution = (Map) objectMapper.readerFor(Map.class).readValue(out.solution());
        assertThat(solution).containsKeys("employees", "shifts");
        assertThat(solution.get("shifts")).usingRecursiveComparison().ignoringCollectionOrder().isEqualTo(List.of(
                Map.of("employee", Map.of("id", 0)), Map.of("employee", Map.of("id", 1))
        ));
        assertThat(out.score()).isEqualTo(SimpleScore.of(18));
    }

    @Test
    public void analyseTest() {
        var planningProblem = TestUtils.getPlanningProblem();
        planningProblem.setProblem("""
                {"employees": [{"id": 0}, {"id": 1}], "shifts": [{}, {}]}
                """);
        var analysis = solverResource.analyze(planningProblem);
        assertThat(analysis.score()).isEqualTo(SimpleScore.ZERO);

        planningProblem.setProblem("""
                {"employees": [{"id": 0}, {"id": 1}], "shifts": [{"employee": {"id": 0}}, {"employee": {"id": 1}}]}
                """);
        analysis = solverResource.analyze(planningProblem);
        assertThat(analysis.score()).isEqualTo(SimpleScore.of(18));

        var constraintAnalysis = analysis.getConstraintAnalysis("penalizeId0");
        assertThat(constraintAnalysis).isNotNull();
        assertThat(constraintAnalysis.matchCount()).isEqualTo(2);
        assertThat(constraintAnalysis.score()).isEqualTo(SimpleScore.of(-2));

        constraintAnalysis = analysis.getConstraintAnalysis("distinctIds");
        assertThat(constraintAnalysis).isNotNull();
        assertThat(constraintAnalysis.matchCount()).isEqualTo(1);
        assertThat(constraintAnalysis.score()).isEqualTo(SimpleScore.of(20));

    }
}
