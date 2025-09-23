package ai.timefold.wasm.service.classgen;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Collections;
import java.util.List;
import java.util.Map;

import jakarta.inject.Inject;

import ai.timefold.solver.core.api.score.buildin.simple.SimpleScore;
import ai.timefold.wasm.service.Employee;
import ai.timefold.wasm.service.Schedule;
import ai.timefold.wasm.service.Shift;
import ai.timefold.wasm.service.SolverResource;
import ai.timefold.wasm.service.TestUtils;
import ai.timefold.wasm.service.dto.WasmConstraint;
import ai.timefold.wasm.service.dto.WasmFunction;
import ai.timefold.wasm.service.dto.constraint.ForEachComponent;
import ai.timefold.wasm.service.dto.constraint.JoinComponent;
import ai.timefold.wasm.service.dto.constraint.RewardComponent;
import ai.timefold.wasm.service.dto.constraint.joiner.EqualJoiner;
import ai.timefold.wasm.service.dto.constraint.joiner.FilteringJoiner;
import ai.timefold.wasm.service.dto.constraint.joiner.GreaterThanJoiner;
import ai.timefold.wasm.service.dto.constraint.joiner.GreaterThanOrEqualJoiner;
import ai.timefold.wasm.service.dto.constraint.joiner.LessThanJoiner;
import ai.timefold.wasm.service.dto.constraint.joiner.LessThanOrEqualJoiner;
import ai.timefold.wasm.service.dto.constraint.joiner.OverlappingJoiner;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import io.quarkus.test.junit.QuarkusTest;

@QuarkusTest
public class JoinerTest {
    @Inject
    ObjectMapper objectMapper;

    @Inject
    SolverResource solverResource;

    Employee e0, e1, e2, e3;

    @BeforeEach
    public void setup() {
        TestUtils.setup(solverResource, objectMapper);
        e0 = new Employee("0");
        e1 = new Employee("1");
        e2 = new Employee("2");
        e3 = new Employee("3");
    }

    @Test
    public void testEqual() throws JsonProcessingException {
        var problem = TestUtils.getPlanningProblem();
        problem.setConstraints(
                Map.of("sameEmployee", new WasmConstraint(List.of(
                        new ForEachComponent("Shift"),
                        new JoinComponent("Shift", List.of(new EqualJoiner(
                                new WasmFunction("getEmployee"),
                                null,
                                null,
                                null,
                                null
                        ))),
                        new RewardComponent("1", null)
                )))
        );

        var s1 = new Shift(e1);
        var s2 = new Shift(e2);
        var s3 = new Shift(e3);

        problem.setProblem(objectMapper.writeValueAsString(new Schedule(
                List.of(e0, e1, e2, e3), List.of(s1, s2, s3)
        )));

        var analysis = solverResource.analyze(problem);
        assertThat(analysis.getConstraintAnalysis("sameEmployee").score())
                .isEqualTo(SimpleScore.of(3));

        s3 = new Shift(e1);

        problem.setProblem(objectMapper.writeValueAsString(new Schedule(
                List.of(e0, e1, e2, e3), List.of(s1, s2, s3)
        )));

        analysis = solverResource.analyze(problem);
        assertThat(analysis.getConstraintAnalysis("sameEmployee").score())
                .isEqualTo(SimpleScore.of(5));
    }

    @Test
    public void testEqualCustomRelation() throws JsonProcessingException {
        var problem = TestUtils.getPlanningProblem();
        problem.setConstraints(
                Map.of("sameParity", new WasmConstraint(List.of(
                        new ForEachComponent("Employee"),
                        new JoinComponent("Employee", List.of(new EqualJoiner(
                                new WasmFunction("getEmployeeId"),
                                null,
                                null,
                                new WasmFunction("sameParity"),
                                new WasmFunction("parity")
                        ))),
                        new RewardComponent("1", null)
                )))
        );

        problem.setProblem(objectMapper.writeValueAsString(new Schedule(
                List.of(e0, e1, e2, e3), Collections.emptyList()
        )));

        var analysis = solverResource.analyze(problem);
        assertThat(analysis.getConstraintAnalysis("sameParity").score())
                .isEqualTo(SimpleScore.of(8));


        var e4 = new Employee("4");
        problem.setProblem(objectMapper.writeValueAsString(new Schedule(
                List.of(e1, e2, e4), Collections.emptyList()
        )));

        analysis = solverResource.analyze(problem);
        assertThat(analysis.getConstraintAnalysis("sameParity").score())
                .isEqualTo(SimpleScore.of(5));
    }

