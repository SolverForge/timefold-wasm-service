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
import ai.timefold.wasm.service.dto.constraint.FlattenLastComponent;
import ai.timefold.wasm.service.dto.constraint.ForEachComponent;
import ai.timefold.wasm.service.dto.constraint.GroupByComponent;
import ai.timefold.wasm.service.dto.constraint.JoinComponent;
import ai.timefold.wasm.service.dto.constraint.RewardComponent;
import ai.timefold.wasm.service.dto.constraint.groupby.AverageAggregator;
import ai.timefold.wasm.service.dto.constraint.groupby.CollectAndThenAggregator;
import ai.timefold.wasm.service.dto.constraint.groupby.ComposeAggregator;
import ai.timefold.wasm.service.dto.constraint.groupby.ConditionalAggregator;
import ai.timefold.wasm.service.dto.constraint.groupby.ConnectedRangeAggregator;
import ai.timefold.wasm.service.dto.constraint.groupby.ConnectedRangeField;
import ai.timefold.wasm.service.dto.constraint.groupby.ConsecutiveAggregator;
import ai.timefold.wasm.service.dto.constraint.groupby.ConsecutiveSequenceField;
import ai.timefold.wasm.service.dto.constraint.groupby.CountAggregator;
import ai.timefold.wasm.service.dto.constraint.groupby.LoadBalanceAggregator;
import ai.timefold.wasm.service.dto.constraint.groupby.MaxAggregator;
import ai.timefold.wasm.service.dto.constraint.groupby.MinAggregator;
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
                List.of(e0, e1, e2, e3), List.of(s1, s2, s3)
        )));

        var analysis = solverResource.analyze(problem);
        assertThat(analysis.getConstraintAnalysis("countDistinct").score())
                .isEqualTo(SimpleScore.of(3));

        s3 = new Shift(e1);

        problem.setProblem(objectMapper.writeValueAsString(new Schedule(
                List.of(e0, e1, e2, e3), List.of(s1, s2, s3)
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

    @Test
    public void testMin() throws JsonProcessingException {
        var problem = TestUtils.getPlanningProblem();
        problem.setConstraints(
                Map.of("min", new WasmConstraint(List.of(
                        new ForEachComponent("Employee"),
                        new GroupByComponent(Collections.emptyList(), List.of(new MinAggregator(new WasmFunction("getEmployeeId"), "compareInt"))),
                        new RewardComponent("1", new WasmFunction("scaleByCount"))
                )))
        );

        problem.setProblem(objectMapper.writeValueAsString(new Schedule(
                List.of(e1, e2), Collections.emptyList()
        )));

        var analysis = solverResource.analyze(problem);
        assertThat(analysis.getConstraintAnalysis("min").score())
                .isEqualTo(SimpleScore.of(1));

        problem.setProblem(objectMapper.writeValueAsString(new Schedule(
                List.of(e1, e3), Collections.emptyList()
        )));

        analysis = solverResource.analyze(problem);
        assertThat(analysis.getConstraintAnalysis("min").score())
                .isEqualTo(SimpleScore.of(1));

        problem.setProblem(objectMapper.writeValueAsString(new Schedule(
                List.of(e2, e3), Collections.emptyList()
        )));

        analysis = solverResource.analyze(problem);
        assertThat(analysis.getConstraintAnalysis("min").score())
                .isEqualTo(SimpleScore.of(2));
    }

    @Test
    public void testMax() throws JsonProcessingException {
        var problem = TestUtils.getPlanningProblem();
        problem.setConstraints(
                Map.of("max", new WasmConstraint(List.of(
                        new ForEachComponent("Employee"),
                        new GroupByComponent(Collections.emptyList(), List.of(new MaxAggregator(new WasmFunction("getEmployeeId"), "compareInt"))),
                        new RewardComponent("1", new WasmFunction("scaleByCount"))
                )))
        );

        problem.setProblem(objectMapper.writeValueAsString(new Schedule(
                List.of(e1, e2), Collections.emptyList()
        )));

        var analysis = solverResource.analyze(problem);
        assertThat(analysis.getConstraintAnalysis("max").score())
                .isEqualTo(SimpleScore.of(2));

        problem.setProblem(objectMapper.writeValueAsString(new Schedule(
                List.of(e1, e3), Collections.emptyList()
        )));

        analysis = solverResource.analyze(problem);
        assertThat(analysis.getConstraintAnalysis("max").score())
                .isEqualTo(SimpleScore.of(3));

        problem.setProblem(objectMapper.writeValueAsString(new Schedule(
                List.of(e2, e3), Collections.emptyList()
        )));

        analysis = solverResource.analyze(problem);
        assertThat(analysis.getConstraintAnalysis("max").score())
                .isEqualTo(SimpleScore.of(3));
    }

    @Test
    public void testLoadBalance() throws JsonProcessingException {
        var problem = TestUtils.getPlanningProblem();
        problem.setConstraints(
                Map.of("loadBalance", new WasmConstraint(List.of(
                        new ForEachComponent("Shift"),
                        new GroupByComponent(Collections.emptyList(), List.of(new LoadBalanceAggregator(new WasmFunction("getEmployee"), null))),
                        new RewardComponent("1", new WasmFunction("scaleByFloat"))
                )))
        );

        var s1 = new Shift(e1);
        var s2 = new Shift(e2);
        var s3 = new Shift(e3);
        problem.setProblem(objectMapper.writeValueAsString(new Schedule(
                List.of(e0, e1, e2, e3), List.of(s1, s2, s3)
        )));

        var analysis = solverResource.analyze(problem);
        assertThat(analysis.getConstraintAnalysis("loadBalance").score())
                .isEqualTo(SimpleScore.of(0));

        s1 = new Shift(e1);
        s2 = new Shift(e1);
        s3 = new Shift(e3);
        problem.setProblem(objectMapper.writeValueAsString(new Schedule(
                List.of(e0, e1, e2, e3), List.of(s1, s2, s3)
        )));

        analysis = solverResource.analyze(problem);
        assertThat(analysis.getConstraintAnalysis("loadBalance").score())
                .isEqualTo(SimpleScore.of(7));
    }

    @Test
    public void testConsecutive() throws JsonProcessingException {
        var problem = TestUtils.getPlanningProblem();
        problem.setConstraints(
                Map.of("consecutive", new WasmConstraint(List.of(
                        new ForEachComponent("Employee"),
                        new GroupByComponent(Collections.emptyList(), List.of(new ConsecutiveAggregator(new WasmFunction("getEmployeeId"), List.of(
                           ConsecutiveSequenceField.COUNT
                        )))),
                        new FlattenLastComponent(null),
                        new RewardComponent("1", new WasmFunction("scaleByCountItemSquared"))
                )))
        );

        problem.setProblem(objectMapper.writeValueAsString(new Schedule(
                List.of(e1, e3), Collections.emptyList()
        )));

        var analysis = solverResource.analyze(problem);
        assertThat(analysis.getConstraintAnalysis("consecutive").score())
                .isEqualTo(SimpleScore.of(2));

        problem.setProblem(objectMapper.writeValueAsString(new Schedule(
                List.of(e1, e2), Collections.emptyList()
        )));

        analysis = solverResource.analyze(problem);
        assertThat(analysis.getConstraintAnalysis("consecutive").score())
                .isEqualTo(SimpleScore.of(4));

        problem.setProblem(objectMapper.writeValueAsString(new Schedule(
                List.of(e1, e2, e3), Collections.emptyList()
        )));

        analysis = solverResource.analyze(problem);
        assertThat(analysis.getConstraintAnalysis("consecutive").score())
                .isEqualTo(SimpleScore.of(9));
    }

    @Test
    public void testConnectedRanges() throws JsonProcessingException {
        var problem = TestUtils.getPlanningProblem();
        problem.setConstraints(
                Map.of("connectedRanges", new WasmConstraint(List.of(
                        new ForEachComponent("Employee"),
                        new GroupByComponent(Collections.emptyList(), List.of(new ConnectedRangeAggregator(null,
                                new WasmFunction("getEmployeeId"),
                                new WasmFunction("getEmployeePlus2"),
                                List.of(
                                    ConnectedRangeField.MAX_OVERLAP
                        )))),
                        new FlattenLastComponent(null),
                        new RewardComponent("1", new WasmFunction("scaleByCountItemSquared"))
                )))
        );

        problem.setProblem(objectMapper.writeValueAsString(new Schedule(
                List.of(e1, e3), Collections.emptyList()
        )));

        var analysis = solverResource.analyze(problem);
        assertThat(analysis.getConstraintAnalysis("connectedRanges").score())
                .isEqualTo(SimpleScore.of(1));

        problem.setProblem(objectMapper.writeValueAsString(new Schedule(
                List.of(e1, e2), Collections.emptyList()
        )));

        analysis = solverResource.analyze(problem);
        assertThat(analysis.getConstraintAnalysis("connectedRanges").score())
                .isEqualTo(SimpleScore.of(4));

        problem.setProblem(objectMapper.writeValueAsString(new Schedule(
                List.of(e1, e2, e3), Collections.emptyList()
        )));

        analysis = solverResource.analyze(problem);
        assertThat(analysis.getConstraintAnalysis("connectedRanges").score())
                .isEqualTo(SimpleScore.of(4));

        problem.setProblem(objectMapper.writeValueAsString(new Schedule(
                List.of(e1, e2, e2, e3), Collections.emptyList()
        )));

        analysis = solverResource.analyze(problem);
        assertThat(analysis.getConstraintAnalysis("connectedRanges").score())
                .isEqualTo(SimpleScore.of(9));
    }

    @Test
    public void testConditionally() throws JsonProcessingException {
        var problem = TestUtils.getPlanningProblem();
        problem.setConstraints(
                Map.of("conditionally", new WasmConstraint(List.of(
                        new ForEachComponent("Shift"),
                        new JoinComponent("Employee"),
                        new GroupByComponent(Collections.emptyList(), List.of(
                                new ConditionalAggregator(
                                        new WasmFunction("isEmployeeId0"),
                                        new CountAggregator(false, null)))),
                        new RewardComponent("1", new WasmFunction("scaleByCount"))
                )))
        );

        var s1 = new Shift(e1);
        var s2 = new Shift(e2);
        var s3 = new Shift(e3);

        problem.setProblem(objectMapper.writeValueAsString(new Schedule(
                List.of(e0, e1, e2, e3), List.of(s1, s2, s3)
        )));

        var analysis = solverResource.analyze(problem);
        assertThat(analysis.getConstraintAnalysis("conditionally").score())
                .isEqualTo(SimpleScore.of(0));

        s1 = new Shift(e0);
        s3 = new Shift(e0);

        problem.setProblem(objectMapper.writeValueAsString(new Schedule(
                List.of(e0, e1, e2, e3), List.of(s1, s2, s3)
        )));

        analysis = solverResource.analyze(problem);
        assertThat(analysis.getConstraintAnalysis("conditionally").score())
                .isEqualTo(SimpleScore.of(8));
    }

    @Test
    public void testCompose() throws JsonProcessingException {
        var problem = TestUtils.getPlanningProblem();
        problem.setConstraints(
                Map.of("compose", new WasmConstraint(List.of(
                        new ForEachComponent("Employee"),
                        new GroupByComponent(Collections.emptyList(), List.of(
                                new ComposeAggregator(
                                        List.of(
                                                new MaxAggregator(new WasmFunction("getEmployeeId"),"compareInt"),
                                                new MinAggregator(new WasmFunction("getEmployeeId"),"compareInt")
                                        ),
                                        new WasmFunction("compareInt")
                                        ))),
                        new RewardComponent("1", new WasmFunction("scaleByCount"))
                )))
        );

        problem.setProblem(objectMapper.writeValueAsString(new Schedule(
                List.of(e1, e2), Collections.emptyList()
        )));

        var analysis = solverResource.analyze(problem);
        assertThat(analysis.getConstraintAnalysis("compose").score())
                .isEqualTo(SimpleScore.of(1));

        problem.setProblem(objectMapper.writeValueAsString(new Schedule(
                List.of(e1, e3), Collections.emptyList()
        )));

        analysis = solverResource.analyze(problem);
        assertThat(analysis.getConstraintAnalysis("compose").score())
                .isEqualTo(SimpleScore.of(2));

        problem.setProblem(objectMapper.writeValueAsString(new Schedule(
                List.of(e2, e3), Collections.emptyList()
        )));

        analysis = solverResource.analyze(problem);
        assertThat(analysis.getConstraintAnalysis("compose").score())
                .isEqualTo(SimpleScore.of(1));
    }

    @Test
    public void testCollectAndThen() throws JsonProcessingException {
        var problem = TestUtils.getPlanningProblem();
        problem.setConstraints(
                Map.of("collectAndThen", new WasmConstraint(List.of(
                        new ForEachComponent("Employee"),
                        new GroupByComponent(Collections.emptyList(), List.of(
                                new CollectAndThenAggregator(
                                        new AverageAggregator(new WasmFunction("getEmployeeId")),
                                        new WasmFunction("round")
                                ))),
                        new RewardComponent("1", new WasmFunction("scaleByCount"))
                )))
        );

        problem.setProblem(objectMapper.writeValueAsString(new Schedule(
                List.of(e1, e2), Collections.emptyList()
        )));

        var analysis = solverResource.analyze(problem);
        assertThat(analysis.getConstraintAnalysis("collectAndThen").score())
                .isEqualTo(SimpleScore.of(15));

        problem.setProblem(objectMapper.writeValueAsString(new Schedule(
                List.of(e1, e3), Collections.emptyList()
        )));

        analysis = solverResource.analyze(problem);
        assertThat(analysis.getConstraintAnalysis("collectAndThen").score())
                .isEqualTo(SimpleScore.of(20));

        problem.setProblem(objectMapper.writeValueAsString(new Schedule(
                List.of(e2, e3), Collections.emptyList()
        )));

        analysis = solverResource.analyze(problem);
        assertThat(analysis.getConstraintAnalysis("collectAndThen").score())
                .isEqualTo(SimpleScore.of(25));
    }
}
