package ai.timefold.wasm.service.dto;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.util.Base64;
import java.util.List;
import java.util.Map;

import jakarta.inject.Inject;

import ai.timefold.wasm.service.dto.annotation.DomainPlanningEntityCollectionProperty;
import ai.timefold.wasm.service.dto.annotation.DomainPlanningScore;
import ai.timefold.wasm.service.dto.annotation.DomainPlanningVariable;
import ai.timefold.wasm.service.dto.annotation.DomainProblemFactCollectionProperty;
import ai.timefold.wasm.service.dto.annotation.DomainValueRangeProvider;
import ai.timefold.wasm.service.dto.constraint.FilterComponent;
import ai.timefold.wasm.service.dto.constraint.ForEachComponent;

import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.databind.ObjectMapper;

import io.quarkus.test.junit.QuarkusTest;

@QuarkusTest
public class PlanningProblemDeserializationTest {
    @Inject
    ObjectMapper objectMapper;

    @Test
    public void testDeserialize() throws IOException {
        var employee = new DomainObject("Employee",
                Map.of(
                        "name", new FieldDescriptor("String", null)
                ));
        var shift = new DomainObject("Shift",
                Map.of(
                        "start", new FieldDescriptor("int", null),
                        "end", new FieldDescriptor("int", null),
                        "employee", new FieldDescriptor("Employee", List.of(new DomainPlanningVariable(true)))
                ));
        var schedule = new DomainObject("Schedule",
                Map.of(
                        "employees", new FieldDescriptor("Employee[]", List.of(new DomainProblemFactCollectionProperty(), new DomainValueRangeProvider())),
                        "shifts", new FieldDescriptor("Shift[]", List.of(new DomainPlanningEntityCollectionProperty())),
                        "score", new FieldDescriptor("SimpleScore", List.of(new DomainPlanningScore()))
                ));
        var penalties = List.of(
                new WasmConstraint("penalize unassigned",
                        "1",
                        List.of(
                                new ForEachComponent("Shift"),
                                new FilterComponent(new WasmFunction("unassigned"))
                        ))
        );
        var rewards = List.of(
                new WasmConstraint("reward requested time off",
                        "2",
                        List.of(
                                new ForEachComponent("Shift"),
                                new FilterComponent(new WasmFunction("requestedTimeOff"))
                        ))
        );
        var input = Map.<String, Object>of(
                "employees", List.of(
                        Map.of("name", "Ann"),
                        Map.of("name", "Beth")
                ),
                "shifts", List.of(
                        Map.of("start", 1, "end", 2),
                        Map.of("start", 3, "end", 4)
                )
        );
        var expected = new PlanningProblem(
                Map.of(
                        "Employee", employee,
                        "Shift", shift,
                        "Schedule", schedule
                ),
                penalties,
                rewards,
                Base64.getEncoder().encodeToString(new byte[] {1, 2, 3}),
                input
        );
        assertThat((Object) objectMapper.readerFor(PlanningProblem.class).readValue(
                   """
                   {
                       "domain": {
                           "Employee": {
                               "name": {"type": "String"}
                           },
                           "Shift": {
                               "start": {"type": "int"},
                               "end": {"type": "int"},
                               "employee": {
                                   "type": "Employee",
                                   "annotations": [{"annotation": "PlanningVariable", "allowsUnassigned": true}]
                               }
                           },
                           "Schedule": {
                               "employees": {
                                   "type": "Employee[]",
                                   "annotations": [
                                       {"annotation": "ProblemFactCollectionProperty"},
                                       {"annotation": "ValueRangeProvider"}
                                   ]
                               },
                               "shifts": {
                                   "type": "Shift[]",
                                   "annotations": [
                                       {"annotation": "PlanningEntityCollectionProperty"}
                                   ]
                               },
                               "score": {
                                   "type": "SimpleScore",
                                   "annotations": [
                                       {"annotation": "PlanningScore"}
                                   ]
                               }
                           }
                       },
                       "penalize": [
                           {
                               "name": "penalize unassigned",
                               "weight": "1",
                               "match": [
                                   {"kind": "each", "className": "Shift"},
                                   {"kind": "filter", "functionName": "unassigned"}
                               ]
                           }
                       ],
                       "reward": [
                           {
                               "name": "reward requested time off",
                               "weight": "2",
                               "match": [
                                   {"kind": "each", "className": "Shift"},
                                   {"kind": "filter", "functionName": "requestedTimeOff"}
                               ]
                           }
                       ],
                       "wasm": "%s",
                       "problem": {
                           "employees": [
                               {"name": "Ann"}, {"name": "Beth"}
                           ],
                           "shifts": [
                               {"start": 1, "end": 2},
                               {"start": 3, "end": 4}
                           ]
                       }
                   }
                   """.formatted(
                           Base64.getEncoder().encodeToString(new byte[] {1, 2, 3})
                   )
        )).usingRecursiveComparison()
          .isEqualTo(expected);
    }
}
