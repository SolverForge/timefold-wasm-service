package ai.timefold.wasm.service;

import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import ai.timefold.solver.core.config.solver.EnvironmentMode;
import ai.timefold.wasm.service.dto.DomainAccessor;
import ai.timefold.wasm.service.dto.DomainListAccessor;
import ai.timefold.wasm.service.dto.DomainObject;
import ai.timefold.wasm.service.dto.DomainObjectMapper;
import ai.timefold.wasm.service.dto.FieldDescriptor;
import ai.timefold.wasm.service.dto.PlanningProblem;
import ai.timefold.wasm.service.dto.PlanningTermination;
import ai.timefold.wasm.service.dto.WasmConstraint;
import ai.timefold.wasm.service.dto.WasmFunction;
import ai.timefold.wasm.service.dto.annotation.DomainPlanningEntityCollectionProperty;
import ai.timefold.wasm.service.dto.annotation.DomainPlanningId;
import ai.timefold.wasm.service.dto.annotation.DomainPlanningScore;
import ai.timefold.wasm.service.dto.annotation.DomainPlanningVariable;
import ai.timefold.wasm.service.dto.annotation.DomainProblemFactCollectionProperty;
import ai.timefold.wasm.service.dto.annotation.DomainValueRangeProvider;
import ai.timefold.wasm.service.dto.constraint.FilterComponent;
import ai.timefold.wasm.service.dto.constraint.ForEachComponent;
import ai.timefold.wasm.service.dto.constraint.GroupByComponent;
import ai.timefold.wasm.service.dto.constraint.JoinComponent;
import ai.timefold.wasm.service.dto.constraint.PenalizeComponent;
import ai.timefold.wasm.service.dto.constraint.RewardComponent;
import ai.timefold.wasm.service.dto.constraint.groupby.CountAggregator;

import com.dylibso.chicory.wabt.Wat2Wasm;
import com.fasterxml.jackson.databind.ObjectMapper;

