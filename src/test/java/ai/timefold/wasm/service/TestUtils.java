package ai.timefold.wasm.service;

import java.util.Base64;
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

import com.dylibso.chicory.runtime.HostFunction;
import com.dylibso.chicory.wabt.Wat2Wasm;
import com.dylibso.chicory.wasm.types.FunctionType;
import com.dylibso.chicory.wasm.types.ValType;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

public class TestUtils {
    public static PlanningProblem getPlanningProblem() {
        return new PlanningProblem(
                Map.of(
                        "Employee",
                        new DomainObject(
                                Map.of("id", new FieldDescriptor("int", null))
                                , null),
                        "Shift",
                        new DomainObject(
                                Map.of("employee", new FieldDescriptor("Employee",
                                        new DomainAccessor("getEmployee", "setEmployee"),
                                        List.of(new DomainPlanningVariable(false))))
                                , null),
                        "Schedule",
                        new DomainObject(
                                Map.of("employees", new FieldDescriptor("Employee[]",
                                                new DomainAccessor("getEmployees", "setEmployees"),
                                                List.of(new DomainProblemFactCollectionProperty(), new DomainValueRangeProvider())),
                                        "shifts",
                                        new FieldDescriptor("Shift[]",
                                                new DomainAccessor("getShifts", "setShifts"),
                                                List.of(new DomainPlanningEntityCollectionProperty())),
                                        "score", new  FieldDescriptor("SimpleScore", List.of(new DomainPlanningScore()))
                                ),new DomainObjectMapper("parseSchedule", "scheduleString"))
                ),
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
                            (func (export "getEmployeeId") (param $employee i32) (result i32)
                                (local.get $employee) (i32.load)
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
                                (i32.add (local.get $schedule) (i32.const 32)) (i32.load)
                            )
                            (func (export "setShifts") (param $schedule i32) (param $shifts i32) (result)
                                (i32.add (local.get $schedule) (i32.const 32)) (local.get $shifts) (i32.store)
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
                        "remove"
                ),
                """
                {"employees": [{"id": 0}, {"id": 1}], "shifts": [{}, {}]}
                """, new PlanningTermination("1s", null, null, null, null, null, null, null, null)
        );
    }

