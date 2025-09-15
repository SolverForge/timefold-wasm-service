package ai.timefold.wasm.service;

import java.lang.reflect.InvocationTargetException;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;

import ai.timefold.solver.core.api.solver.SolverFactory;
import ai.timefold.solver.core.config.solver.SolverConfig;
import ai.timefold.solver.core.impl.util.MutableReference;
import ai.timefold.wasm.service.classgen.Allocator;
import ai.timefold.wasm.service.classgen.ConstraintProviderClassGenerator;
import ai.timefold.wasm.service.classgen.DomainObjectClassGenerator;
import ai.timefold.wasm.service.classgen.DomainObjectClassLoader;
import ai.timefold.wasm.service.classgen.WasmListAccessor;
import ai.timefold.wasm.service.dto.PlanningProblem;
import ai.timefold.wasm.service.dto.SolveResult;

import org.eclipse.microprofile.config.inject.ConfigProperty;

import com.dylibso.chicory.compiler.MachineFactoryCompiler;
import com.dylibso.chicory.runtime.ByteArrayMemory;
import com.dylibso.chicory.runtime.HostFunction;
import com.dylibso.chicory.runtime.ImportFunction;
import com.dylibso.chicory.runtime.ImportValues;
import com.dylibso.chicory.runtime.Instance;
import com.dylibso.chicory.wasi.WasiOptions;
import com.dylibso.chicory.wasi.WasiPreview1;
import com.dylibso.chicory.wasm.Parser;

@Path("/solver")
public class SolverResource {
    public static ThreadLocal<Instance> INSTANCE = new ThreadLocal<>();
    public static ThreadLocal<WasmListAccessor> LIST_ACCESSOR = new ThreadLocal<>();
    public static ThreadLocal<Allocator> ALLOCATOR = new ThreadLocal<>();
    public static ThreadLocal<DomainObjectClassLoader> GENERATED_CLASS_LOADER = new ThreadLocal<>();

    private List<HostFunction> hostFunctionList = Collections.emptyList();

    public void setHostFunctionList(List<HostFunction> hostFunctionList) {
        this.hostFunctionList = hostFunctionList;
    }

    @ConfigProperty(name = "generatedClassPath", defaultValue = "")
    Optional<String> generatedClassPath;

    @POST
    public SolveResult<?> solve(PlanningProblem planningProblem) {
        var solverConfig = new SolverConfig();

        var domainObjectClassGenerator = new DomainObjectClassGenerator();
        var wasmInstance = createWasmInstance(planningProblem.getWasm());

        try {
            var classLoader = new DomainObjectClassLoader();
            GENERATED_CLASS_LOADER.set(classLoader);
            INSTANCE.set(wasmInstance);
            LIST_ACCESSOR.set(new WasmListAccessor(wasmInstance, planningProblem.getListAccessor()));
            ALLOCATOR.set(new Allocator(wasmInstance, planningProblem.getAllocator(), planningProblem.getDeallocator()));

            domainObjectClassGenerator.prepareClassesForPlanningProblem(planningProblem);

            var solutionClass = classLoader.getClassForDomainClassName(planningProblem.getSolutionClass());
            var entityClassList = new ArrayList<Class<?>>(planningProblem.getEntityClassList().size());
            for (var entityClass : planningProblem.getEntityClassList()) {
                entityClassList.add(classLoader.getClassForDomainClassName(entityClass));
            }

            solverConfig.setSolutionClass(solutionClass);
            solverConfig.setEntityClassList(entityClassList);
            solverConfig.setEnvironmentMode(planningProblem.getEnvironmentMode());

            var constraintProviderClass = new ConstraintProviderClassGenerator()
                    .defineConstraintProviderClass(planningProblem);

            generatedClassPath.ifPresent(s -> classLoader.dumpGeneratedClasses(Paths.get(s)));

            solverConfig.withConstraintProviderClass(constraintProviderClass);

            solverConfig.withTerminationConfig(planningProblem.terminationConfig());

            var solverFactory = SolverFactory.create(solverConfig);
            var solver = solverFactory.buildSolver();
            var solverInput = convertPlanningProblem(wasmInstance, classLoader, planningProblem);

            // Copy the solution into a map; we don't know enough from the WASM
            // to create an accurate planning clone that cannot be corrupted by
            // constraints/setters
            MutableReference<SolveResult<?>> bestSolutionRef = new MutableReference<>(
                    new SolveResult<>(planningProblem.getProblem(), null));
            solver.addEventListener(event -> {
                bestSolutionRef.setValue(new SolveResult<>(event.getNewBestSolution().toString(), event.getNewBestScore()));
            });

            solver.solve(solverInput);
            return bestSolutionRef.getValue();
        } finally {
            GENERATED_CLASS_LOADER.remove();
            LIST_ACCESSOR.remove();
        }
    }

    private Instance createWasmInstance(byte[] wasm) {
        var instanceBuilder = Instance.builder(Parser.parse(wasm))
                .withMemoryFactory(ByteArrayMemory::new)
                .withMachineFactory(MachineFactoryCompiler::compile);

        // let's just use the default options for now
        var options = WasiOptions.builder()
                .inheritSystem()
                .build();
        // create our instance of wasip1
        var wasi = WasiPreview1.builder().withOptions(options).build();

        var importFunctions = new ImportFunction[hostFunctionList.size()];
        for (int i = 0; i < importFunctions.length; i++) {
            importFunctions[i] = hostFunctionList.get(i);
        }
        instanceBuilder.withImportValues(ImportValues.builder()
                .addFunction(importFunctions)
                .addFunction(wasi.toHostFunctions())
                .build());


        var out = instanceBuilder.build();
        out.initialize(true);
        return out;
    }

    private Object convertPlanningProblem(Instance wasmInstance, DomainObjectClassLoader classLoader, PlanningProblem planningProblem) {
        var solutionClass = classLoader.getClassForDomainClassName(planningProblem.getSolutionClass());
        var allocator = ALLOCATOR.get();
        try {
            return solutionClass.getConstructor(Allocator.class, Instance.class, String.class).newInstance(allocator, wasmInstance, planningProblem.getProblem());
        } catch (InstantiationException | IllegalAccessException | InvocationTargetException | NoSuchMethodException e) {
            throw new RuntimeException(e);
        }
    }
}
