package com.antshorttv.operations;

import static org.assertj.core.api.Assertions.assertThat;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Instant;
import java.util.*;
import org.junit.jupiter.api.Test;

class OperationsPipelineHealthTest {
    @Test void dropDeltaIsStableOnRetryAndRestartButDoesNotFireForever(){
        var db=new OperationsDatabase(OperationsTestDatabase.create());var json=new ObjectMapper();
        var service=new OperationsPipelineHealth(db,json);Instant end=Instant.parse("2026-11-05T01:00:00Z");
        assertThat(service.sample(List.of(resource("a",4,end)),1,end,true).value()).isEqualTo(1d);
        service=new OperationsPipelineHealth(db,json);
        assertThat(service.sample(List.of(resource("a",4,end)),1,end,true).value()).isEqualTo(1d);
        assertThat(service.sample(List.of(resource("a",4,end.plusSeconds(60))),1,end.plusSeconds(60),true).value()).isZero();
        assertThat(service.sample(List.of(resource("a",0,end.plusSeconds(120))),1,end.plusSeconds(120),true).value()).isZero();
        assertThat(service.sample(List.of(resource("a",1,end.plusSeconds(180))),1,end.plusSeconds(180),true).value()).isEqualTo(1d);
    }
    @Test void observesOtherInstancesAndMissingEvidenceIsUnknown(){
        var service=new OperationsPipelineHealth(new OperationsDatabase(OperationsTestDatabase.create()),new ObjectMapper());Instant end=Instant.now();
        assertThat(service.sample(List.of(resource("a",0,end),resource("b",2,end)),1,end,true).value()).isEqualTo(1d);
        assertThat(service.sample(List.of(Map.of("instanceId","c")),1,end,true).complete()).isFalse();
        assertThat(service.sample(List.of(),1,end,true).value()).isNull();
    }
    private Map<String,Object> resource(String instance,long dropped,Instant end){var value=new HashMap<String,Object>();value.put("instanceId",instance);value.put("monitoringDropped",dropped);value.put("monitoringDimensionOverflow",0);value.put("monitoringQueueDepth",0);value.put("monitoringLastFlushAt",end.toString());value.put("at",end.toString());return value;}
}