    public static void setup(SolverResource solverResource, ObjectMapper objectMapper) {
        // Implemented these in Java since writing them in pure web assembly would be a pain
        var WORD_SIZE = Integer.SIZE;
        solverResource.setHostFunctionList(List.of(
                new HostFunction("host", "hparseSchedule",
                        FunctionType.of(List.of(ValType.I32, ValType.I32), List.of(ValType.I32)),
                        (instance, args) -> {
                            var scheduleString = instance.memory().readString((int) args[1], (int) args[0]);
                            var alloc = instance.export("alloc");
                            var newList = instance.export("newList");
                            var append = instance.export("append");
                            var getItem = instance.export("getItem");

                            try {
                                var parsedSchedule = objectMapper.reader().readTree(scheduleString);
                                var parsedEmployees = parsedSchedule.get("employees");
                                var parsedShifts = parsedSchedule.get("shifts");

                                var schedule = (int) alloc.apply(WORD_SIZE * 2)[0];
                                var employees = (int) newList.apply()[0];
                                var shifts = (int) newList.apply()[0];

                                instance.memory().writeI32(schedule, employees);
                                instance.memory().writeI32(schedule + WORD_SIZE, shifts);

                                for (var i = 0; i < parsedEmployees.size(); i++) {
                                    var parsedEmployee = parsedEmployees.get(i);
                                    var id = parsedEmployee.get("id").asInt();
                                    var employee = (int) alloc.apply(WORD_SIZE)[0];
                                    instance.memory().writeI32(employee, id);
                                    append.apply(employees, employee);
                                }

                                for (var i = 0; i < parsedShifts.size(); i++) {
                                    var parsedShift = parsedShifts.get(i);
                                    var shift = (int) alloc.apply(WORD_SIZE)[0];

                                    if (parsedShift.has("employee")) {
                                        var employee = parsedShift.get("employee");
                                        if (employee.isNull()) {
                                            instance.memory().writeI32(shift, 0);
                                        } else {
                                            instance.memory().writeI32(shift,
                                                    (int) getItem.apply(employees, employee.get("id").asInt())[0]);
                                        }
                                    }

                                    append.apply(shifts, shift);
                                }

                                return new long[] {schedule};
                            } catch (JsonProcessingException e) {
                                throw new RuntimeException(e);
                            }
                        }),
                new HostFunction("host", "hscheduleString",
                        FunctionType.of(List.of(ValType.I32), List.of(ValType.I32)),
                        (instance, args) -> {
                            var schedule = (int) args[0];
                            var alloc = instance.export("alloc");
                            var listSize = instance.export("size");
                            var getItem = instance.export("getItem");

                            var employees = instance.memory().readI32(schedule);
                            var employeesLength = (int) listSize.apply(employees)[0];
                            var shifts = instance.memory().readI32(schedule + WORD_SIZE);
                            var shiftsLength = (int) listSize.apply(shifts)[0];

                            StringBuilder out = new StringBuilder();
                            out.append("{");

                            var isFirst = true;
                            out.append("\"employees\": [");
                            for (int i = 0; i < employeesLength; i++) {
                                if (isFirst) {
                                    isFirst = false;
                                } else {
                                    out.append(", ");
                                }
                                out.append("{");
                                out.append("\"id\": ");
                                var id = instance.memory().readI32((int) getItem.apply(employees, i)[0]);
                                out.append(id);
                                out.append("}");
                            }
                            out.append("], ");

                            isFirst = true;
                            out.append("\"shifts\": [");
                            for (int i = 0; i < shiftsLength; i++) {
                                if (isFirst) {
                                    isFirst = false;
                                } else {
                                    out.append(", ");
                                }
                                out.append("{\"employee\": ");
                                var employee = (int) instance.memory().readI32((int) getItem.apply(shifts, i)[0]);
                                if (employee == 0) {
                                    out.append("null");
                                } else {
                                    out.append("{");
                                    out.append("\"id\": ");
                                    var id = instance.memory().readI32(employee);
                                    out.append(id);
                                    out.append("}");
                                }
                                out.append("}");
                            }
                            out.append("]}");
                            var outString = out.toString();
                            var memoryString = (int) alloc.apply(outString.getBytes().length + 1)[0];
                            instance.memory().writeCString(memoryString, outString);
                            return new long[] {memoryString};
                        }),
                new HostFunction("host", "hnewList",
                        FunctionType.of(List.of(), List.of(ValType.I32)),
                        (instance, args) -> {
                            var alloc = instance.export("alloc");
                            var listInstance = (int) alloc.apply(WORD_SIZE * 2)[0];

                            instance.memory().writeI32(listInstance, 0);
                            instance.memory().writeI32(listInstance + WORD_SIZE, 0);

                            return new long[] {listInstance};
                        }),
                new HostFunction("host", "hgetItem",
                        FunctionType.of(List.of(ValType.I32, ValType.I32), List.of(ValType.I32)),
                        (instance, args) -> {

                            var listInstance = (int) args[0];
                            var backingArray = (int) instance.memory().readI32(listInstance + WORD_SIZE);
                            int itemIndex = (int) args[1];
                            var item = instance.memory().readI32(backingArray + (WORD_SIZE * itemIndex));

                            return new long[] {item};
                        }),
                new HostFunction("host", "hsetItem",
                        FunctionType.of(List.of(ValType.I32, ValType.I32, ValType.I32), List.of()),
                        (instance, args) -> {

                            var listInstance = (int) args[0];
                            var backingArray = (int) instance.memory().readI32(listInstance + WORD_SIZE);
                            int itemIndex = (int) args[1];
                            var item = (int) args[2];
                            instance.memory().writeI32(backingArray + (WORD_SIZE * itemIndex), item);
                            return new long[] {};
                        }),
                new HostFunction("host", "hsize",
                        FunctionType.of(List.of(ValType.I32), List.of(ValType.I32)),
                        (instance, args) -> {
                            var size = (int) instance.memory().readI32((int) args[0]);
                            return new long[] { size };
                        }),
                new HostFunction("host", "happend",
                        FunctionType.of(List.of(ValType.I32, ValType.I32), List.of()),
                        (instance, args) -> {
                            var alloc = instance.export("alloc");

                            var listInstance = (int) args[0];

                            var oldSize = (int) instance.memory().readI32(listInstance);
                            var newSize = oldSize + 1;

                            instance.memory().writeI32(listInstance, newSize);

                            var oldBackingArray = (int) instance.memory().readI32(listInstance + WORD_SIZE);
                            var newBackingArray = (int) alloc.apply((long) WORD_SIZE * newSize)[0];

                            instance.memory().copy(newBackingArray, oldBackingArray, oldSize * WORD_SIZE);
                            instance.memory().writeI32(newBackingArray + oldSize * WORD_SIZE, (int) args[1]);
                            instance.memory().writeI32(listInstance + WORD_SIZE, newBackingArray);

                            return new long[] {};
                        }),
                new HostFunction("host", "hinsert",
                        FunctionType.of(List.of(ValType.I32, ValType.I32, ValType.I32), List.of()),
                        (instance, args) -> {
                            // Not needed for simple planning variables
                            throw new UnsupportedOperationException();
                        }),
                new HostFunction("host", "hremove",
                        FunctionType.of(List.of(ValType.I32, ValType.I32), List.of()),
                        (instance, args) -> {
                            // Not needed for simple planning variables
                            throw new UnsupportedOperationException();
                        }),
                new HostFunction("host", "hround",
                        FunctionType.of(List.of(ValType.F32), List.of(ValType.I32)),
                        (instance, args) -> new long[] { (long) (Float.intBitsToFloat((int) args[0]) * 10) })
        ));
    }
}
