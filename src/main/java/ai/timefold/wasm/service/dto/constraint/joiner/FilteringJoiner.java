package ai.timefold.wasm.service.dto.constraint.joiner;

import static ai.timefold.wasm.service.classgen.DomainObjectClassGenerator.getDescriptor;

import java.lang.constant.MethodTypeDesc;

import ai.timefold.solver.core.api.score.stream.Joiners;
import ai.timefold.wasm.service.classgen.DataStreamInfo;
import ai.timefold.wasm.service.classgen.FunctionType;
import ai.timefold.wasm.service.dto.WasmFunction;

import com.fasterxml.jackson.annotation.JsonProperty;

public record FilteringJoiner(@JsonProperty("filter") WasmFunction filter) implements DataJoiner {
    public FilteringJoiner() {
        this(null);
    }

    @Override
    public String relation() {
        return "filtering";
    }

    @Override
    public void loadJoinerInstance(DataStreamInfo dataStreamInfo) {
        var codeBuilder = dataStreamInfo.codeBuilder();
        var filterDesc = dataStreamInfo.loadFunctionWithExtras(FunctionType.PREDICATE, filter, 1);
        codeBuilder.invokestatic(getDescriptor(Joiners.class), "filtering",
                MethodTypeDesc.of(getDescriptor(dataStreamInfo.dataStream().getJoinerClass()),
                        filterDesc));
    }
}
