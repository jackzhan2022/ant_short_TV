package com.antshorttv.operations;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Map;

/** Persisted counter baselines make drop evidence stable across evaluator restarts and retries. */
final class OperationsPipelineHealth {
    private final OperationsDatabase database;
    private final ObjectMapper json;
    OperationsPipelineHealth(OperationsDatabase database,ObjectMapper json){this.database=database;this.json=json;}

    OperationsMetricSource.Sample sample(List<Map<String,Object>> resources,int minutes,Instant end,boolean complete){
        if(resources.isEmpty())return new OperationsMetricSource.Sample(null,0,false);
        double faults=0;boolean known=complete;
        for(var resource:resources){
            if(!resource.containsKey("monitoringDropped")||!resource.containsKey("monitoringDimensionOverflow")){known=false;continue;}
            Double value=database.transactions().execute(status->observe(resource,minutes,end));
            if(value==null)known=false;else faults+=value;
        }
        return new OperationsMetricSource.Sample(known?faults:null,resources.size(),known);
    }
    private Double observe(Map<String,Object> resource,int minutes,Instant end){
        String key="pipeline.baseline."+minutes+"."+java.util.HexFormat.of().formatHex(hash(String.valueOf(resource.get("instanceId"))));
        database.jdbc().update("insert into ops_runtime_setting(setting_key,value_json,version,updated_at) values(?,'{}',1,?) on duplicate key update setting_key=setting_key",key,Timestamp.from(end));
        var rows=database.jdbc().queryForList("select value_json from ops_runtime_setting where setting_key=? for update",key);
        Map<String,Object> previous=decode(rows.get(0).get("value_json"));
        if(previous.containsKey("end")){
            Instant last=Instant.parse(previous.get("end").toString());
            if(last.equals(end))return ((Number)previous.get("faults")).doubleValue();
            if(last.isAfter(end))return null;
        }
        long dropped=number(resource.get("monitoringDropped")),overflow=number(resource.get("monitoringDimensionOverflow"));
        // A lower value is a process restart, not negative evidence; first observed losses still count.
        long dropDelta=delta(dropped,previous.get("dropped")),overflowDelta=delta(overflow,previous.get("overflow"));
        Instant flush=at(resource.get("monitoringLastFlushAt")),sample=at(resource.get("at"));
        double fault=dropDelta>0||overflowDelta>0||number(resource.get("monitoringQueueDepth"))>=5000
            ||resource.get("monitoringLastFailure")!=null||flush==null||flush.isBefore(end.minusSeconds(120))
            ||sample==null||sample.isBefore(end.minusSeconds(120))?1d:0d;
        try{
            String state=json.writeValueAsString(Map.of("end",end.toString(),"dropped",dropped,"overflow",overflow,"faults",fault));
            database.jdbc().update("update ops_runtime_setting set value_json=?,version=version+1,updated_at=? where setting_key=?",state,Timestamp.from(end),key);
        }catch(com.fasterxml.jackson.core.JsonProcessingException e){throw new IllegalStateException(e);}
        return fault;
    }
    private Map<String,Object> decode(Object value){try{return json.readValue(value.toString(),new TypeReference<>(){});}catch(Exception e){throw new IllegalStateException(e);}}
    private static long delta(long current,Object prior){long previous=number(prior);return current>=previous?current-previous:current;}
    private static long number(Object value){return value instanceof Number n?n.longValue():0;}
    private static Instant at(Object value){return value==null?null:Instant.parse(value.toString());}
    private static byte[] hash(String value){try{return java.security.MessageDigest.getInstance("SHA-256").digest(value.getBytes(java.nio.charset.StandardCharsets.UTF_8));}catch(java.security.NoSuchAlgorithmException e){throw new IllegalStateException(e);}}
}