    @Test
    public void testLessThan() throws JsonProcessingException {
        var problem = TestUtils.getPlanningProblem();
        problem.setConstraints(
                Map.of("smallerEmployee", new WasmConstraint(List.of(
                        new ForEachComponent("Shift"),
                        new JoinComponent("Shift", List.of(new LessThanJoiner(
                                new WasmFunction("getShiftEmployeeId"),
                                null,
                                null,
                                new WasmFunction("compareInt")
                        ))),
                        new RewardComponent("1", null)
                )))
        );

        var s1 = new Shift(e1);
        var s2 = new Shift(e2);
        var s3 = new Shift(e3);

        problem.setProblem(objectMapper.writeValueAsString(new Schedule(
                List.of(e0, e1, e2, e3), List.of(s1, s2, s3)
        )));

        var analysis = solverResource.analyze(problem);
        assertThat(analysis.getConstraintAnalysis("smallerEmployee").score())
                .isEqualTo(SimpleScore.of(3));

        s3 = new Shift(e1);

        problem.setProblem(objectMapper.writeValueAsString(new Schedule(
                List.of(e0, e1, e2, e3), List.of(s1, s2, s3)
        )));

        analysis = solverResource.analyze(problem);
        assertThat(analysis.getConstraintAnalysis("smallerEmployee").score())
                .isEqualTo(SimpleScore.of(2));
    }

    @Test
    public void testGreaterThan() throws JsonProcessingException {
        var problem = TestUtils.getPlanningProblem();
        problem.setConstraints(
                Map.of("biggerEmployee", new WasmConstraint(List.of(
                        new ForEachComponent("Shift"),
                        new JoinComponent("Shift", List.of(new GreaterThanJoiner(
                                new WasmFunction("getShiftEmployeeId"),
                                null,
                                null,
                                new WasmFunction("compareInt")
                        ))),
                        new RewardComponent("1", null)
                )))
        );

        var s1 = new Shift(e1);
        var s2 = new Shift(e2);
        var s3 = new Shift(e3);

        problem.setProblem(objectMapper.writeValueAsString(new Schedule(
                List.of(e0, e1, e2, e3), List.of(s1, s2, s3)
        )));

        var analysis = solverResource.analyze(problem);
        assertThat(analysis.getConstraintAnalysis("biggerEmployee").score())
                .isEqualTo(SimpleScore.of(3));

        s3 = new Shift(e1);

        problem.setProblem(objectMapper.writeValueAsString(new Schedule(
                List.of(e0, e1, e2, e3), List.of(s1, s2, s3)
        )));

        analysis = solverResource.analyze(problem);
        assertThat(analysis.getConstraintAnalysis("biggerEmployee").score())
                .isEqualTo(SimpleScore.of(2));
    }

    @Test
    public void testLessThanOrEqual() throws JsonProcessingException {
        var problem = TestUtils.getPlanningProblem();
        problem.setConstraints(
                Map.of("smallerOrEqualEmployee", new WasmConstraint(List.of(
                        new ForEachComponent("Shift"),
                        new JoinComponent("Shift", List.of(new LessThanOrEqualJoiner(
                                new WasmFunction("getShiftEmployeeId"),
                                null,
                                null,
                                new WasmFunction("compareInt")
                        ))),
                        new RewardComponent("1", null)
                )))
        );

        var s1 = new Shift(e1);
        var s2 = new Shift(e2);
        var s3 = new Shift(e3);

        problem.setProblem(objectMapper.writeValueAsString(new Schedule(
                List.of(e0, e1, e2, e3), List.of(s1, s2, s3)
        )));

        var analysis = solverResource.analyze(problem);
        assertThat(analysis.getConstraintAnalysis("smallerOrEqualEmployee").score())
                .isEqualTo(SimpleScore.of(6));

        s3 = new Shift(e1);

        problem.setProblem(objectMapper.writeValueAsString(new Schedule(
                List.of(e0, e1, e2, e3), List.of(s1, s2, s3)
        )));

        analysis = solverResource.analyze(problem);
        assertThat(analysis.getConstraintAnalysis("smallerOrEqualEmployee").score())
                .isEqualTo(SimpleScore.of(7));
    }

