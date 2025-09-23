package ai.timefold.wasm.service.classgen;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Collections;
import java.util.List;
import java.util.Map;

import jakarta.inject.Inject;

import ai.timefold.solver.core.api.score.buildin.simple.SimpleScore;
import ai.timefold.solver.core.api.score.stream.ConstraintCollectors;
import ai.timefold.wasm.service.Employee;
import ai.timefold.wasm.service.Schedule;
import ai.timefold.wasm.service.Shift;
import ai.timefold.wasm.service.SolverResource;
import ai.timefold.wasm.service.TestUtils;
import ai.timefold.wasm.service.dto.WasmConstraint;
import ai.timefold.wasm.service.dto.WasmFunction;
import ai.timefold.wasm.service.dto.constraint.ComplementComponent;
import ai.timefold.wasm.service.dto.constraint.ExpandComponent;
import ai.timefold.wasm.service.dto.constraint.FilterComponent;
import ai.timefold.wasm.service.dto.constraint.ForEachComponent;
import ai.timefold.wasm.service.dto.constraint.GroupByComponent;
import ai.timefold.wasm.service.dto.constraint.IfExistsComponent;
import ai.timefold.wasm.service.dto.constraint.IfNotExistsComponent;
import ai.timefold.wasm.service.dto.constraint.MapComponent;
import ai.timefold.wasm.service.dto.constraint.RewardComponent;
import ai.timefold.wasm.service.dto.constraint.groupby.CountAggregator;
import ai.timefold.wasm.service.dto.constraint.groupby.LoadBalanceAggregator;
import ai.timefold.wasm.service.dto.constraint.joiner.GreaterThanJoiner;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import io.quarkus.test.junit.QuarkusTest;

@QuarkusTest
public class ComponentTest {
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
    public void ifExists() throws JsonProcessingException {
        var problem = TestUtils.getPlanningProblem();
        problem.setConstraints(
                Map.of("thereABetterEmployee", new WasmConstraint(List.of(
                        new ForEachComponent("Employee"),
                        new IfExistsComponent("Employee", List.of(
                                new GreaterThanJoiner(new WasmFunction("getEmployeeId"),
                                        null, null, new WasmFunction("compareInt"))
                        )),
                        new RewardComponent("1", null)
                )))
        );

        problem.setProblem(objectMapper.writeValueAsString(new Schedule(
                List.of(e1), Collections.emptyList()
        )));

        var analysis = solverResource.analyze(problem);
        assertThat(analysis.getConstraintAnalysis("thereABetterEmployee").score())
                .isEqualTo(SimpleScore.of(0));

        problem.setProblem(objectMapper.writeValueAsString(new Schedule(
                List.of(e1, e2), Collections.emptyList()
        )));

        analysis = solverResource.analyze(problem);
        assertThat(analysis.getConstraintAnalysis("thereABetterEmployee").score())
                .isEqualTo(SimpleScore.of(1));

        problem.setProblem(objectMapper.writeValueAsString(new Schedule(
                List.of(e1, e2, e3), Collections.emptyList()
        )));

        analysis = solverResource.analyze(problem);
        assertThat(analysis.getConstraintAnalysis("thereABetterEmployee").score())
                .isEqualTo(SimpleScore.of(2));
    }

    @Test
    public void ifNotExists() throws JsonProcessingException {
        var problem = TestUtils.getPlanningProblem();
        problem.setConstraints(
                Map.of("thereNotABetterEmployee", new WasmConstraint(List.of(
                        new ForEachComponent("Employee"),
                        new IfNotExistsComponent("Employee", List.of(
                                new GreaterThanJoiner(new WasmFunction("getEmployeeId"),
                                        null, null, new WasmFunction("compareInt"))
                        )),
                        new RewardComponent("1", null)
                )))
        );

        problem.setProblem(objectMapper.writeValueAsString(new Schedule(
                List.of(e1), Collections.emptyList()
        )));

        var analysis = solverResource.analyze(problem);
        assertThat(analysis.getConstraintAnalysis("thereNotABetterEmployee").score())
                .isEqualTo(SimpleScore.of(1));

        problem.setProblem(objectMapper.writeValueAsString(new Schedule(
                List.of(e1, e2), Collections.emptyList()
        )));

        analysis = solverResource.analyze(problem);
        assertThat(analysis.getConstraintAnalysis("thereNotABetterEmployee").score())
                .isEqualTo(SimpleScore.of(1));

        problem.setProblem(objectMapper.writeValueAsString(new Schedule(
                List.of(e1, e2, e3), Collections.emptyList()
        )));

        analysis = solverResource.analyze(problem);
        assertThat(analysis.getConstraintAnalysis("thereNotABetterEmployee").score())
                .isEqualTo(SimpleScore.of(1));
    }

