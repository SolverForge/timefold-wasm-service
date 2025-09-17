package ai.timefold.wasm.service.dto;

import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Map;

import ai.timefold.solver.core.config.solver.EnvironmentMode;
import ai.timefold.solver.core.config.solver.termination.TerminationConfig;

import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;

@NullMarked
public class PlanningProblem {
    @JsonProperty("domain")
    Map<String, DomainObject> domainObjectMap;

    @JsonIgnore
    String solutionClass;
    @JsonIgnore
    List<String> entityClassList;

    @JsonProperty("constraints")
    List<WasmConstraint> constraintList;

    EnvironmentMode environmentMode;

    byte[] wasm;

    String allocator;

    String deallocator;

    String solutionDeallocator;

    DomainListAccessor listAccessor;

    String problem;

    @JsonProperty("termination")
    PlanningTermination terminationConfig;

    @JsonCreator
    public PlanningProblem(@JsonProperty("domain")  Map<String, DomainObject> domainObjectMap,
            @JsonProperty("constraints") Map<String, WasmConstraint> constraintList,
            @Nullable@JsonProperty("environmentMode") EnvironmentMode environmentMode,
            @JsonProperty("wasm") String wasm,
            @JsonProperty("allocator") String allocator,
            @JsonProperty("deallocator") String deallocator,
            @Nullable @JsonProperty("solutionDeallocator") String solutionDeallocator,
            @JsonProperty("listAccessor")  DomainListAccessor listAccessor,
            @JsonProperty("problem") String problem,
            @Nullable @JsonProperty("termination") PlanningTermination terminationConfig) {
        this.domainObjectMap = domainObjectMap;
        this.constraintList = constraintList.entrySet()
                .stream()
                .map(entry -> {
                    entry.getValue().name = entry.getKey();
                    return entry.getValue();
                }).toList();
        this.environmentMode = (environmentMode != null)? environmentMode : EnvironmentMode.PHASE_ASSERT;
        this.problem = problem;
        this.wasm = Base64.getDecoder().decode(wasm);
        this.allocator = allocator;
        this.deallocator = deallocator;
        this.solutionDeallocator = (solutionDeallocator != null)? solutionDeallocator : deallocator;
        this.listAccessor = listAccessor;
        this.terminationConfig = (terminationConfig != null)? terminationConfig : new PlanningTermination().withDiminishedReturns(new PlanningDiminishedReturns());

        entityClassList = new ArrayList<>();
        for (var entry : domainObjectMap.entrySet()) {
            var className = entry.getKey();
            var domainObject = entry.getValue();
            domainObject.setName(className);

            for (var field : domainObject.fieldDescriptorMap.values()) {
                if (field.annotations != null) {
                    for (var annotation : field.annotations) {
                        if (annotation.definesPlanningEntity() && !entityClassList.contains(className)) {
                            entityClassList.add(className);
                        }
                        if (annotation.definesPlanningSolution()) {
                            if (solutionClass == null) {
                                solutionClass = className;
                            } else if (!solutionClass.equals(className)) {
                                throw new IllegalStateException("Multiple solution classes found (%s) and (%s).".formatted(solutionClass, className));
                            }
                        }
                    }
                }
            }
        }

        if (solutionClass == null) {
            throw new IllegalStateException("No solution class found.");
        }
        if (entityClassList.isEmpty()) {
            throw new IllegalStateException("No entity classes found.");
        }
    }

    public Map<String, DomainObject> getDomainObjectMap() {
        return domainObjectMap;
    }

    public String getSolutionClass() {
        return solutionClass;
    }

    public List<String> getEntityClassList() {
        return entityClassList;
    }

    public List<WasmConstraint> getConstraintList() {
        return constraintList;
    }

    public void setConstraints(Map<String, WasmConstraint> constraintMap) {
        this.constraintList = constraintMap.entrySet()
                .stream()
                .map(entry -> {
                    entry.getValue().name = entry.getKey();
                    return entry.getValue();
                }).toList();
    }

    public String getProblem() {
        return problem;
    }

    public void setProblem(String problem) {
        this.problem = problem;
    }

    public byte[] getWasm() {
        return wasm;
    }

    public String getAllocator() {
        return allocator;
    }

    public String getDeallocator() {
        return deallocator;
    }

    public String getSolutionDeallocator() {
        return solutionDeallocator;
    }

    public DomainListAccessor getListAccessor() {
        return listAccessor;
    }

    public TerminationConfig terminationConfig() {
        return terminationConfig.asTerminationConfig();
    }

    public EnvironmentMode getEnvironmentMode() {
        return environmentMode;
    }
}
