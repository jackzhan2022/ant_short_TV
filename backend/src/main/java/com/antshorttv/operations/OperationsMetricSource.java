package com.antshorttv.operations;

import java.time.Instant;

public interface OperationsMetricSource {
    Sample sample(String metric,String dimensionsJson,int windowMinutes,Instant end);
    record Sample(Double value,long samples,boolean complete) {}
}
