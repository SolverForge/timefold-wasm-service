package ai.timefold.wasm.service;

import java.util.List;

public record Schedule(List<Employee> employees, List<Shift> shifts) {
}
