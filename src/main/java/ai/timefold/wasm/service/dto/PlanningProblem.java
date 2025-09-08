package ai.timefold.wasm.service.dto;

import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Map;

import org.jspecify.annotations.NullMarked;

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

    @JsonProperty("penalize")
    List<WasmConstraint> penaltyConstraintList;

    @JsonProperty("reward")
    List<WasmConstraint> rewardConstraintList;

    byte[] wasm;

    Map<String, Object> problem;

    @JsonCreator
    public PlanningProblem(@JsonProperty("domain")  Map<String, DomainObject> domainObjectMap,
            @JsonProperty("penalty") List<WasmConstraint> penaltyConstraintList,
            @JsonProperty("reward") List<WasmConstraint> rewardConstraintList,
            @JsonProperty("wasm") String wasm,
            @JsonProperty("problem") Map<String, Object> problem) {
        this.domainObjectMap = domainObjectMap;
        this.penaltyConstraintList = penaltyConstraintList;
        this.rewardConstraintList = rewardConstraintList;
        this.problem = problem;
        this.wasm = Base64.getDecoder().decode(wasm);

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

    public List<WasmConstraint> getPenaltyConstraintList() {
        return penaltyConstraintList;
    }

    public List<WasmConstraint> getRewardConstraintList() {
        return rewardConstraintList;
    }

    public Map<String, Object> getProblem() {
        return problem;
    }

    public byte[] getWasm() {
        return wasm;
    }
}
