package ai.timefold.wasm.service;

import java.lang.reflect.InvocationTargetException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;

import ai.timefold.solver.core.api.solver.SolverFactory;
import ai.timefold.solver.core.config.solver.SolverConfig;
import ai.timefold.solver.core.config.solver.termination.TerminationConfig;
import ai.timefold.solver.core.impl.util.MutableReference;
import ai.timefold.wasm.service.classgen.ConstraintProviderClassGenerator;
import ai.timefold.wasm.service.classgen.DomainObjectClassGenerator;
import ai.timefold.wasm.service.classgen.WasmObject;
import ai.timefold.wasm.service.dto.PlanningProblem;

import com.dylibso.chicory.compiler.MachineFactoryCompiler;
import com.dylibso.chicory.runtime.Instance;
import com.dylibso.chicory.wasm.Parser;

@Path("/solver")
public class SolverResource {
    @POST
    public Map<String, Object> solve(PlanningProblem planningProblem) {
        var solverConfig = new SolverConfig();

        var domainObjectClassGenerator = new DomainObjectClassGenerator();
        try {
            DomainObjectClassGenerator.domainObjectClassGenerator.set(domainObjectClassGenerator);
            domainObjectClassGenerator.prepareClassesForPlanningProblem(planningProblem);

            var solutionClass = domainObjectClassGenerator.getClassForDomainClassName(planningProblem.getSolutionClass());
            var entityClassList = new ArrayList<Class<?>>(planningProblem.getEntityClassList().size());
            for (var entityClass : planningProblem.getEntityClassList()) {
                entityClassList.add(domainObjectClassGenerator.getClassForDomainClassName(entityClass));
            }

            solverConfig.setSolutionClass(solutionClass);
            solverConfig.setEntityClassList(entityClassList);
            var wasmInstance = createWasmInstance(planningProblem.getWasm());

            var constraintProviderClass = new ConstraintProviderClassGenerator(domainObjectClassGenerator, wasmInstance)
                    .defineConstraintProviderClass(planningProblem);
            solverConfig.withConstraintProviderClass(constraintProviderClass);

            solverConfig.withTerminationConfig(new TerminationConfig().withSecondsSpentLimit(1L));

            var solverFactory = SolverFactory.create(solverConfig);
            var solver = solverFactory.buildSolver();
            var solverInput = convertPlanningProblem(wasmInstance, domainObjectClassGenerator, planningProblem);

            // Copy the solution into a map; we don't know enough from the WASM
            // to create an accurate planning clone that cannot be corrupted by
            // constraints/setters
            MutableReference<Map<String, Object>> bestSolutionRef = new MutableReference<>(planningProblem.getProblem());
            solver.addEventListener(event -> {
                @SuppressWarnings("unchecked")
                var newSolution = (Map<String, Object>) convertObjectToMap(event.getNewBestSolution());
                bestSolutionRef.setValue(newSolution);
            });

            solver.solve(solverInput);
            return bestSolutionRef.getValue();
        } finally {
            DomainObjectClassGenerator.domainObjectClassGenerator.remove();
        }
    }

    private Instance createWasmInstance(byte[] wasm) {
        var out = Instance.builder(Parser.parse(wasm))
                .withMachineFactory(MachineFactoryCompiler::compile)
                .build();
        return out;
    }

    private Object convertPlanningProblem(Instance wasmInstance, DomainObjectClassGenerator classGenerator, PlanningProblem planningProblem) {
        var solutionClass = classGenerator.getClassForDomainClassName(planningProblem.getSolutionClass());
        try {
            return solutionClass.getConstructor(Instance.class, Map.class).newInstance(wasmInstance, planningProblem.getProblem());
        } catch (InstantiationException | IllegalAccessException | InvocationTargetException | NoSuchMethodException e) {
            throw new RuntimeException(e);
        }
    }

    private Object convertObjectToMap(Object object) {
        if ((!(object instanceof WasmObject))) {
            return object;
        }
        try {
            @SuppressWarnings("unchecked")
            Map<String, Object> out = (Map<String, Object>) object.getClass().getMethod("toMap").invoke(object);
            for (var entry : out.entrySet()) {
                if (entry.getValue() instanceof List<?> list) {
                    if (!list.isEmpty()) {
                        var newList = list.stream().map(this::convertObjectToMap).toList();
                        entry.setValue(newList);
                    }
                }
            }
            return out;
        } catch (IllegalAccessException | InvocationTargetException | NoSuchMethodException e) {
            throw new RuntimeException(e);
        }
    }
}
