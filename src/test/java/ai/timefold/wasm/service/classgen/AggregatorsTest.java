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
import ai.timefold.wasm.service.dto.constraint.GroupByComponent;
import ai.timefold.wasm.service.dto.constraint.RewardComponent;
import ai.timefold.wasm.service.dto.constraint.groupby.AverageAggregator;
import ai.timefold.wasm.service.dto.constraint.groupby.CountAggregator;
import ai.timefold.wasm.service.dto.constraint.groupby.SumAggregator;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import io.quarkus.test.junit.QuarkusTest;

@QuarkusTest
public class AggregatorsTest {
    @Inject
    ObjectMapper objectMapper;

    @Inject
    SolverResource solverResource;

    Employee e1, e2, e3;

    @BeforeEach
    public void setup() {
        TestUtils.setup(solverResource, objectMapper);
        e1 = new Employee("1");
        e2 = new Employee("2");
        e3 = new Employee("3");
    }

    @Test
    public void testCount() throws JsonProcessingException {
        var problem = TestUtils.getPlanningProblem();
        problem.setConstraints(
                Map.of("count", new WasmConstraint(List.of(
                        new ForEachComponent("Employee"),
                        new GroupByComponent(Collections.emptyList(), List.of(new CountAggregator(false, null))),
                        new RewardComponent("1", new WasmFunction("scaleByCount"))
                )))
        );

        problem.setProblem(objectMapper.writeValueAsString(new Schedule(
                List.of(e1), Collections.emptyList()
        )));

        var analysis = solverResource.analyze(problem);
        assertThat(analysis.getConstraintAnalysis("count").score())
                .isEqualTo(SimpleScore.of(1));

        problem.setProblem(objectMapper.writeValueAsString(new Schedule(
                List.of(e1, e2, e3), Collections.emptyList()
        )));

        analysis = solverResource.analyze(problem);
        assertThat(analysis.getConstraintAnalysis("count").score())
                .isEqualTo(SimpleScore.of(3));
    }

    @Test
    public void testCountDistinct() throws JsonProcessingException {
        var problem = TestUtils.getPlanningProblem();
        problem.setConstraints(
                Map.of("countDistinct", new WasmConstraint(List.of(
                        new ForEachComponent("Shift"),
                        new GroupByComponent(Collections.emptyList(), List.of(new CountAggregator(true, new WasmFunction("getEmployee")))),
                        new RewardComponent("1", new WasmFunction("scaleByCount"))
                )))
        );

        var s1 = new Shift(e1);
        var s2 = new Shift(e2);
        var s3 = new Shift(e3);

        problem.setProblem(objectMapper.writeValueAsString(new Schedule(
                Collections.emptyList(), List.of(s1, s2, s3)
        )));

        var analysis = solverResource.analyze(problem);
        assertThat(analysis.getConstraintAnalysis("countDistinct").score())
                .isEqualTo(SimpleScore.of(3));

        s3 = new Shift(e1);

        problem.setProblem(objectMapper.writeValueAsString(new Schedule(
                Collections.emptyList(), List.of(s1, s2, s3)
        )));

        analysis = solverResource.analyze(problem);
        assertThat(analysis.getConstraintAnalysis("countDistinct").score())
                .isEqualTo(SimpleScore.of(2));
    }

    @Test
    public void testSum() throws JsonProcessingException {
        var problem = TestUtils.getPlanningProblem();
        problem.setConstraints(
                Map.of("sum", new WasmConstraint(List.of(
                        new ForEachComponent("Employee"),
                        new GroupByComponent(Collections.emptyList(), List.of(new SumAggregator(new WasmFunction("getEmployeeId")))),
                        new RewardComponent("1", new WasmFunction("scaleByCount"))
                )))
        );

        problem.setProblem(objectMapper.writeValueAsString(new Schedule(
                List.of(e1), Collections.emptyList()
        )));

        var analysis = solverResource.analyze(problem);
        assertThat(analysis.getConstraintAnalysis("sum").score())
                .isEqualTo(SimpleScore.of(1));

        problem.setProblem(objectMapper.writeValueAsString(new Schedule(
                List.of(e1, e2, e3), Collections.emptyList()
        )));

        analysis = solverResource.analyze(problem);
        assertThat(analysis.getConstraintAnalysis("sum").score())
                .isEqualTo(SimpleScore.of(6));
    }

    @Test
    public void testAverage() throws JsonProcessingException {
        var problem = TestUtils.getPlanningProblem();
        problem.setConstraints(
                Map.of("average", new WasmConstraint(List.of(
                        new ForEachComponent("Employee"),
                        new GroupByComponent(Collections.emptyList(), List.of(new AverageAggregator(new WasmFunction("getEmployeeId")))),
                        new RewardComponent("1", new WasmFunction("scaleByFloat"))
                )))
        );

        problem.setProblem(objectMapper.writeValueAsString(new Schedule(
                List.of(e1), Collections.emptyList()
        )));

        var analysis = solverResource.analyze(problem);
        assertThat(analysis.getConstraintAnalysis("average").score())
                .isEqualTo(SimpleScore.of(10));

        problem.setProblem(objectMapper.writeValueAsString(new Schedule(
                List.of(e1, e2), Collections.emptyList()
        )));

        analysis = solverResource.analyze(problem);
        assertThat(analysis.getConstraintAnalysis("average").score())
                .isEqualTo(SimpleScore.of(15));

        problem.setProblem(objectMapper.writeValueAsString(new Schedule(
                List.of(e1, e3), Collections.emptyList()
        )));

        analysis = solverResource.analyze(problem);
        assertThat(analysis.getConstraintAnalysis("average").score())
                .isEqualTo(SimpleScore.of(20));
    }
}
