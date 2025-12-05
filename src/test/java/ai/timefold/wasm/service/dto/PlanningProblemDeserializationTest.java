package ai.timefold.wasm.service.dto;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import jakarta.inject.Inject;

import ai.timefold.solver.core.config.solver.EnvironmentMode;
import ai.timefold.wasm.service.dto.annotation.DomainPlanningEntityCollectionProperty;
import ai.timefold.wasm.service.dto.annotation.DomainPlanningScore;
import ai.timefold.wasm.service.dto.annotation.DomainPlanningVariable;
import ai.timefold.wasm.service.dto.annotation.DomainProblemFactCollectionProperty;
import ai.timefold.wasm.service.dto.annotation.DomainValueRangeProvider;
import ai.timefold.wasm.service.dto.constraint.FilterComponent;
import ai.timefold.wasm.service.dto.constraint.ForEachComponent;
import ai.timefold.wasm.service.dto.constraint.PenalizeComponent;
import ai.timefold.wasm.service.dto.constraint.RewardComponent;

import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.databind.ObjectMapper;

import io.quarkus.test.junit.QuarkusTest;

@QuarkusTest
public class PlanningProblemDeserializationTest {
    @Inject
    ObjectMapper objectMapper;

    @Test
    public void testDeserialize() throws IOException {
        var employeeFields = new LinkedHashMap<String, FieldDescriptor>();
        employeeFields.put("name", new FieldDescriptor("String", null));
        var employee = new DomainObject(employeeFields, null);
        employee.setName("Employee");

        var shiftFields = new LinkedHashMap<String, FieldDescriptor>();
        shiftFields.put("start", new FieldDescriptor("int", null));
        shiftFields.put("end", new FieldDescriptor("int", null));
        shiftFields.put("employee", new FieldDescriptor("Employee", new DomainAccessor("getEmployee", "setEmployee"), List.of(new DomainPlanningVariable(false))));
        var shift = new DomainObject(shiftFields, null);
        shift.setName("Shift");

        var scheduleFields = new LinkedHashMap<String, FieldDescriptor>();
        scheduleFields.put("employees", new FieldDescriptor("Employee[]", new DomainAccessor("getEmployees", "setEmployees"), List.of(new DomainProblemFactCollectionProperty(), new DomainValueRangeProvider())));
        scheduleFields.put("shifts", new FieldDescriptor("Shift[]", new DomainAccessor("getShifts", "setShifts"), List.of(new DomainPlanningEntityCollectionProperty())));
        scheduleFields.put("score", new FieldDescriptor("SimpleScore", List.of(new DomainPlanningScore())));
        var schedule = new DomainObject(scheduleFields, new DomainObjectMapper("strToSchedule", "scheduleToStr"));
        schedule.setName("Schedule");

        var constraints = Map.of(
                "penalize unassigned", new WasmConstraint(
                        List.of(
                                new ForEachComponent("Shift"),
                                new FilterComponent(new WasmFunction("unassigned")),
                                new PenalizeComponent("1", null)
                        )),
                "reward requested time off", new WasmConstraint(
                        List.of(
                                new ForEachComponent("Shift"),
                                new FilterComponent(new WasmFunction("requestedTimeOff")),
                                new RewardComponent("2", null)
                        ))
        );
        var expected = new PlanningProblem(
                Map.of(
                        "Employee", employee,
                        "Shift", shift,
                        "Schedule", schedule
                ),
                constraints,
                EnvironmentMode.FULL_ASSERT,
                Base64.getEncoder().encodeToString(new byte[] {1, 2, 3}),
                "alloc",
                "dealloc",
                null,
                new DomainListAccessor(
                        "newList",
                        "getItem",
                        "setItem",
                        "size",
                        "append",
                        "insert",
                        "remove",
                        "dealloc"
                ),
                "abcd",
                new PlanningTermination().withSpentLimit("1s")
        );
        assertThat((Object) objectMapper.readerFor(PlanningProblem.class).readValue(
                   """
                   {
                       "domain": {
                           "Employee": {
                               "fields": {"name": {"type": "String"}}
                           },
                           "Shift": {
                               "fields": {
                                   "start": {"type": "int"},
                                   "end": {"type": "int"},
                                   "employee": {
                                       "type": "Employee",
                                       "accessor": {"getter": "getEmployee", "setter": "setEmployee"},
                                       "annotations": [{"annotation": "PlanningVariable", "allowsUnassigned": true}]
                                   }
                               }
                           },
                           "Schedule": {
                               "fields": {
                                   "employees": {
                                       "type": "Employee[]",
                                       "accessor": {"getter": "getEmployees", "setter": "setEmployees"},
                                       "annotations": [
                                           {"annotation": "ProblemFactCollectionProperty"},
                                           {"annotation": "ValueRangeProvider"}
                                       ]
                                   },
                                   "shifts": {
                                       "type": "Shift[]",
                                       "accessor": {"getter": "getShifts", "setter": "setShifts"},
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
                               },
                               "mapper": {"fromString": "strToSchedule", "toString": "scheduleToStr"}
                           }
                       },
                       "constraints": {
                           "penalize unassigned": [
                               {"kind": "forEach", "className": "Shift"},
                               {"kind": "filter", "predicate": "unassigned"},
                               {"kind": "penalize", "weight": "1"}
                           ],
                           "reward requested time off": [
                               {"kind": "forEach", "className": "Shift"},
                               {"kind": "filter", "predicate": "requestedTimeOff"},
                               {"kind": "reward", "weight": "2"}
                           ]
                       },
                       "wasm": "%s",
                       "allocator": "alloc",
                       "deallocator": "dealloc",
                       "listAccessor": {
                           "new": "newList",
                           "get": "getItem",
                           "set": "setItem",
                           "length": "size",
                           "append": "append",
                           "insert": "insert",
                           "remove": "remove",
                           "deallocator": "dealloc"
                       },
                       "problem": "abcd",
                       "environmentMode": "FULL_ASSERT",
                       "termination": {"spentLimit": "1s"}
                   }
                   """.formatted(
                           Base64.getEncoder().encodeToString(new byte[] {1, 2, 3})
                   )
        )).usingRecursiveComparison()
          .ignoringCollectionOrderInFields("constraintList")
          .isEqualTo(expected);
    }
}