    @Test
    public void testGreaterThanOrEqual() throws JsonProcessingException {
        var problem = TestUtils.getPlanningProblem();
        problem.setConstraints(
                Map.of("biggerOrEqualEmployee", new WasmConstraint(List.of(
                        new ForEachComponent("Shift"),
                        new JoinComponent("Shift", List.of(new GreaterThanOrEqualJoiner(
                                new WasmFunction("getShiftEmployeeId"),
                                null,
                                null,
                                new WasmFunction("compareInt")
                        ))),
                        new RewardComponent("1", null)
                )))
        );

        var s1 = new Shift(e1);
        var s2 = new Shift(e2);
        var s3 = new Shift(e3);

        problem.setProblem(objectMapper.writeValueAsString(new Schedule(
                List.of(e0, e1, e2, e3), List.of(s1, s2, s3)
        )));

        var analysis = solverResource.analyze(problem);
        assertThat(analysis.getConstraintAnalysis("biggerOrEqualEmployee").score())
                .isEqualTo(SimpleScore.of(6));

        s3 = new Shift(e1);

        problem.setProblem(objectMapper.writeValueAsString(new Schedule(
                List.of(e0, e1, e2, e3), List.of(s1, s2, s3)
        )));

        analysis = solverResource.analyze(problem);
        assertThat(analysis.getConstraintAnalysis("biggerOrEqualEmployee").score())
                .isEqualTo(SimpleScore.of(7));
    }

    @Test
    public void testOverlapping() throws JsonProcessingException {
        var problem = TestUtils.getPlanningProblem();
        problem.setConstraints(
                Map.of("overlapping", new WasmConstraint(List.of(
                        new ForEachComponent("Employee"),
                        new JoinComponent("Employee", List.of(new OverlappingJoiner(
                                new WasmFunction("getEmployeeId"),
                                new WasmFunction("getEmployeePlus2"),
                                null,
                                null,
                                null,
                                null,
                                new WasmFunction("compareInt")
                        ))),
                        new RewardComponent("1", null)
                )))
        );

        problem.setProblem(objectMapper.writeValueAsString(new Schedule(
                List.of(e0, e1, e2, e3), Collections.emptyList()
        )));

        var analysis = solverResource.analyze(problem);
        assertThat(analysis.getConstraintAnalysis("overlapping").score())
                .isEqualTo(SimpleScore.of(10));
    }

    @Test
    public void testFiltering() throws JsonProcessingException {
        var problem = TestUtils.getPlanningProblem();
        problem.setConstraints(
                Map.of("filtering", new WasmConstraint(List.of(
                        new ForEachComponent("Shift"),
                        new JoinComponent("Employee", List.of(new FilteringJoiner(
                                new WasmFunction("isEmployeeId0")
                        ))),
                        new RewardComponent("1", null)
                )))
        );

        var shift = new Shift(e0);

        problem.setProblem(objectMapper.writeValueAsString(new Schedule(
                List.of(e0, e1, e2, e3), List.of(shift)
        )));

        var analysis = solverResource.analyze(problem);
        assertThat(analysis.getConstraintAnalysis("filtering").score())
                .isEqualTo(SimpleScore.of(4));

        shift = new Shift(e1);

        problem.setProblem(objectMapper.writeValueAsString(new Schedule(
                List.of(e0, e1, e2, e3), List.of(shift)
        )));

        analysis = solverResource.analyze(problem);
        assertThat(analysis.getConstraintAnalysis("filtering").score())
                .isEqualTo(SimpleScore.of(0));
    }
}
