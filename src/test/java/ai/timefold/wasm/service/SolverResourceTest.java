package ai.timefold.wasm.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Base64;
import java.util.List;
import java.util.Map;

import ai.timefold.solver.core.api.score.buildin.simple.SimpleScore;
import ai.timefold.wasm.service.dto.DomainObject;
import ai.timefold.wasm.service.dto.FieldDescriptor;
import ai.timefold.wasm.service.dto.PlanningProblem;
import ai.timefold.wasm.service.dto.WasmConstraint;
import ai.timefold.wasm.service.dto.WasmFunction;
import ai.timefold.wasm.service.dto.annotation.DomainPlanningEntityCollectionProperty;
import ai.timefold.wasm.service.dto.annotation.DomainPlanningScore;
import ai.timefold.wasm.service.dto.annotation.DomainPlanningVariable;
import ai.timefold.wasm.service.dto.annotation.DomainProblemFactCollectionProperty;
import ai.timefold.wasm.service.dto.annotation.DomainValueRangeProvider;
import ai.timefold.wasm.service.dto.constraint.FilterComponent;
import ai.timefold.wasm.service.dto.constraint.ForEachComponent;

import org.junit.jupiter.api.Test;

import com.dylibso.chicory.wabt.Wat2Wasm;

public class SolverResourceTest {
    @Test
    public void solveTest() {
        var solverResource = new SolverResource();
        var planningProblem = new PlanningProblem(
                Map.of(
                        "Employee",
                        new DomainObject(
                                Map.of("id", new FieldDescriptor("int", null))
                        ),
                        "Shift",
                        new DomainObject(
                                Map.of("employee", new FieldDescriptor("Employee", List.of(new DomainPlanningVariable(false))))
                        ),
                        "Schedule",
                        new DomainObject(
                                Map.of("employees", new FieldDescriptor("Employee[]", List.of(new DomainProblemFactCollectionProperty(), new DomainValueRangeProvider())),
                                        "shifts", new FieldDescriptor("Shift[]", List.of(new DomainPlanningEntityCollectionProperty())),
                                        "score", new  FieldDescriptor("SimpleScore", List.of(new DomainPlanningScore()))
                        ))
                        ),
                // List.of(),
                // penalize
                List.of(
                        new WasmConstraint(
                                "penalizeId0",
                                "1",
                                List.of(
                                      new ForEachComponent("Shift"),
                                      new FilterComponent(new WasmFunction("isEmployeeId0"))
                                )
                        )
                ),
                //reward
                List.of(),
                Base64.getEncoder().encodeToString(Wat2Wasm.parse(
                        """
                        (module
                            (memory 1024)
                            (func (export "isEmployeeId0") (param $shift i32) (result i32)
                                (i32.eq (local.get $shift) (i32.load) (i32.load) (i32.const 0))
                            )
                        )
                        """)),
                Map.of(
                        "employees", List.of(
                                Map.of("id", 0),
                                Map.of("id", 1)
                        ),
                        "shifts", List.of(
                                Map.of(),
                                Map.of()
                        )
                )
        );
        var out = solverResource.solve(planningProblem);
        System.out.println(out);
        assertThat(out).containsKeys("score", "employees", "shifts");
        assertThat(out).containsEntry("shifts", List.of(
                Map.of("employee", Map.of("id", 1)), Map.of("employee", Map.of("id", 1))
        ));
        assertThat(out).containsEntry("score", SimpleScore.of(0));
    }
}