    @Test
    public void map() throws JsonProcessingException {
        var problem = TestUtils.getPlanningProblem();
        problem.setConstraints(
                Map.of("employeeId", new WasmConstraint(List.of(
                        new ForEachComponent("Employee"),
                        new MapComponent(List.of(new WasmFunction("getEmployeeId"))),
                        new RewardComponent("1", new WasmFunction("scaleByCount"))
                )))
        );

        problem.setProblem(objectMapper.writeValueAsString(new Schedule(
                List.of(e1), Collections.emptyList()
        )));

        var analysis = solverResource.analyze(problem);
        assertThat(analysis.getConstraintAnalysis("employeeId").score())
                .isEqualTo(SimpleScore.of(1));

        problem.setProblem(objectMapper.writeValueAsString(new Schedule(
                List.of(e1, e2), Collections.emptyList()
        )));

        analysis = solverResource.analyze(problem);
        assertThat(analysis.getConstraintAnalysis("employeeId").score())
                .isEqualTo(SimpleScore.of(3));

        problem.setProblem(objectMapper.writeValueAsString(new Schedule(
                List.of(e1, e2, e3), Collections.emptyList()
        )));

        analysis = solverResource.analyze(problem);
        assertThat(analysis.getConstraintAnalysis("employeeId").score())
                .isEqualTo(SimpleScore.of(6));
    }

    @Test
    public void expand() throws JsonProcessingException {
        var problem = TestUtils.getPlanningProblem();
        problem.setConstraints(
                Map.of("isEmployeeId0", new WasmConstraint(List.of(
                        new ForEachComponent("Shift"),
                        new ExpandComponent(List.of(new WasmFunction("getEmployee"))),
                        new FilterComponent(new WasmFunction("isEmployeeId0")),
                        new RewardComponent("1", null)
                )))
        );

        var shift = new Shift(e0);
        problem.setProblem(objectMapper.writeValueAsString(new Schedule(
                List.of(e0, e1, e2, e3), List.of(shift)
        )));

        var analysis = solverResource.analyze(problem);
        assertThat(analysis.getConstraintAnalysis("isEmployeeId0").score())
                .isEqualTo(SimpleScore.of(1));

        shift = new Shift(e1);
        problem.setProblem(objectMapper.writeValueAsString(new Schedule(
                List.of(e0, e1, e2, e3), List.of(shift)
        )));

        analysis = solverResource.analyze(problem);
        assertThat(analysis.getConstraintAnalysis("isEmployeeId0").score())
                .isEqualTo(SimpleScore.of(0));
    }

    @Test
    public void complement() throws JsonProcessingException {
        var problem = TestUtils.getPlanningProblem();
        problem.setConstraints(
                Map.of("loadBalance", new WasmConstraint(List.of(
                        new ForEachComponent("Shift"),
                        new GroupByComponent(List.of(new WasmFunction("getEmployee")),
                                List.of(new CountAggregator())),
                        new ComplementComponent("Employee"),
                        new GroupByComponent(List.of(), List.of(new LoadBalanceAggregator(new WasmFunction("pick1"),
                                new WasmFunction("pick2")))),
                        new RewardComponent("1", new WasmFunction("scaleByFloat"))
                )))
        );

        var s1 = new Shift(e0);
        var s2 = new Shift(e1);
        var s3 = new Shift(e2);
        var s4 = new Shift(e3);

        problem.setProblem(objectMapper.writeValueAsString(new Schedule(
                List.of(e0, e1, e2, e3), List.of(s1, s2, s3, s4)
        )));

        var analysis = solverResource.analyze(problem);
        assertThat(analysis.getConstraintAnalysis("loadBalance").score())
                .isEqualTo(SimpleScore.of(0));

        s2 = new Shift(e0);
        s4 = new Shift(e2);
        problem.setProblem(objectMapper.writeValueAsString(new Schedule(
                List.of(e0, e1, e2, e3), List.of(s1, s2, s3, s4)
        )));

        analysis = solverResource.analyze(problem);
        assertThat(analysis.getConstraintAnalysis("loadBalance").score())
                .isEqualTo(SimpleScore.of(20));
    }
}