public class TestUtils {
    public static PlanningProblem getPlanningProblem() {
        // Use LinkedHashMap to preserve field order - critical for WASM memory layout
        var employeeFields = new LinkedHashMap<String, FieldDescriptor>();
        employeeFields.put("id", new FieldDescriptor("int", new DomainAccessor("getEmployeeId", null), List.of(new DomainPlanningId())));

        var shiftFields = new LinkedHashMap<String, FieldDescriptor>();
        shiftFields.put("employee", new FieldDescriptor("Employee",
                new DomainAccessor("getEmployee", "setEmployee"),
                List.of(new DomainPlanningVariable(false))));

        var scheduleFields = new LinkedHashMap<String, FieldDescriptor>();
        scheduleFields.put("employees", new FieldDescriptor("Employee[]",
                new DomainAccessor("getEmployees", "setEmployees"),
                List.of(new DomainProblemFactCollectionProperty(), new DomainValueRangeProvider())));
        scheduleFields.put("shifts", new FieldDescriptor("Shift[]",
                new DomainAccessor("getShifts", "setShifts"),
                List.of(new DomainPlanningEntityCollectionProperty())));
        scheduleFields.put("score", new FieldDescriptor("SimpleScore", List.of(new DomainPlanningScore())));

        var domainObjects = new LinkedHashMap<String, DomainObject>();
        domainObjects.put("Employee", new DomainObject(employeeFields, null));
        domainObjects.put("Shift", new DomainObject(shiftFields, null));
        domainObjects.put("Schedule", new DomainObject(scheduleFields, new DomainObjectMapper("parseSchedule", "scheduleString")));

        return new PlanningProblem(
                domainObjects,
                Map.of(
                        "penalizeId0", new WasmConstraint(
                                List.of(
                                        new ForEachComponent("Shift"),
                                        new JoinComponent("Employee"),
                                        new FilterComponent(new WasmFunction("isEmployeeId0")),
                                        new PenalizeComponent("1", null))
                        ),
                        "distinctIds", new WasmConstraint(
                                List.of(
                                        new ForEachComponent("Shift"),
                                        new GroupByComponent(null, List.of(
                                                new CountAggregator(true, new WasmFunction("getEmployee"))
                                        )),
                                        new RewardComponent("10", new WasmFunction("scaleByCount"))
                                )
                        )
                ),
                EnvironmentMode.FULL_ASSERT,
                Base64.getEncoder().encodeToString(Wat2Wasm.parse(
                        """
                        (module
                            (type (;0;) (func (param i32) (result i32)))
                            (type (;1;) (func (result i32)))
                            (type (;2;) (func (param i32 i32) (result i32)))
                            (type (;3;) (func (param i32 i32 i32)))
                            (type (;4;) (func (param i32 i32)))
                            (type (;5;) (func (param i32) (result i32)))
                            (type (;6;) (func (param f32) (result i32)))
                            (import "host" "hparseSchedule" (func $hparseSchedule (type 2)))
                            (import "host" "hscheduleString" (func $hscheduleString (type 5)))
                            (import "host" "hnewList" (func $hnewList (type 1)))
                            (import "host" "hgetItem" (func $hgetItem (type 2)))
                            (import "host" "hsetItem" (func $hsetItem (type 3)))
                            (import "host" "hsize" (func $hsize (type 0)))
                            (import "host" "happend" (func $happend (type 4)))
                            (import "host" "hinsert" (func $hinsert (type 3)))
                            (import "host" "hremove" (func $hremove (type 4)))
                            (import "host" "hround" (func $hround (type 6)))
                            (memory 1)
                            (func (export "parseSchedule") (param $length i32) (param $schedule i32) (result i32)
                                (local.get $length) (local.get $schedule) (call $hparseSchedule)
                            )
                            (func (export "scheduleString") (param $schedule i32) (result i32)
                                (local.get $schedule) (call $hscheduleString)
                            )
                            (func (export "newList") (result i32)
                                (call $hnewList)
                            )
                            (func (export "round") (param $value f32) (result i32)
                                (local.get $value) (call $hround)
                            )
                            (func (export "getItem") (param $list i32) (param $index i32) (result i32)
                                (local.get $list) (local.get $index) (call $hgetItem)
                            )
                            (func (export "setItem") (param $list i32) (param $index i32) (param $item i32)
                                (local.get $list) (local.get $index) (local.get $item) (call $hsetItem)
                            )
                            (func (export "size") (param $list i32) (result i32)
                                (local.get $list) (call $hsize)
                            )
                            (func (export "append") (param $list i32) (param $item i32)
                                (local.get $list) (local.get $item) (call $happend)
                            )
                            (func (export "insert") (param $list i32) (param $index i32) (param $item i32)
                                (local.get $list) (local.get $index) (local.get $item) (call $hinsert)
                            )
                            (func (export "remove") (param $list i32) (param $index i32)
                                (local.get $list) (local.get $index) (call $hremove)
                            )
                            (func (export "getEmployee") (param $shift i32) (result i32)
                                (local.get $shift) (i32.load)
                            )
                            (func (export "getShiftEmployeeId") (param $shift i32) (result i32)
                                (local.get $shift) (i32.load) (i32.load)
                            )
                            (func (export "getEmployeeId") (param $employee i32) (result i32)
                                (local.get $employee) (i32.load)
                            )
                            (func (export "getEmployeePlus2") (param $employee i32) (result i32)
                                (i32.add (local.get $employee) (i32.load) (i32.const 2))
                            )
                            (func (export "setEmployee") (param $shift i32) (param $employee i32) (result)
                                (local.get $shift) (local.get $employee) (i32.store)
                            )
                            (func (export "getEmployees") (param $schedule i32) (result i32)
                                (local.get $schedule) (i32.load)
                            )
                            (func (export "setEmployees") (param $schedule i32) (param $employees i32) (result)
                                (local.get $schedule) (local.get $employees) (i32.store)
                            )
                            (func (export "getShifts") (param $schedule i32) (result i32)
                                (i32.add (local.get $schedule) (i32.const 4)) (i32.load)
                            )
                            (func (export "setShifts") (param $schedule i32) (param $shifts i32) (result)
                                (i32.add (local.get $schedule) (i32.const 4)) (local.get $shifts) (i32.store)
                            )
                            (func (export "isEmployeeId0") (param $shift i32) (param $employee i32) (result i32)
                                (i32.eq (local.get $shift) (i32.load) (i32.load) (i32.const 0))
                            )
                            (func (export "scaleByCount") (param $count i32) (result i32)
                                (local.get $count)
                            )
                            (func (export "scaleByFloat") (param $value f32) (result i32)
                                (local.get $value) (call $hround)
                            )
                            (func (export "scaleByCountItemSquared") (param $list i32) (result i32)
                                (local $x i32) (i32.mul (local.get $list) (i32.const 0) (call $hgetItem) (local.tee $x) (local.get $x))
                            )
                            (func (export "compareInt") (param $a i32) (param $b i32) (result i32)
                                (i32.sub (local.get $a) (local.get $b))
                            )
                            (func (export "sameParity") (param $a i32) (param $b i32) (result i32)
                                (local.get $a) (i32.const 2) (i32.rem_u) (local.get $b) (i32.const 2) (i32.rem_u) (i32.eq)
                            )
                            (func (export "parity") (param $a i32) (result i32)
                                (local.get $a) (i32.const 2) (i32.rem_u)
                            )
                            (func (export "id") (param $a i32) (result i32)
                                (local.get $a)
                            )
                            (func (export "pick1") (param $a i32) (param $b i32) (result i32)
                                (local.get $a)
                            )
                            (func (export "pick2") (param $a i32) (param $b i32) (result i32)
                                (local.get $b)
                            )
                            (func (export "alloc") (param $size i32) (result i32)
                                (local $out i32) (i32.const 0) (i32.load) (local.set $out) (i32.const 0) (i32.add (local.get $out) (local.get $size)) (i32.store) (local.get $out)
                            )
                            (func (export "dealloc") (param $pointer i32) (result)
                                return
                            )
                            (func (export "_start") (result)
                                (i32.const 0) (i32.const 32) (i32.store)
                            )
                        )
                        """)),
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
                """
                {"employees": [{"id": 0}, {"id": 1}], "shifts": [{}, {}]}
                """, new PlanningTermination(null, null, null, null, null, 10, null, null, null)
        );
    }

    /**
     * @deprecated Host functions are now auto-generated by HostFunctionProvider.
     *             This method is kept for backwards compatibility but does nothing.
     */
    @Deprecated
    public static void setup(SolverResource solverResource, ObjectMapper objectMapper) {
        // No longer needed - host functions are generated per request by HostFunctionProvider
    }
}
