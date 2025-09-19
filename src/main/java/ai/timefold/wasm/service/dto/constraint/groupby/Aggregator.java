package ai.timefold.wasm.service.dto.constraint.groupby;

import ai.timefold.wasm.service.classgen.DataStreamInfo;

import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.fasterxml.jackson.databind.annotation.JsonTypeIdResolver;

@JsonTypeInfo(use=JsonTypeInfo.Id.CUSTOM, property="name", visible=true)
@JsonTypeIdResolver(AggregatorTypeIdResolver.class)
public sealed interface Aggregator
        permits AverageAggregator, ConnectedRangeAggregator, ConsecutiveAggregator, CountAggregator, LoadBalanceAggregator,
        MaxAggregator, MinAggregator, SumAggregator {
    String name();
    void loadAggregatorInstance(DataStreamInfo dataStreamInfo);
}
